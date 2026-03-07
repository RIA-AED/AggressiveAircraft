package dev.ignis.aggressiveaircraft.mixin;

import immersive_aircraft.entity.AircraftEntity;
import immersive_aircraft.item.upgrade.VehicleStat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AircraftEntity.class)
public class AircraftEntityDebugMixin {

    @Unique
    private static final Logger aggressiveAircraft$LOGGER = LogManager.getLogger("AircraftDebug");
    @Unique
    private float aggressiveAircraft$lastPitch = 0.0f;
    @Unique
    private int aggressiveAircraft$tickCounter = 0;


    @Inject(
            method = "m_8119_()V",
            at = @At("HEAD"),
            remap = false
    )
    private void debugTickStart(CallbackInfo ci) {
        AircraftEntity aircraft = (AircraftEntity)(Object)this;
        if (!aircraft.level().isClientSide()) return; // 只调试客户端

        float currentPitch = aircraft.getXRot();
        float delta = currentPitch - aggressiveAircraft$lastPitch;

        // 每10 ticks输出一次，或者俯仰角变化超过5度时
        if (++aggressiveAircraft$tickCounter >= 10 || Math.abs(delta) > 5.0f) {
            aggressiveAircraft$tickCounter = 0;
            aggressiveAircraft$LOGGER.info(String.format(
                    "[TickStart] Type: %s, Pitch: %.2f (delta: %.2f), OnGround: %s, HasPassenger: %s, isVehicle: %s",
                    aircraft.getClass().getSimpleName(),
                    currentPitch,
                    delta,
                    aircraft.onGround(),
                    !aircraft.getPassengers().isEmpty(),
                    aircraft.isVehicle()
            ));
        }
        aggressiveAircraft$lastPitch = currentPitch;
    }

    @Inject(
            method = "updateController",
            at = @At("HEAD"),
            remap = false
    )
    private void debugUpdateControllerStart(CallbackInfo ci) {
        AircraftEntity aircraft = (AircraftEntity)(Object)this;
        if (!aircraft.level().isClientSide()) return;
        if (!aircraft.getPassengers().isEmpty()) return; // 只调试无乘客情况

        aggressiveAircraft$LOGGER.info(String.format(
                "[UpdateController START] Pitch: %.2f, pressingInterpolatedZ: %.2f, STABILIZER: %.2f, onGround: %s",
                aircraft.getXRot(),
                aircraft.pressingInterpolatedZ.getSmooth(),
                aircraft.getProperties().getAdditive(VehicleStat.STABILIZER),
                aircraft.onGround()
        ));
    }

    @Inject(
            method = "updateController",
            at = @At("RETURN"),
            remap = false
    )
    private void debugUpdateControllerEnd(CallbackInfo ci) {
        AircraftEntity aircraft = (AircraftEntity)(Object)this;
        if (!aircraft.level().isClientSide()) return;
        if (!aircraft.getPassengers().isEmpty()) return;

        aggressiveAircraft$LOGGER.info(String.format(
                "[UpdateController END] Pitch: %.2f",
                aircraft.getXRot()
        ));
    }

    @Inject(
            method = "updateVelocity",
            at = @At("HEAD"),
            remap = false
    )
    private void debugUpdateVelocityStart(CallbackInfo ci) {
        AircraftEntity aircraft = (AircraftEntity)(Object)this;
        if (!aircraft.level().isClientSide()) return;
        if (!aircraft.getPassengers().isEmpty()) return;

        aggressiveAircraft$LOGGER.info(String.format(
                "[UpdateVelocity START] Pitch: %.2f, onGround: %s, GROUND_PITCH: %.2f",
                aircraft.getXRot(),
                aircraft.onGround(),
                aircraft.getProperties().get(VehicleStat.GROUND_PITCH)
        ));
    }

    @Inject(
            method = "updateVelocity",
            at = @At("RETURN"),
            remap = false
    )
    private void debugUpdateVelocityEnd(CallbackInfo ci) {
        AircraftEntity aircraft = (AircraftEntity)(Object)this;
        if (!aircraft.level().isClientSide()) return;
        if (!aircraft.getPassengers().isEmpty()) return;

        aggressiveAircraft$LOGGER.info(String.format(
                "[UpdateVelocity END] Pitch: %.2f",
                aircraft.getXRot()
        ));
    }
}
