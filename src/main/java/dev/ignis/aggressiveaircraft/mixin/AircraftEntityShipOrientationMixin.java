package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.compat.vs.AircraftShipContextProvider;
import immersive_aircraft.entity.AircraftEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * IA's ground roll decay and pitch stabilizer operate in world axes. While an
 * aircraft is supported by a ship those operations would make it counter-rotate
 * against a tilted deck, so the relative orientation is preserved instead.
 */
@Mixin(value = AircraftEntity.class, remap = false)
public abstract class AircraftEntityShipOrientationMixin {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Limmersive_aircraft/entity/AircraftEntity;setZRot(F)V",
                    remap = false
            ),
            require = 2,
            remap = true
    )
    private void aggressiveAircraft$preserveShipRelativeRoll(
            AircraftEntity aircraft,
            float roll
    ) {
        if (aircraft.onGround()
                && aircraft instanceof AircraftShipContextProvider provider
                && provider.aggressiveAircraft$getShipContext().isAttached()) {
            return;
        }
        aircraft.setZRot(roll);
    }

    @Redirect(
            method = "updateController",
            at = @At(
                    value = "INVOKE",
                    target = "Limmersive_aircraft/entity/AircraftEntity;setXRot(F)V",
                    ordinal = 1
            ),
            require = 1,
            remap = true
    )
    private void aggressiveAircraft$preserveShipRelativePitch(
            AircraftEntity aircraft,
            float pitch
    ) {
        if (aircraft.onGround()
                && aircraft instanceof AircraftShipContextProvider provider
                && provider.aggressiveAircraft$getShipContext().isAttached()) {
            return;
        }
        aircraft.setXRot(pitch);
    }
}
