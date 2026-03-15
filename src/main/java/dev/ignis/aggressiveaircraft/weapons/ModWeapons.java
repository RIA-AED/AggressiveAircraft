package dev.ignis.aggressiveaircraft.weapons;

import dev.ignis.aggressiveaircraft.AggressiveAircraft;
import immersive_aircraft.WeaponRegistry;
import net.minecraft.resources.ResourceLocation;

public class ModWeapons {
    public static void register() {
        WeaponRegistry.register(
                ResourceLocation.tryBuild(AggressiveAircraft.MODID, "machine_gun"),
                MachineGun::new
        );
        WeaponRegistry.register(
                ResourceLocation.tryBuild(AggressiveAircraft.MODID, "heavy_cannon"),
                HeavyCannon::new
        );
        WeaponRegistry.register(
                ResourceLocation.tryBuild(AggressiveAircraft.MODID, "heavy_bomb_bay"),
                HeavyBombBay::new
        );
        WeaponRegistry.register(
                ResourceLocation.tryBuild(AggressiveAircraft.MODID, "homing_rocket_launcher"),
                HomingRocketLauncher::new
        );
        WeaponRegistry.register(
                ResourceLocation.tryBuild(AggressiveAircraft.MODID, "cluster_dispenser"),
                ClusterDispenser::new
        );
    }
}
