package dev.ignis.aggressiveaircraft.entities;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class HomingRocketEntity extends AbstractHurtingProjectile {
    private static final double MAX_LIFETIME = 200; // 10秒 = 200 ticks
    private static final double TURN_SPEED = 0.12;
    private static final double SPEED = 1.8;

    private float damage = 200.0f;
    private float explosionPower = 2.0f;
    private int lifetime = 0;
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
            for (int i = 0; i < 3; i++) {
                double offsetX = (this.random.nextDouble() - 0.5) * 0.3;
                double offsetY = (this.random.nextDouble() - 0.5) * 0.3;
                double offsetZ = (this.random.nextDouble() - 0.5) * 0.3;
                this.level().addParticle(
                    ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    this.getX() + offsetX,
                    this.getY() + offsetY,
                    this.getZ() + offsetZ,
                    0.0, 0.05, 0.0
                );
            }
        }

        if (!this.level().isClientSide) {
            lifetime++;
            if (lifetime > MAX_LIFETIME) {
                explode();
                this.discard();
                return;
            }

            // 保持恒定速度，覆盖原版的阻力衰减
            Vec3 currentVel = this.getDeltaMovement();
            if (currentVel.lengthSqr() > 0.001) {
                Vec3 normalized = currentVel.normalize();
                this.setDeltaMovement(normalized.x * SPEED, normalized.y * SPEED, normalized.z * SPEED);
            }

            if (target != null && target.isAlive()) {
                guideToTarget(target.position());
            }
        }

        super.tick();
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
        if (canHitEntity(result.getEntity())) {
            result.getEntity().hurt(level().damageSources().thrown(this, this.getOwner()), damage);
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
    protected boolean canHitEntity(Entity target) {
        if (target.isSpectator() || !target.isAlive() || !target.isPickable()) {
            return false;
        }
        Entity entity = this.getOwner();
        return entity == null || !entity.isPassengerOfSameVehicle(target);
    }
}
