package dev.ignis.aggressiveaircraft.entities;

import dev.ignis.aggressiveaircraft.AggressiveAircraft;
import dev.ignis.aggressiveaircraft.ModConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Random;

public class ClusterDispenserEntity extends AbstractHurtingProjectile {
    private static final double MAX_LIFETIME = 60; // 3秒 = 60 ticks（1秒飞行 + 2秒布撒）
    private static final double DESCENT_START_TIME = 20; // 1秒后开始转为水平
    private static final double FIRE_START_TIME = 20; // 1秒后开始布撒
    private static final double FIRE_END_TIME = 60; // 3秒后结束布撒（布撒持续2秒）
    private static final double FIRE_INTERVAL = 1.0; // 每tick发射1发
    private static final double HORIZONTAL_SPEED = 2.0; // 水平飞行速度

    private int lifetime = 0;
    private double fireTimer = 0;
    private boolean hasFired = false;
    private Vec3 inheritedVelocity = Vec3.ZERO;
    
    // 调试用：记录是否已设置继承速度
    private boolean velocitySet = false;
    private final Random random = new Random();

    public ClusterDispenserEntity(EntityType<? extends ClusterDispenserEntity> entityType, Level level) {
        super(entityType, level);
    }

    public void setInheritedVelocity(Vec3 velocity) {
        this.inheritedVelocity = velocity;
        this.velocitySet = true;
        System.out.println("[ClusterDispenser] Inherited velocity set: " + velocity);
    }

    @Override
    public void tick() {
        // 先调用父类tick处理运动（客户端和服务端都需要）
        super.tick();

        if (this.level().isClientSide) {
            // 烟雾尾迹
            for (int i = 0; i < 3; i++) {
                double offsetX = (this.random.nextDouble() - 0.5) * 0.5;
                double offsetY = (this.random.nextDouble() - 0.5) * 0.5;
                double offsetZ = (this.random.nextDouble() - 0.5) * 0.5;
                this.level().addParticle(
                    ParticleTypes.SMOKE,
                    this.getX() + offsetX,
                    this.getY() + offsetY,
                    this.getZ() + offsetZ,
                    0.0, 0.02, 0.0
                );
            }
            
            // 客户端：只在刚创建时同步载具角度（避免初始朝向错误）
            if (this.tickCount < 5) {
                syncRotationFromVehicle();
            }
            
            return;
        }

        // 服务端逻辑
        lifetime++;

        // 超过5秒爆炸
        if (lifetime > MAX_LIFETIME) {
            explode();
            this.discard();
            return;
        }

        // 飞行轨迹控制 - 在父类tick之后设置速度，确保下一tick使用新速度
        updateFlightPath();

        // 发射子弹阶段（后3秒）
        if (lifetime >= FIRE_START_TIME) {
            fireTimer += 1.0;
            while (fireTimer >= FIRE_INTERVAL) {
                fireTimer -= FIRE_INTERVAL;
                fireBullet();
            }
        }
    }

    private void updateFlightPath() {
        Vec3 currentVel = this.getDeltaMovement();
        
        // 调试输出
        if (lifetime == 0 || lifetime == 1) {
            AggressiveAircraft.LOGGER.debug("[ClusterDispenser] tick=" + lifetime + " velocitySet=" + velocitySet
                + " inherited=" + inheritedVelocity + " currentVel=" + currentVel);
        }

        if (lifetime < DESCENT_START_TIME) {
            // 前1秒：保持当前速度（从BulletWeapon.fire()继承的飞机速度）
            // 逐渐减小垂直速度，让布撒器趋于水平
            double progress = lifetime / DESCENT_START_TIME;
            // 使用当前速度作为基础，而不是inheritedVelocity
            // 这样可以确保方向正确
            double targetY = currentVel.y * (1.0 - progress * 0.3); // 垂直速度衰减30%
            this.setDeltaMovement(
                currentVel.x,
                targetY,
                currentVel.z
            );
        } else {
            // 后3秒：转为水平飞行
            double horizontalProgress = Math.min(1.0, (lifetime - DESCENT_START_TIME) / 20.0);

            // 水平方向继承原速度方向，但大小趋近恒定
            Vec3 horizontalDir = new Vec3(inheritedVelocity.x, 0, inheritedVelocity.z).normalize();
            if (horizontalDir.lengthSqr() < 0.001) {
                horizontalDir = new Vec3(0, 0, 1);
            }

            double targetX = horizontalDir.x * HORIZONTAL_SPEED;
            double targetZ = horizontalDir.z * HORIZONTAL_SPEED;
            double targetY = -0.1 * horizontalProgress; // 轻微下降

            // 平滑过渡
            this.setDeltaMovement(
                currentVel.x + (targetX - currentVel.x) * 0.1,
                currentVel.y + (targetY - currentVel.y) * 0.1,
                currentVel.z + (targetZ - currentVel.z) * 0.1
            );
        }
    }

    private void fireBullet() {
        var bomblet = ModEntities.CLUSTER_BOMBLET.get().create(this.level());
        if (bomblet == null) return;

        // Read config values
        bomblet.setDamage(ModConfig.CLUSTER_DISPENSER_BOMBLET_DAMAGE.get());
        bomblet.setExplosionPower(ModConfig.CLUSTER_DISPENSER_BOMBLET_EXPLOSION_POWER.get());
        bomblet.setDestroyBlocks(ModConfig.CLUSTER_DISPENSER_BOMBLET_DESTROY_BLOCKS.get());
        bomblet.setOwner(this.getOwner());
        bomblet.setPos(this.getX(), this.getY(), this.getZ());

        // Scatter downward
        double scatterX = (random.nextDouble() - 0.5) * 0.3;
        double scatterZ = (random.nextDouble() - 0.5) * 0.3;
        double downwardSpeed = 2.0 + random.nextDouble() * 0.5;

        bomblet.shoot(scatterX, -1.0, scatterZ, (float) downwardSpeed, 0.0f);
        this.level().addFreshEntity(bomblet);
        
        // 播放拾起物品的声音，音量2.0（更大声）
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 2.0f, 1.0f);
    }

    private void explode() {
        if (!this.level().isClientSide) {
            this.level().explode(
                this.getOwner() != null ? this.getOwner() : this,
                this.getX(),
                this.getY(),
                this.getZ(),
                2.0f,
                Level.ExplosionInteraction.NONE
            );
        }
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
        return false;
    }

    protected void pushEntities() {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Lifetime", lifetime);
        tag.putDouble("InheritedVelX", inheritedVelocity.x);
        tag.putDouble("InheritedVelY", inheritedVelocity.y);
        tag.putDouble("InheritedVelZ", inheritedVelocity.z);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Lifetime")) {
            lifetime = tag.getInt("Lifetime");
        }
        if (tag.contains("InheritedVelX")) {
            inheritedVelocity = new Vec3(
                tag.getDouble("InheritedVelX"),
                tag.getDouble("InheritedVelY"),
                tag.getDouble("InheritedVelZ")
            );
        }
    }

    public float getRoll(float tickDelta) {
        return 0.0f; // 布撒器无滚动
    }
    
    /**
     * 从载具同步旋转角度（用于初始朝向）
     */
    private void syncRotationFromVehicle() {
        Entity owner = this.getOwner();
        if (owner == null) return;
        
        // 获取玩家乘坐的载具
        Entity vehicle = owner.getVehicle();
        if (vehicle == null) return;
        
        // 同步载具的旋转角度
        this.setYRot(vehicle.getYRot());
        this.setXRot(vehicle.getXRot());
        this.yRotO = vehicle.getYRot();
        this.xRotO = vehicle.getXRot();
    }
    
    /**
     * 根据速度向量更新实体旋转
     */
    private void updateRotation(Vec3 velocity) {
        // 计算水平方向角度（yaw）
        double horizontalDist = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(velocity.x, velocity.z)));
        float pitch = (float) (Math.toDegrees(Math.atan2(velocity.y, horizontalDist)));
        
        // 设置旋转
        this.setYRot(yaw);
        this.setXRot(pitch);
        
        // 同步到旧系统
        this.yRotO = yaw;
        this.xRotO = pitch;
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        // 覆盖默认的插值运动，直接使用服务器速度
        this.setDeltaMovement(x, y, z);
    }
}
