package dev.ignis.aggressiveaircraft.mixin;

import immersive_aircraft.entity.EngineVehicle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EngineVehicle.class)
public interface EngineVehicleAccessor {
    @Accessor(value = "fuel",remap = false)
    int[] getFuelArray();
}
