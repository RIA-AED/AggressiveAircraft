package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.compat.vs.AircraftShipContextProvider;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Completes IA's six-axis ship transform after VS has applied its generic
 * entity position/yaw drag.
 */
@Pseudo
@Mixin(
        targets = "org.valkyrienskies.mod.common.util.EntityDragger",
        remap = false
)
public abstract class VsEntityDraggerAircraftMixin {

    @Inject(method = "dragEntitiesWithShips", at = @At("TAIL"), require = 1)
    private void aggressiveAircraft$finishAircraftShipTransform(
            Iterable<? extends Entity> entities,
            boolean preTick,
            CallbackInfo ci
    ) {
        if (preTick) {
            return;
        }

        for (Entity entity : entities) {
            if (entity instanceof VehicleEntity aircraft
                    && aircraft instanceof AircraftShipContextProvider provider) {
                provider.aggressiveAircraft$getShipContext().afterShipDrag(aircraft);
            }
        }
    }
}
