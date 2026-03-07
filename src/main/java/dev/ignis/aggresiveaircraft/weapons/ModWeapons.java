package dev.ignis.aggresiveaircraft.weapons;

import dev.ignis.aggresiveaircraft.AggresiveAircraft;
import dev.ignis.aggresiveaircraft.items.ModItems;
import immersive_aircraft.WeaponRegistry;
import net.minecraft.resources.ResourceLocation;

public class ModWeapons {
    public static void register() {
        WeaponRegistry.register(
                ResourceLocation.tryBuild(AggresiveAircraft.MODID, "machine_gun"),
                MachineGun::new
        );
        WeaponRegistry.register(
                ResourceLocation.tryBuild(AggresiveAircraft.MODID, "heavy_cannon"),
                HeavyCannon::new
        );
        WeaponRegistry.register(
                ResourceLocation.tryBuild(AggresiveAircraft.MODID, "heavy_bomb_bay"),
                HeavyBombBay::new
        );
    }
}
