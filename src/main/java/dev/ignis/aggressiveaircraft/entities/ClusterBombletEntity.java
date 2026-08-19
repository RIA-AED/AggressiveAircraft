package dev.ignis.aggressiveaircraft.entities;

import dev.ignis.aggressiveaircraft.combat.Enemy;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cluster bomblet entity - the submunitions dispersed by the ClusterDispenserEntity.
 * Visually rendered as a billboard sprite, similar to ExplosiveBulletEntity.
 * Damage, explosion power, and block destruction are configurable via ModConfig.
 */
public class ClusterBombletEntity extends AbstractHurtingProjectile {
    private static final double TURN_SPEED = 0.1; // 弱制导：缓慢转向系数（垂直方向再打折）
    private static final double MIN_GUIDED_SPEED = 2.0; // 制导时保持的最低下落速度（抵抗原版0.95/tick阻尼）

    private float damage = 4.0f; // 每次命中伤害
    private int hitCount = 4; // 命中次数（x次y伤害）
    private LivingEntity target = null; // 制导目标，由布撒器在发射时按血量份额分配

    public ClusterBombletEntity(EntityType<? extends ClusterBombletEntity> entityType, Level level) {
        super(entityType, level);
    }

    public float getScale() {
        return 0.4f;
    }

    public float getDamage() {
        return damage;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    public void setHitCount(int hitCount) {
        this.hitCount = hitCount;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!this.level().isClientSide && canHitEntity(result.getEntity())) {
            Entity hit = result.getEntity();
            if (hit instanceof LivingEntity living) {
                // 无视无敌帧，连续造成 x 次 y 伤害（弹射物伤害，无爆炸）
                for (int i = 0; i < hitCount; i++) {
                    living.hurtTime = 0;
                    living.invulnerableTime = 0;
                    living.hurt(level().damageSources().thrown(this, this.getOwner()), damage);
                }
            } else {
                hit.hurt(level().damageSources().thrown(this, this.getOwner()), damage * hitCount);
            }
        }
        this.discard();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            this.discard();
        }
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = this.getBoundingBox().getSize() * 10.0;
        if (Double.isNaN(d)) {
            d = 10.0;
        }
        return distance < (d *= 64.0) * d * getScale();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (target.isSpectator() || !target.isAlive() || !target.isPickable()) {
            return false;
        }
        // 仅限攻击敌对目标（Enemy接口/敌对类别），玩家与其余实体直接穿过
        if (!(target instanceof Enemy)
                && !(target instanceof LivingEntity living && Enemy.isHostile(living))) {
            return false;
        }
        // 不撞到任何载具上的乘客（含发射飞机的乘员与任何飞机上的乘客）；飞机本身非Enemy/玩家，天然穿过
        return target.getVehicle() == null;
    }

    @Override
    public void tick() {
        super.tick();

        if (getDeltaMovement().lengthSqr() < 0.1) {
            discard();
        }

        // Client: trail particles
        if (this.level().isClientSide) {
            Vec3 motion = this.getDeltaMovement();

            // Smoke trail behind the bomblet
            double offsetX = (this.random.nextDouble() - 0.5) * 0.15;
            double offsetY = (this.random.nextDouble() - 0.5) * 0.15;
            double offsetZ = (this.random.nextDouble() - 0.5) * 0.15;

            double smokeX = this.getX() - motion.x * 0.2 + offsetX;
            double smokeY = this.getY() - motion.y * 0.2 + offsetY;
            double smokeZ = this.getZ() - motion.z * 0.2 + offsetZ;

            this.level().addParticle(
                    ParticleTypes.SMOKE,
                    smokeX, smokeY, smokeZ,
                    -motion.x * 0.05, -motion.y * 0.05 + 0.01, -motion.z * 0.05
            );
        }

        // 服务端：弱制导
        if (!this.level().isClientSide) {
            guideTick();
        }
    }

    /**
     * 弱制导：以缓慢的转向率向分配的目标漂移，垂直方向转向再打折，
     * 保持下落速度但不低于最低值（原版 projectile 每tick有0.95阻尼，需抵消）。
     * 制导明显弱于火箭：无法加速、追不上移动目标，静态目标会被明显拉向。
     */
    private void guideTick() {
        if (target == null || !target.isAlive() || target.level() != this.level()
                || target.getVehicle() != null) {
            target = null; // 目标已死亡/离开/成为乘客，转为自由下落
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        if (velocity.lengthSqr() < 0.001) {
            return;
        }
        Vec3 currentDir = velocity.normalize();
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(this.position()).normalize();

        Vec3 newDir = new Vec3(
                currentDir.x + (toTarget.x - currentDir.x) * TURN_SPEED,
                currentDir.y + (toTarget.y - currentDir.y) * TURN_SPEED * 0.7,
                currentDir.z + (toTarget.z - currentDir.z) * TURN_SPEED
        ).normalize();

        // 只会下落不会上升：垂直分量钳制为向下
        if (newDir.y > 0) {
            newDir = new Vec3(newDir.x, 0, newDir.z);
            if (newDir.lengthSqr() < 0.001) {
                newDir = new Vec3(0, -1, 0); // 原方向几乎完全朝上时，回退为垂直下落
            } else {
                newDir = newDir.normalize();
            }
        }

        double speed = Math.max(MIN_GUIDED_SPEED, velocity.length());
        this.setDeltaMovement(newDir.x * speed, newDir.y * speed, newDir.z * speed);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }
}
