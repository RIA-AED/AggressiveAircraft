package dev.ignis.aggressiveaircraft.entities;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;

public class HeavyBombEntity extends PrimedTnt {
    private float explosionPower = 15.0f;
    private boolean destroyBlocks = true;
    private boolean wasOnGround = false;

    private static final EntityDataAccessor<Boolean> DATA_ORIENTATION_FROZEN =
            SynchedEntityData.defineId(HeavyBombEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_FROZEN_PITCH =
            SynchedEntityData.defineId(HeavyBombEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_FROZEN_YAW =
            SynchedEntityData.defineId(HeavyBombEntity.class, EntityDataSerializers.FLOAT);

    public HeavyBombEntity(EntityType<? extends PrimedTnt> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ORIENTATION_FROZEN, false);
        this.entityData.define(DATA_FROZEN_PITCH, 0.0f);
        this.entityData.define(DATA_FROZEN_YAW, 0.0f);
    }

    public void setExplosionPower(double explosionPower) {
        this.explosionPower = (float) explosionPower;
    }

    public void setDestroyBlocks(boolean destroyBlocks) {
        this.destroyBlocks = destroyBlocks;
    }

    @Override
    public void tick() {
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
        }
        // Save velocity before move() for orientation freezing on landing
        var preMoveVelocity = this.getDeltaMovement();
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));
        }
        // On first landing, reset fuse to exactly 2 seconds and freeze orientation
        boolean nowOnGround = this.onGround();
        if (nowOnGround && !wasOnGround) {
            this.setFuse(40);
            // Freeze falling angle
            float vx = (float) preMoveVelocity.x;
            float vy = (float) preMoveVelocity.y;
            float vz = (float) preMoveVelocity.z;
            float hSpeed = (float) Math.sqrt(vx * vx + vz * vz);
            if (hSpeed > 0.001f || Math.abs(vy) > 0.001f) {
                float yaw = (float) Math.atan2(vx, vz);
                float pitch = (float) Math.atan2(vy, hSpeed);
                this.entityData.set(DATA_FROZEN_PITCH, pitch);
                this.entityData.set(DATA_FROZEN_YAW, yaw);
                this.entityData.set(DATA_ORIENTATION_FROZEN, true);
            }
        }
        wasOnGround = nowOnGround;

        int i = this.getFuse() - 1;
        this.setFuse(i);
        if (i <= 0) {
            this.discard();
            if (!this.level().isClientSide) {
                this.boom();
            }
        } else {
            this.updateInWaterStateAndDoFluidPushing();
            if (this.level().isClientSide) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
            }
        }
    }

    private void boom() {
        Level.ExplosionInteraction interaction = destroyBlocks 
            ? Level.ExplosionInteraction.TNT 
            : Level.ExplosionInteraction.NONE;
        
        Entity owner = this.getOwner();
        this.level().explode(
            owner != null ? owner : this,
            this.getX(),
            this.getY(0.0625),
            this.getZ(),
            explosionPower,
            interaction
        );
    }

    public boolean isOrientationFrozen() {
        return this.entityData.get(DATA_ORIENTATION_FROZEN);
    }

    public float getFrozenPitch() {
        return this.entityData.get(DATA_FROZEN_PITCH);
    }

    public float getFrozenYaw() {
        return this.entityData.get(DATA_FROZEN_YAW);
    }
}
