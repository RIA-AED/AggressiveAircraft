package dev.ignis.aggressiveaircraft.entities;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
    private float damage = 30.0f;
    private float explosionPower = 2.0f;
    private boolean destroyBlocks = false;

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

    public void setExplosionPower(double explosionPower) {
        this.explosionPower = (float) explosionPower;
    }

    public void setDestroyBlocks(boolean destroyBlocks) {
        this.destroyBlocks = destroyBlocks;
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
        Entity entity = this.getOwner();
        return entity == null || !entity.isPassengerOfSameVehicle(target);
    }

    @Override
    public void tick() {
        super.tick();

        if (getDeltaMovement().lengthSqr() < 0.1) {
            explode();
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
