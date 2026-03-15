package dev.ignis.aggressiveaircraft.entities;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
    private static final double MAX_LIFETIME = 100; // 5秒 = 100 ticks
    private static final double DESCENT_START_TIME = 40; // 2秒后（60 ticks剩余）开始俯冲转为水平
    private static final double FIRE_START_TIME = 40; // 后3秒 = 60 ticks，从第40 tick开始发射
    private static final int BULLETS_PER_SECOND = 20;
    private static final double FIRE_INTERVAL = 20.0 / BULLETS_PER_SECOND; // 每多少tick发射一发
    private static final double HORIZONTAL_SPEED = 2.0; // 水平飞行速度

    private int lifetime = 0;
    private double fireTimer = 0;
    private boolean hasFired = false;
    private Vec3 inheritedVelocity = Vec3.ZERO;
    private final Random random = new Random();

    public ClusterDispenserEntity(EntityType<? extends ClusterDispenserEntity> entityType, Level level) {
        super(entityType, level);
    }

    public void setInheritedVelocity(Vec3 velocity) {
        this.inheritedVelocity = velocity;
    }

    @Override
    public void tick() {
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
        }

        if (!this.level().isClientSide) {
            lifetime++;

            // 超过5秒爆炸
            if (lifetime > MAX_LIFETIME) {
                explode();
                this.discard();
                return;
            }

            // 飞行轨迹控制
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

        super.tick();
    }

    private void updateFlightPath() {
        Vec3 currentVel = this.getDeltaMovement();

        if (lifetime < DESCENT_START_TIME) {
            // 前2秒：继承飞机速度，逐渐减小垂直速度
            double progress = lifetime / DESCENT_START_TIME;
            double targetY = inheritedVelocity.y * (1.0 - progress * 0.5); // 垂直速度衰减50%
            this.setDeltaMovement(
                inheritedVelocity.x,
                targetY,
                inheritedVelocity.z
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
        var bullet = ModEntities.EXPLOSIVE_BULLET.get().create(this.level());
        if (bullet == null) return;

        // 设置子弹属性
        bullet.setDamage(30.0f);
        bullet.setExplosionPower(2.0);
        bullet.setDestroyBlocks(false);
        bullet.setOwner(this.getOwner());
        bullet.setPos(this.getX(), this.getY(), this.getZ());

        // 向下散射
        double scatterX = (random.nextDouble() - 0.5) * 0.3;
        double scatterZ = (random.nextDouble() - 0.5) * 0.3;
        double downwardSpeed = 2.0 + random.nextDouble() * 0.5;

        bullet.shoot(scatterX, -1.0, scatterZ, (float) downwardSpeed, 0.0f);
        this.level().addFreshEntity(bullet);
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
}
