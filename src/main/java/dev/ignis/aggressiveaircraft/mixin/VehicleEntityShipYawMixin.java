package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.AggressiveAircraft;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Quaterniondc;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

/**
 * Keeps an unoccupied IA vehicle at a stable ship-relative yaw.
 *
 * <p>VS already converts the yaw contained in {@code PacketEntityShipMotion}
 * to world space before calling {@link Entity#setYRot(float)}. This mixin must
 * therefore never transform yaw while a pilot is present. Doing so adds the
 * ship yaw twice on remote clients and produces the one-frame/first-mount
 * rotation seen by observers.</p>
 */
@Mixin(Entity.class)
public abstract class VehicleEntityShipYawMixin {

    @Unique
    private static final int aggressiveAircraft$SHIP_CACHE_INTERVAL = 5;

    @Unique
    private float aggressiveAircraft$relativeYaw;

    @Unique
    private boolean aggressiveAircraft$relativeYawInitialized;

    @Unique
    private long aggressiveAircraft$yawReferenceShipId = Long.MIN_VALUE;

    @Unique
    private int aggressiveAircraft$lastPassengerCount;

    @Unique
    private Ship aggressiveAircraft$cachedShip;

    @Unique
    private long aggressiveAircraft$shipCacheTick = Long.MIN_VALUE;

    @Inject(method = "tick", at = @At("TAIL"))
    @SuppressWarnings("ConstantValue")
    private void aggressiveAircraft$followShipYawWhileParked(CallbackInfo ci) {
        if (!((Object) this instanceof VehicleEntity aircraft)) {
            return;
        }

        int passengerCount = aircraft.getPassengers().size();
        if (aggressiveAircraft$lastPassengerCount > 0 && passengerCount == 0) {
            // The final piloted world yaw becomes the new parked relative yaw.
            aggressiveAircraft$relativeYawInitialized = false;
        }
        aggressiveAircraft$lastPassengerCount = passengerCount;

        // A piloted vehicle is fully owned by IA + VS networking/interpolation.
        // In particular, do not add ship yaw to Entity#setYRot here.
        if (passengerCount > 0) {
            return;
        }

        if (!AggressiveAircraft.VALKYRIENSKIES_LOADED || !aircraft.onGround()) {
            aggressiveAircraft$clearYawReference();
            return;
        }

        Ship ship = aggressiveAircraft$getShipManaging();
        if (ship == null) {
            aggressiveAircraft$clearYawReference();
            return;
        }

        float shipYaw = aggressiveAircraft$getShipYaw(ship);
        long shipId = ship.getId();
        if (!aggressiveAircraft$relativeYawInitialized
                || aggressiveAircraft$yawReferenceShipId != shipId) {
            aggressiveAircraft$relativeYaw =
                    aggressiveAircraft$normalizeYaw(aircraft.getYRot() - shipYaw);
            aggressiveAircraft$relativeYawInitialized = true;
            aggressiveAircraft$yawReferenceShipId = shipId;
        }

        aggressiveAircraft$setYRot(shipYaw + aggressiveAircraft$relativeYaw);
    }

    @Unique
    private Ship aggressiveAircraft$getShipManaging() {
        Entity self = (Entity) (Object) this;
        Level level = self.level();
        if (level == null) {
            return null;
        }

        long currentTick = level.getGameTime();
        boolean clockMovedBackwards = currentTick < aggressiveAircraft$shipCacheTick;
        boolean cacheExpired = currentTick - aggressiveAircraft$shipCacheTick
                >= aggressiveAircraft$SHIP_CACHE_INTERVAL;
        if (aggressiveAircraft$shipCacheTick == Long.MIN_VALUE
                || clockMovedBackwards
                || cacheExpired) {
            aggressiveAircraft$shipCacheTick = currentTick;
            AABBdc checkArea = new AABBd(
                    self.getX() - 0.5, self.getY() - 2.0, self.getZ() - 0.5,
                    self.getX() + 0.5, self.getY(), self.getZ() + 0.5
            );

            aggressiveAircraft$cachedShip = null;
            for (Ship ship : ValkyrienSkies.getShipsIntersecting(level, checkArea)) {
                aggressiveAircraft$cachedShip = ship;
                break;
            }
        }
        return aggressiveAircraft$cachedShip;
    }

    @Unique
    private float aggressiveAircraft$getShipYaw(Ship ship) {
        Quaterniondc rotation = ship.getTransform().getRotation();
        return -(float) Math.toDegrees(Math.atan2(
                2.0 * (rotation.w() * rotation.y() + rotation.x() * rotation.z()),
                1.0 - 2.0 * (rotation.y() * rotation.y() + rotation.x() * rotation.x())
        ));
    }

    @Unique
    private float aggressiveAircraft$normalizeYaw(float yaw) {
        while (yaw > 180.0F) {
            yaw -= 360.0F;
        }
        while (yaw < -180.0F) {
            yaw += 360.0F;
        }
        return yaw;
    }

    @Unique
    private void aggressiveAircraft$clearYawReference() {
        aggressiveAircraft$relativeYawInitialized = false;
        aggressiveAircraft$yawReferenceShipId = Long.MIN_VALUE;
    }

    @Shadow
    private float yRot;

    /**
     * Writes the backing field intentionally, avoiding interception of VS's
     * normal setYRot calls and avoiding recursive injection.
     */
    @Unique
    private void aggressiveAircraft$setYRot(float yaw) {
        if (!Float.isFinite(yaw)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + yaw + ", discarding.");
        } else {
            this.yRot = yaw;
        }
    }
}
