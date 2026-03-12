package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.AggressiveAircraft;
import immersive_aircraft.entity.AircraftEntity;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.item.upgrade.VehicleStat;
import net.minecraft.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
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

import java.util.Optional;

@Mixin(Entity.class)
public abstract class AircraftEntityGroundPitchMixin {
    @Unique
    private float aggressiveAircraft$yRotStored = 0;

    @Unique
    private int aggressiveAircraft$lastPassengerCount = 0;

    @Unique
    private Ship aggressiveAircraft$cachedShip = null;

    @Unique
    private int aggressiveAircraft$shipCacheTick = 0;

    @Unique
    private float aggressiveAircraft$getYRotOffset(){
        return AggressiveAircraft.VALKYRIENSKIES_LOADED ? aggressiveAircraft$getShipYaw() : 0;
    }

    @Unique
    private static final int SHIP_CACHE_INTERVAL = 5;

    @Unique
    private Ship aggressiveAircraft$getShipManaging(){
        Entity self = (Entity)(Object)this;
        Level level = self.level();
        if (level == null) return null;

        long currentTick = level.getGameTime();
        if (currentTick - aggressiveAircraft$shipCacheTick >= SHIP_CACHE_INTERVAL) {
            aggressiveAircraft$shipCacheTick = (int) currentTick;

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
    private float aggressiveAircraft$getShipYaw() {
        Ship ship = aggressiveAircraft$getShipManaging();
        if (ship != null) {
            Quaterniondc rotation = ship.getTransform().getRotation();
            // 从四元数计算完整范围的偏航角
            return -(float) Math.toDegrees(Math.atan2(
                    2.0 * (rotation.w() * rotation.y() + rotation.x() * rotation.z()),
                    1.0 - 2.0 * (rotation.y() * rotation.y() + rotation.x() * rotation.x())
            ));
        }
        return 0;
    }

    @Unique
    private float aggressiveAircraft$normalizeYaw(float yaw) {
        while (yaw > 180.0f) yaw -= 360.0f;
        while (yaw < -180.0f) yaw += 360.0f;
        return yaw;
    }

    @Unique
    private float aggressiveAircraft$getRelativeYaw(float worldYaw, float shipYaw) {
        // 计算实体相对于船的偏航角，并归一化到 [-180, 180]
        return aggressiveAircraft$normalizeYaw(worldYaw - shipYaw);
    }

    @Unique
    private float aggressiveAircraft$getWorldYawFromRelative(float relativeYaw, float shipYaw) {
        // 将相对偏航转换为世界偏航
        return shipYaw + relativeYaw;
    }

    @Unique
    private float aggressiveAircraft$getWorldYawFromRelative(float relativeYaw){
        return relativeYaw + aggressiveAircraft$getYRotOffset();
    }


    @Shadow
    public float xRotO;

    @Shadow
    public abstract float getViewXRot(float tickDelta);

    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    private void syncYawWithShip(CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (self instanceof VehicleEntity aircraft) {
            int currentPassengerCount = aircraft.getPassengers().size();
            if (aggressiveAircraft$lastPassengerCount == 0 && currentPassengerCount == 1) {
                aggressiveAircraft$onPassengerEnter();
            } else if (aggressiveAircraft$lastPassengerCount == 1 && currentPassengerCount == 0) {
                aggressiveAircraft$onPassengerExit();
            }
            aggressiveAircraft$lastPassengerCount = currentPassengerCount;

            if (aircraft.getPassengers().isEmpty() && aircraft.onGround()) {
                if (AggressiveAircraft.VALKYRIENSKIES_LOADED) {
                    Ship ship = aggressiveAircraft$getShipManaging();
                    if (ship != null) {
                        float targetYaw = aggressiveAircraft$getWorldYawFromRelative(aggressiveAircraft$yRotStored);
                        aggressiveAircraft$setYRot(targetYaw);
                    }
                }
            }
        }
    }

    /**
     * 当飞机没有乘客时，强制俯仰角保持为 GROUND_PITCH
     */
    @Inject(
            method = "setXRot",
            at = @At("HEAD"),
            cancellable = true
    )
    private void forceGroundPitchWhenNoPilot(float pitch, CallbackInfo ci) {
        if((Object)this instanceof AircraftEntity aircraft) {
            if (aircraft.getPassengers().isEmpty() && aircraft.onGround()) {
                float groundPitch = -aircraft.getProperties().get(VehicleStat.GROUND_PITCH);
                aggressiveAircraft$setXRot(groundPitch);
                ci.cancel();
            }
        }
    }

    @Inject(
            method = "setYRot",
            at = @At("HEAD"),
            cancellable = true
    )
    private void forceGroundYawWhenNoPilot(float yaw, CallbackInfo ci) {
        if((Object)this instanceof VehicleEntity aircraft) {
            if (aircraft.getPassengers().isEmpty() && aircraft.onGround()) {
                Ship ship = aggressiveAircraft$getShipManaging();
                if (ship != null) {
                    float shipYaw = aggressiveAircraft$getShipYaw();
                    // 使用存储的相对偏航 + 当前船的偏航
                    float targetYaw = aggressiveAircraft$getWorldYawFromRelative(aggressiveAircraft$yRotStored, shipYaw);
                    //AggressiveAircraft.LOGGER.info("[Calc] Target World Yaw: " + targetYaw + ", Ship Yaw: " + shipYaw + ", Stored: " + aggressiveAircraft$yRotStored);
                    aggressiveAircraft$setYRot(targetYaw);
                    ci.cancel();
                }
            } else {
                // 存储相对偏航
                float shipYaw = aggressiveAircraft$getShipYaw();
                aggressiveAircraft$yRotStored = aggressiveAircraft$getRelativeYaw(yRot, shipYaw);
                //AggressiveAircraft.LOGGER.info("[Set] World Yaw: " + yRot + ", Ship Yaw: " + shipYaw + ", Stored: " + aggressiveAircraft$yRotStored);
            }
        }
    }

    @Shadow
    private float xRot;
    @Shadow
    private float yRot;

    @Shadow
    public abstract float getYRot();

    @Shadow private boolean onGround;

    @Unique
    private void aggressiveAircraft$setXRot(float pitch) {
        if (!Float.isFinite(pitch)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + pitch + ", discarding.");
        } else {
            this.xRot = pitch;
            this.xRotO = pitch;
        }
    }

    @Unique
    private void aggressiveAircraft$setYRot(float yaw) {
        if (!Float.isFinite(yaw)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + yaw + ", discarding.");
        } else {
            this.yRot = yaw;
        }
    }

    @Unique
    private void aggressiveAircraft$onPassengerEnter() {
        // 乘客坐下时触发
    }

    @Unique
    private void aggressiveAircraft$onPassengerExit() {
        // 乘客离开时触发
    }
}
