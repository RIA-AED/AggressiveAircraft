package dev.ignis.aggressiveaircraft.entities;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class RocketPodRocketEntity extends AbstractHurtingProjectile {
    private static final int BOOST_DURATION = 5; // 动力段持续5tick
    private static final double MAX_BOOST_SPEED = 3.0; // 动力段最终速度（格/tick）
    private static final double MAX_LIFETIME = 40; // 2秒 = 40 ticks

    private float damage = 50.0f;
    private float explosionPower = 4.0f;
    private boolean destroyBlocks = false;
    private int lifetime = 0;
    private Vec3 inheritedVelocity = Vec3.ZERO; // 继承自飞机的速度
    private boolean clientInitialized = false; // 客户端是否已初始化朝向

    public RocketPodRocketEntity(EntityType<? extends RocketPodRocketEntity> entityType, Level level) {
        super(entityType, level);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public void setExplosionPower(double explosionPower) {
        this.explosionPower = (float) explosionPower;
    }

    public void setDestroyBlocks(boolean destroyBlocks) {
        this.destroyBlocks = destroyBlocks;
    }

    /**
     * 设置继承自载具的速度，发射时调用
     */
    public void setInheritedVelocity(Vec3 velocity) {
        this.inheritedVelocity = velocity;
    }

    @Override
    public void tick() {
        // 客户端首次tick前：从deltaMovement直接计算并硬设正确旋转
        // 使用与rotateTowardsMovement相同的公式，确保后续lerp不会产生渐变
        if (this.level().isClientSide && !clientInitialized) {
            clientInitialized = true;
            Vec3 vel = this.getDeltaMovement();
            if (vel.lengthSqr() > 0.001) {
                double hDist = vel.horizontalDistance();
                // 与ProjectileUtil.rotateTowardsMovement完全一致的公式
                float yaw = (float)(Mth.atan2(vel.z, vel.x) * (180.0 / Math.PI)) + 90.0F;
                float pitch = (float)(Mth.atan2(hDist, vel.y) * (180.0 / Math.PI)) - 90.0F;
                this.setYRot(yaw);
                this.setXRot(pitch);
                this.yRotO = yaw;
                this.xRotO = pitch;
            }
        }

        super.tick();

        if (!this.level().isClientSide) {
            lifetime++;
            if (lifetime > MAX_LIFETIME) {
                explode();
                this.discard();
                return;
            }
        }

        Vec3 currentVel = this.getDeltaMovement();

        if (lifetime <= BOOST_DURATION) {
            // 动力段：逐渐加速
            // 初始速度 = shoot设定的初速 + 飞机继承速度
            // 随时间线性插值到 MAX_BOOST_SPEED
            double progress = (double) lifetime / BOOST_DURATION; // 0 -> 1
            Vec3 dir = currentVel.lengthSqr() > 0.001 ? currentVel.normalize() : Vec3.ZERO;

            // 当前速度 = 飞行方向上的分量逐步增加到最大推力速度
            double currentSpeed = currentVel.length();
            double targetSpeed = MAX_BOOST_SPEED;
            double newSpeed = currentSpeed + (targetSpeed - currentSpeed) * progress * 0.3;

            // 继承速度随时间衰减
            double inheritFactor = 1.0 - progress;
            Vec3 boostVel = dir.scale(newSpeed).add(inheritedVelocity.scale(inheritFactor * 0.05));

            this.setDeltaMovement(boostVel);

            // 客户端：动力段冒烟
            if (this.level().isClientSide) {
                spawnBoostParticles();
            }
        } else {
            // 出了动力段：正常飞行，受重力和阻力影响（AbstractHurtingProjectile默认行为）
            // 不做额外处理，让原版物理自然减速
        }

        // 速度过低时爆炸
        if (!this.level().isClientSide && lifetime > BOOST_DURATION && currentVel.lengthSqr() < 0.1) {
            explode();
            this.discard();
        }
    }

    private void spawnBoostParticles() {
        Vec3 motion = this.getDeltaMovement();
        for (int i = 0; i < 3; i++) {
            double trailOffset = i * 0.1;
            double spreadX = (this.random.nextDouble() - 0.5) * 0.4;
            double spreadY = (this.random.nextDouble() - 0.5) * 0.4;
            double spreadZ = (this.random.nextDouble() - 0.5) * 0.4;

            double spawnX = this.getX() - motion.x * trailOffset + spreadX;
            double spawnY = this.getY() - motion.y * trailOffset + spreadY;
            double spawnZ = this.getZ() - motion.z * trailOffset + spreadZ;

            double velX = (this.random.nextDouble() - 0.5) * 0.01;
            double velY = 0.02 + this.random.nextDouble() * 0.01;
            double velZ = (this.random.nextDouble() - 0.5) * 0.01;

            this.level().addParticle(
                ParticleTypes.SMOKE,
                spawnX, spawnY, spawnZ,
                velX, velY, velZ
            );
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (canHitEntity(result.getEntity())) {
            result.getEntity().hurt(level().damageSources().thrown(this, this.getOwner()), damage);
        }
        explode();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            explode();
            this.discard();
        }
    }

    private void explode() {
        if (!this.level().isClientSide) {
            Level.ExplosionInteraction interaction = destroyBlocks
                ? Level.ExplosionInteraction.TNT
                : Level.ExplosionInteraction.NONE;

            Entity owner = this.getOwner();
            this.level().explode(
                owner != null ? owner : this,
                this.getX(),
                this.getY(),
                this.getZ(),
                explosionPower,
                interaction
            );
        }
    }

    @Override
    protected float getInertia() {
        return 0.99f; // 默认0.95，更接近1.0意味着更小的阻力
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (target.isSpectator() || !target.isAlive() || !target.isPickable()) {
            return false;
        }
        Entity entity = this.getOwner();
        return entity == null || !entity.isPassengerOfSameVehicle(target);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = this.getBoundingBox().getSize() * 10.0;
        if (Double.isNaN(d)) {
            d = 10.0;
        }
        return distance < (d *= 64.0) * d;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Lifetime", lifetime);
        tag.putFloat("Damage", damage);
        tag.putFloat("ExplosionPower", explosionPower);
        tag.putBoolean("DestroyBlocks", destroyBlocks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Lifetime")) lifetime = tag.getInt("Lifetime");
        if (tag.contains("Damage")) damage = tag.getFloat("Damage");
        if (tag.contains("ExplosionPower")) explosionPower = tag.getFloat("ExplosionPower");
        if (tag.contains("DestroyBlocks")) destroyBlocks = tag.getBoolean("DestroyBlocks");
    }

    /**
     * 获取当前是否在动力段（用于渲染器判断是否冒烟）
     */
    public boolean isBoosting() {
        return lifetime <= BOOST_DURATION;
    }

    public float getRoll(float tickDelta) {
        return 0.0f;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        Entity owner = this.getOwner();
        int i = owner == null ? 0 : owner.getId();
        // 传输 deltaMovement 而非 xPower/yPower/zPower，确保客户端收到正确速度
        return new ClientboundAddEntityPacket(
            this.getId(), this.getUUID(),
            this.getX(), this.getY(), this.getZ(),
            this.getXRot(), this.getYRot(),
            this.getType(), i,
            this.getDeltaMovement(), 0.0
        );
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        // 从 packet 恢复 deltaMovement（super 只恢复了 xPower/yPower/zPower）
        this.setDeltaMovement(packet.getXa(), packet.getYa(), packet.getZa());
        // 同步 old rotation，避免第一帧插值导致方向闪烁
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }
}
