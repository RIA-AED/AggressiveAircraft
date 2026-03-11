package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.AggressiveAircraft;
import immersive_aircraft.entity.AircraftEntity;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.item.upgrade.VehicleStat;
import net.minecraft.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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
    private float aggressiveAircraft$getYRotOffset(){
        return AggressiveAircraft.VALKYRIENSKIES_LOADED ? aggressiveAircraft$getShipYaw() : 0;
    }

    @Unique
    private Ship aggressiveAircraft$getShipManaging(){
        Entity self = (Entity)(Object)this;
        Level level = self.level();
        if (level == null) return null;

        AABBdc checkArea = new AABBd(
                self.getX() - 0.5, self.getY() - 2.0, self.getZ() - 0.5,
                self.getX() + 0.5, self.getY(), self.getZ() + 0.5
        );

        for (Ship ship : ValkyrienSkies.getShipsIntersecting(level, checkArea)) {
            return ship;
        }
        return null;
    }

    @Unique
    private float aggressiveAircraft$getShipYaw(){
        Ship ship = aggressiveAircraft$getShipManaging();
        if(ship != null){
            return (float)Math.toDegrees(ship.getTransform().getRotation().getEulerAnglesXYZ(new Vector3d()).y);
        }else return 0;
    }

    @Unique
    private float aggressiveAircraft$getRealYaw(float yaw){
        return yaw - aggressiveAircraft$getYRotOffset();
    }


    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    private void syncYawWithShip(CallbackInfo ci) {
        if ((Object)this instanceof VehicleEntity aircraft) {
            if (AggressiveAircraft.VALKYRIENSKIES_LOADED 
                    && aircraft.getPassengers().isEmpty() 
                    && aircraft.onGround()) {
                Ship ship = aggressiveAircraft$getShipManaging();
                if (ship != null) {
                    AggressiveAircraft.LOGGER.info("Is on ship, ship yaw: "+aggressiveAircraft$getShipYaw()+", self yaw: "+aggressiveAircraft$yRotStored+", current: "+getYRot());
                    float currentShipYaw = aggressiveAircraft$getShipYaw();
                    aggressiveAircraft$setYRot(aggressiveAircraft$yRotStored + currentShipYaw);
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
    private void forceGroundYawWhenNoPilot(float pitch, CallbackInfo ci) {
        if((Object)this instanceof VehicleEntity aircraft) {
            if (aircraft.getPassengers().isEmpty() && aircraft.onGround()) {
                aggressiveAircraft$setYRot(aggressiveAircraft$yRotStored+aggressiveAircraft$getYRotOffset());
                ci.cancel();
            }else{
                aggressiveAircraft$yRotStored = aggressiveAircraft$getRealYaw(this.getYRot());
            }
        }
    }

    @Shadow
    private float xRot;
    @Shadow
    public float xRotO;
    @Shadow
    private float yRot;

    @Shadow
    public abstract float getYRot();

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
}
