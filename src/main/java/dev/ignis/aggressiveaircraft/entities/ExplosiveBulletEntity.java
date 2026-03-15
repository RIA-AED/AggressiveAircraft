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

public class ExplosiveBulletEntity extends AbstractHurtingProjectile {
    private float damage = 30.0f;
    private float explosionPower = 4.0f;
    private boolean destroyBlocks = false;

    public ExplosiveBulletEntity(EntityType<? extends ExplosiveBulletEntity> entityType, Level level) {
        super(entityType, level);
    }

    public float getScale() {
        return 0.5f;
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
        
        // 客户端：生成拖尾烟雾
        if (this.level().isClientSide) {
            Vec3 motion = this.getDeltaMovement();
            double speed = motion.length();
            
            // 根据速度生成不同密度的烟雾
            int particleCount = speed > 1.0 ? 2 : 1;
            for (int i = 0; i < particleCount; i++) {
                // 在子弹后方生成烟雾
                double offsetX = (this.random.nextDouble() - 0.5) * 0.2;
                double offsetY = (this.random.nextDouble() - 0.5) * 0.2;
                double offsetZ = (this.random.nextDouble() - 0.5) * 0.2;
                
                // 烟雾位置稍微向后偏移
                double smokeX = this.getX() - motion.x * 0.3 + offsetX;
                double smokeY = this.getY() - motion.y * 0.3 + offsetY;
                double smokeZ = this.getZ() - motion.z * 0.3 + offsetZ;
                
                this.level().addParticle(
                    ParticleTypes.SMOKE,
                    smokeX, smokeY, smokeZ,
                    -motion.x * 0.1, -motion.y * 0.1 + 0.02, -motion.z * 0.1
                );
            }
            
            // 偶尔生成火焰粒子
            if (this.random.nextInt(3) == 0) {
                this.level().addParticle(
                    ParticleTypes.FLAME,
                    this.getX(), this.getY(), this.getZ(),
                    0, 0, 0
                );
            }
        }
    }

    @Override
    public boolean isPushable() {
        return false; // 禁止被爆炸等外力推动
    }
}
