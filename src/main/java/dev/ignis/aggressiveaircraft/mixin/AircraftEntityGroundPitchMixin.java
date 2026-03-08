package dev.ignis.aggressiveaircraft.mixin;

import immersive_aircraft.entity.AircraftEntity;
import immersive_aircraft.item.upgrade.VehicleStat;
import net.minecraft.Util;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class AircraftEntityGroundPitchMixin {
    @Unique
    private float aggressiveAircraft$yRotStored = 0;
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
        if((Object)this instanceof AircraftEntity aircraft) {
            if (aircraft.getPassengers().isEmpty() && aircraft.onGround()) {
                aggressiveAircraft$setYRot(aggressiveAircraft$yRotStored);
                ci.cancel();
            }else{
                aggressiveAircraft$yRotStored = this.getYRot();
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
