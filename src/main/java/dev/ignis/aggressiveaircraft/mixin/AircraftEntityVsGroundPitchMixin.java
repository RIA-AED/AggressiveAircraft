package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.AggressiveAircraft;
import immersive_aircraft.entity.AircraftEntity;
import immersive_aircraft.entity.misc.VehicleProperties;
import immersive_aircraft.item.upgrade.VehicleStat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

/**
 * Keeps Immersive Aircraft's ground pitch on normal terrain, but prevents that
 * world-space pitch from making an aircraft nose up while VS is dragging it
 * along a ship deck.
 */
@Mixin(value = AircraftEntity.class, remap = false)
public abstract class AircraftEntityVsGroundPitchMixin {

    /**
     * IA's original ground branch is:
     *
     * <pre>
     * setXRot((getXRot() + groundPitch) * 0.9F - groundPitch);
     * </pre>
     *
     * It converges to {@code -groundPitch}. On a VS deck that fixed world-space
     * angle is not relative to the deck, so converge to zero instead.
     */
    @Redirect(
            method = "updateVelocity",
            at = @At(
                    value = "INVOKE",
                    target = "Limmersive_aircraft/entity/misc/VehicleProperties;get(Limmersive_aircraft/item/upgrade/VehicleStat;)F"
            ),
            require = 2
    )
    private float aggressiveAircraft$removeGroundPitchOnShip(
            VehicleProperties properties,
            VehicleStat stat
    ) {
        AircraftEntity aircraft = (AircraftEntity) (Object) this;
        if (stat != VehicleStat.GROUND_PITCH
                || !AggressiveAircraft.VALKYRIENSKIES_LOADED
                || !(aircraft instanceof IEntityDraggingInformationProvider draggingProvider)
                || !draggingProvider.getDraggingInformation().isEntityBeingDraggedByAShip()) {
            return properties.get(stat);
        }

        return 0.0F;
    }
}
