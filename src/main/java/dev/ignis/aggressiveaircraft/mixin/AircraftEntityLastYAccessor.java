package dev.ignis.aggressiveaircraft.mixin;

import immersive_aircraft.entity.AircraftEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AircraftEntity.class, remap = false)
public interface AircraftEntityLastYAccessor {

    @Accessor("lastY")
    void aggressiveAircraft$setLastY(double value);
}
