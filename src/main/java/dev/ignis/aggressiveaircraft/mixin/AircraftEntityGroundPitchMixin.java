package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.AggressiveAircraft;
import immersive_aircraft.entity.AircraftEntity;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.item.upgrade.VehicleStat;
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

@Mixin(Entity.class)
public abstract class AircraftEntityGroundPitchMixin {

    //todo 将俯仰修复逻辑注入客户端isGround实现

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
    private boolean aggressiveAircraft$isTrulyParked() {
        Entity self = (Entity)(Object)this;
        VehicleEntity aircraft = (VehicleEntity) self;
        // 仅当飞机完全没有乘客时才认为是"停放"状态
        // 这样其他玩家驾驶的飞机不会被强制地面姿态
        return aircraft.getPassengers().isEmpty();
    }

    @Unique
    private boolean aggressiveAircraft$isLocalPlayerPiloting() {
        Entity self = (Entity)(Object)this;
        if (!self.level().isClientSide()) return false;
        VehicleEntity aircraft = (VehicleEntity) self;
        for (Entity passenger : aircraft.getPassengers()) {
            if (passenger instanceof net.minecraft.client.player.LocalPlayer) {
                return true;
            }
        }
        return false;
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

            // 仅在无人停放且着地时，主动修正偏航跟随飞艇旋转
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
            if (aggressiveAircraft$cachedShip!=null && aggressiveAircraft$isTrulyParked() && aircraft.onGround()) {
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
            if (!aircraft.onGround()) {
                // 不在地面：仅存储相对偏航供后续使用
                float shipYaw = aggressiveAircraft$getShipYaw();
                if (shipYaw != 0) {
                    aggressiveAircraft$yRotStored = aggressiveAircraft$getRelativeYaw(yRot, shipYaw);
                }
                return;
            }

            Ship ship = aggressiveAircraft$getShipManaging();
            if (ship == null) return;

            float shipYaw = aggressiveAircraft$getShipYaw();

            if (aggressiveAircraft$isLocalPlayerPiloting()) {
                // 本地玩家驾驶：yRot在ship-local空间，存储相对偏航供停放时使用
                aggressiveAircraft$yRotStored = aggressiveAircraft$getRelativeYaw(yRot, shipYaw);
                // 不取消，让正常的setYRot执行（本地控制不需要校正）
            } else if (aircraft.getPassengers().isEmpty()) {
                // 无人停放：使用存储的相对偏航 + 当前船的偏航，保持跟随船旋转
                float targetYaw = aggressiveAircraft$getWorldYawFromRelative(aggressiveAircraft$yRotStored, shipYaw);
                aggressiveAircraft$setYRot(targetYaw);
                ci.cancel();
            } else {
                // 观察者视角，有其他玩家在驾驶：
                // 服务端发来的yaw是ship-local空间（飞行员客户端在VS局部坐标系中设置），
                // 需要加上shipYaw转换为世界偏航
                aggressiveAircraft$yRotStored = aggressiveAircraft$normalizeYaw(yaw);
                float targetYaw = aggressiveAircraft$getWorldYawFromRelative(yaw, shipYaw);
                aggressiveAircraft$setYRot(targetYaw);
                ci.cancel();
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
