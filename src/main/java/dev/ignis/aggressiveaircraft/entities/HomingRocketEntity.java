package dev.ignis.aggressiveaircraft.entities;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class HomingRocketEntity extends AbstractHurtingProjectile {
    private static final double MAX_LIFETIME = 200; // 10秒 = 200 ticks
    private static final double TURN_SPEED = 0.12;
    private static final double SPEED = 2; // 40格/秒 = 2格/tick
    private static final int MID_FLIGHT_SCAN_INTERVAL = 10; // 每10tick扫描一次
    private static final double MID_FLIGHT_SCAN_DISTANCE = 25.0; // 前方25格
    private static final double MID_FLIGHT_SCAN_RADIUS = 25.0; // 搜索半径25格

    private float damage = 200.0f;
    private float explosionPower = 2.0f;
    private int lifetime = 0;
    private int midFlightScanTimer = 0;
    private LivingEntity target = null;

    public HomingRocketEntity(EntityType<? extends HomingRocketEntity> entityType, Level level) {
        super(entityType, level);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public void setExplosionPower(float explosionPower) {
        this.explosionPower = explosionPower;
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    @Override
    public void tick() {
        if (this.level().isClientSide) {
            // 生成更多更密的烟雾，带扩散效果
            Vec3 motion = this.getDeltaMovement();
            for (int i = 0; i < 8; i++) {
                // 在火箭后方扩散生成
                double trailOffset = i * 0.15; // 沿运动方向向后偏移
                double spreadX = (this.random.nextDouble() - 0.5) * 0.8; // 更大扩散范围
                double spreadY = (this.random.nextDouble() - 0.5) * 0.8;
                double spreadZ = (this.random.nextDouble() - 0.5) * 0.8;

                double spawnX = this.getX() - motion.x * trailOffset + spreadX;
                double spawnY = this.getY() - motion.y * trailOffset + spreadY;
                double spawnZ = this.getZ() - motion.z * trailOffset + spreadZ;

                // 随机扩散速度
                double velX = (this.random.nextDouble() - 0.5) * 0.02;
                double velY = 0.03 + this.random.nextDouble() * 0.02; // 向上飘
                double velZ = (this.random.nextDouble() - 0.5) * 0.02;

                this.level().addParticle(
                    ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    spawnX, spawnY, spawnZ,
                    velX, velY, velZ
                );
            }
            // 客户端也保持恒定速度，避免与服务端不同步
            Vec3 currentVel = this.getDeltaMovement();
            if (currentVel.lengthSqr() > 0.001) {
                Vec3 normalized = currentVel.normalize();
                this.setDeltaMovement(normalized.x * SPEED, normalized.y * SPEED, normalized.z * SPEED);
            }
        }

        if (!this.level().isClientSide) {
            lifetime++;
            if (lifetime > MAX_LIFETIME) {
                explode();
                this.discard();
                return;
            }

            if (target != null && target.isAlive()) {
                // 有目标时制导飞行，guideToTarget 内部已设置 SPEED 速度
                guideToTarget(target.position());
            } else {
                // 无目标时保持当前方向恒定速度，覆盖原版阻力衰减
                Vec3 currentVel = this.getDeltaMovement();
                if (currentVel.lengthSqr() > 0.001) {
                    Vec3 normalized = currentVel.normalize();
                    this.setDeltaMovement(normalized.x * SPEED, normalized.y * SPEED, normalized.z * SPEED);
                }

                // 无目标时，每10tick尝试二次锁定
                midFlightScanTimer++;
                if (midFlightScanTimer >= MID_FLIGHT_SCAN_INTERVAL) {
                    midFlightScanTimer = 0;
                    tryMidFlightLock();
                }
            }
        }

        super.tick();
    }

    private void tryMidFlightLock() {
        Vec3 currentPos = this.position();
        Vec3 currentVel = this.getDeltaMovement();
        if (currentVel.lengthSqr() < 0.001) return;

        // 计算前方25格处的点
        Vec3 direction = currentVel.normalize();
        Vec3 scanCenter = currentPos.add(direction.x * MID_FLIGHT_SCAN_DISTANCE,
                                         direction.y * MID_FLIGHT_SCAN_DISTANCE,
                                         direction.z * MID_FLIGHT_SCAN_DISTANCE);

        // 搜索25格半径内的敌对实体
        AABB searchBox = new AABB(
            scanCenter.x - MID_FLIGHT_SCAN_RADIUS, scanCenter.y - MID_FLIGHT_SCAN_RADIUS, scanCenter.z - MID_FLIGHT_SCAN_RADIUS,
            scanCenter.x + MID_FLIGHT_SCAN_RADIUS, scanCenter.y + MID_FLIGHT_SCAN_RADIUS, scanCenter.z + MID_FLIGHT_SCAN_RADIUS
        );

        List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, searchBox, this::isValidMidFlightTarget);

        if (!entities.isEmpty()) {
            // 选择血量最多的目标，过滤25生命值以下
            target = entities.stream()
                .filter(e -> e.getHealth() >= 25.0f)
                .max(Comparator.comparingDouble(LivingEntity::getHealth))
                .orElse(null);
        }
    }

    private boolean isValidMidFlightTarget(LivingEntity entity) {
        if (!entity.isAlive() || !entity.isPickable()) {
            return false;
        }

        // 敌对生物判断
        if (entity.getMobType() == net.minecraft.world.entity.MobType.UNDEAD
            || entity.getMobType() == net.minecraft.world.entity.MobType.ARTHROPOD
            || entity.getMobType() == net.minecraft.world.entity.MobType.ILLAGER) {
            // 是敌对生物
        } else if (entity.getType().getCategory().isFriendly()) {
            return false;
        }

        // 不能是发射者或其乘客
        Entity owner = this.getOwner();
        if (owner != null && entity.isPassengerOfSameVehicle(owner)) {
            return false;
        }

        return true;
    }

    private void guideToTarget(Vec3 targetPosition) {
        Vec3 currentPos = this.position();
        Vec3 toTarget = targetPosition.subtract(currentPos).normalize();

        Vec3 currentVelocity = this.getDeltaMovement();
        Vec3 currentDir = currentVelocity.lengthSqr() > 0.001
            ? currentVelocity.normalize()
            : toTarget;

        Vec3 newDir = new Vec3(
            currentDir.x + (toTarget.x - currentDir.x) * TURN_SPEED,
            currentDir.y + (toTarget.y - currentDir.y) * TURN_SPEED,
            currentDir.z + (toTarget.z - currentDir.z) * TURN_SPEED
        ).normalize();

        this.setDeltaMovement(newDir.x * SPEED, newDir.y * SPEED, newDir.z * SPEED);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (canHitEntity(target)) {
            // 无视无敌帧直接造成伤害
            if (target instanceof LivingEntity living) {
                living.hurtTime = 0;  // 重置无敌帧计时器
                living.invulnerableTime = 0;  // 重置无敌时间
            }
            target.hurt(level().damageSources().thrown(this, this.getOwner()), damage);
        }
        explode();
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        explode();
        this.discard();
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
            Level.ExplosionInteraction interaction = Level.ExplosionInteraction.NONE;
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
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false; // 禁止被爆炸等外力推动
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
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Lifetime", lifetime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Lifetime")) {
            lifetime = tag.getInt("Lifetime");
        }
    }

    public float getRoll(float tickDelta) {
        return 0.0f; // 火箭无滚动
    }
}
