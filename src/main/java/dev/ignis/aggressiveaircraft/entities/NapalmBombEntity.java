package dev.ignis.aggressiveaircraft.entities;

import dev.ignis.aggressiveaircraft.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

public class NapalmBombEntity extends PrimedTnt {
    private static final Random random = new Random();

    public NapalmBombEntity(EntityType<? extends PrimedTnt> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void tick() {
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));
        }
        int i = this.getFuse() - (onGround() ? 5 : 1);
        this.setFuse(i);
        if (i <= 0) {
            this.discard();
            if (!this.level().isClientSide) {
                this.napalmExplosion();
            }
        } else {
            this.updateInWaterStateAndDoFluidPushing();
            if (this.level().isClientSide) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
            }
        }
    }

    private void napalmExplosion() {
        int radius = ModConfig.NAPALM_BOMB_FIRE_RADIUS.get();
        int fireDamage = ModConfig.NAPALM_BOMB_FIRE_DAMAGE.get();
        int burnDuration = ModConfig.NAPALM_BOMB_BURN_DURATION.get();
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        Level level = this.level();

        // 1. Ignite entities within radius and deal fire damage
        AABB aabb = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> e.distanceToSqr(this) <= (double) radius * radius);
        DamageSource fireSource = level.damageSources().onFire();
        for (LivingEntity entity : entities) {
            entity.setSecondsOnFire(burnDuration);
            entity.hurt(fireSource, fireDamage);
        }

        // 2. Try to ignite block surfaces within radius (0.75 probability)
        BlockPos center = this.blockPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    if (random.nextFloat() > 0.75f) continue;

                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).isAir()) continue;

                    // Check if any adjacent face has a solid block to support fire
                    boolean canPlaceFire = false;
                    for (Direction dir : Direction.values()) {
                        BlockPos adjacent = pos.relative(dir);
                        BlockState adjacentState = level.getBlockState(adjacent);
                        if (adjacentState.isFaceSturdy(level, adjacent, dir.getOpposite())) {
                            canPlaceFire = true;
                            break;
                        }
                    }

                    if (canPlaceFire) {
                        level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
                    }
                }
            }
        }

        // 3. Spawn particle effects - send a small explosion to trigger client-side particles
        // Use a small explosion that doesn't destroy blocks to generate visual effects
        level.explode(
            this.getOwner() != null ? this.getOwner() : this,
            x, y, z,
            2.0f,
            true, // fire
            Level.ExplosionInteraction.NONE
        );
    }
}
