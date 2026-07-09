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
            // Client spawns particles locally without server packets
            if (this.level().isClientSide) {
                this.spawnNapalmParticles();
            }
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

    /**
     * Client-side only: spawn large amounts of smoke and fire particles
     */
    private void spawnNapalmParticles() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        Level level = this.level();
        int radius = ModConfig.NAPALM_BOMB_FIRE_RADIUS.get();

        // Large smoke cloud
        for (int i = 0; i < 80; i++) {
            double ox = (random.nextDouble() - 0.5) * radius * 1.5;
            double oy = random.nextDouble() * radius * 0.8;
            double oz = (random.nextDouble() - 0.5) * radius * 1.5;
            double vx = (random.nextDouble() - 0.5) * 0.3;
            double vy = random.nextDouble() * 0.4 + 0.1;
            double vz = (random.nextDouble() - 0.5) * 0.3;
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x + ox, y + oy, z + oz, vx, vy, vz);
        }

        // Large fire particles
        for (int i = 0; i < 60; i++) {
            double ox = (random.nextDouble() - 0.5) * radius;
            double oy = random.nextDouble() * radius * 0.5;
            double oz = (random.nextDouble() - 0.5) * radius;
            double vx = (random.nextDouble() - 0.5) * 0.2;
            double vy = random.nextDouble() * 0.3;
            double vz = (random.nextDouble() - 0.5) * 0.2;
            level.addParticle(ParticleTypes.FLAME, x + ox, y + oy, z + oz, vx, vy, vz);
        }

        // Extra large smoke puffs
        for (int i = 0; i < 40; i++) {
            double ox = (random.nextDouble() - 0.5) * radius;
            double oy = random.nextDouble() * radius * 0.6;
            double oz = (random.nextDouble() - 0.5) * radius;
            double vx = (random.nextDouble() - 0.5) * 0.15;
            double vy = random.nextDouble() * 0.5 + 0.2;
            double vz = (random.nextDouble() - 0.5) * 0.15;
            level.addParticle(ParticleTypes.LARGE_SMOKE, x + ox, y + oy, z + oz, vx, vy, vz);
        }

        // Lava-like dripping particles for napalm feel
        for (int i = 0; i < 20; i++) {
            double ox = (random.nextDouble() - 0.5) * radius * 0.8;
            double oy = random.nextDouble() * 2.0;
            double oz = (random.nextDouble() - 0.5) * radius * 0.8;
            level.addParticle(ParticleTypes.LAVA, x + ox, y + oy, z + oz, 0, 0, 0);
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

        // 2. Try to ignite block surfaces within radius, probability decays with distance
        BlockPos center = this.blockPosition();
        float maxIgniteChance = ModConfig.NAPALM_BOMB_IGNITE_CHANCE.get().floatValue();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > radius * radius) continue;

                    // Probability decays linearly from maxIgniteChance at center to 0 at edge
                    float distanceFactor = (float) Math.sqrt(distSq) / radius;
                    float igniteChance = maxIgniteChance * (1.0f - distanceFactor);
                    if (random.nextFloat() > igniteChance) continue;

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

        // 3. Small explosion for sound effect only (no block damage)
        level.explode(
            this.getOwner() != null ? this.getOwner() : this,
            x, y, z,
            2.0f,
            true, // fire
            Level.ExplosionInteraction.NONE
        );
    }
}
