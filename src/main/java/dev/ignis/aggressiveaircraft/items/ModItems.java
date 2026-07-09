package dev.ignis.aggressiveaircraft.items;

import dev.ignis.aggressiveaircraft.AggressiveAircraft;
import dev.ignis.aggressiveaircraft.blocks.ModBlocks;
import immersive_aircraft.item.WeaponItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AggressiveAircraft.MODID);

    public static final RegistryObject<Item> MACHINE_GUN = ITEMS.register("machine_gun",
            () -> new WeaponItem(new Item.Properties().stacksTo(1), immersive_aircraft.entity.misc.WeaponMount.Type.FRONT));

    public static final RegistryObject<Item> HEAVY_CANNON = ITEMS.register("heavy_cannon",
            () -> new WeaponItem(new Item.Properties().stacksTo(1), immersive_aircraft.entity.misc.WeaponMount.Type.ROTATING));

    public static final RegistryObject<Item> HEAVY_BOMB_BAY = ITEMS.register("heavy_bomb_bay",
            () -> new WeaponItem(new Item.Properties().stacksTo(1), immersive_aircraft.entity.misc.WeaponMount.Type.DROP));

    public static final RegistryObject<Item> LIGHT_MG_AMMO = ITEMS.register("light_mg_ammo",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> HE_CANNON_AMMO = ITEMS.register("he_cannon_ammo",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> BOMB_200LB = ITEMS.register("bomb_200lb",
            () -> new Item(new Item.Properties().stacksTo(2)));

    public static final RegistryObject<Item> HOMING_ROCKET_LAUNCHER = ITEMS.register("homing_rocket_launcher",
            () -> new WeaponItem(new Item.Properties().stacksTo(1), immersive_aircraft.entity.misc.WeaponMount.Type.FRONT));

    public static final RegistryObject<Item> HOMING_ROCKET_AMMO = ITEMS.register("homing_rocket_ammo",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> CLUSTER_DISPENSER = ITEMS.register("cluster_dispenser",
            () -> new WeaponItem(new Item.Properties().stacksTo(1), immersive_aircraft.entity.misc.WeaponMount.Type.DROP));

    public static final RegistryObject<Item> CLUSTER_BOMB = ITEMS.register("cluster_bomb",
            () -> new Item(new Item.Properties().stacksTo(2)));

    public static final RegistryObject<Item> ROTARY_CANNON = ITEMS.register("rotary_cannon",
            () -> new WeaponItem(new Item.Properties().stacksTo(1), immersive_aircraft.entity.misc.WeaponMount.Type.FRONT));

    public static final RegistryObject<Item> ROCKET_POD = ITEMS.register("rocket_pod",
            () -> new WeaponItem(new Item.Properties().stacksTo(1), immersive_aircraft.entity.misc.WeaponMount.Type.FRONT));

    public static final RegistryObject<Item> ROCKET_POD_AMMO = ITEMS.register("rocket_pod_ammo",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> NAPALM_BOMB_BAY = ITEMS.register("napalm_bomb_bay",
            () -> new WeaponItem(new Item.Properties().stacksTo(1), immersive_aircraft.entity.misc.WeaponMount.Type.DROP));

    public static final RegistryObject<Item> NAPALM_BOMB_AMMO = ITEMS.register("napalm_bomb_ammo",
            () -> new Item(new Item.Properties().stacksTo(4)));

    // --- Supply Station Block Items ---

    public static final RegistryObject<Item> AMMO_SUPPLY_STATION = ITEMS.register("ammo_supply_station",
            () -> new BlockItem(ModBlocks.AMMO_SUPPLY_STATION.get(), new Item.Properties()));

    public static final RegistryObject<Item> FUEL_SUPPLY_STATION = ITEMS.register("fuel_supply_station",
            () -> new BlockItem(ModBlocks.FUEL_SUPPLY_STATION.get(), new Item.Properties()));

    public static final RegistryObject<Item> REPAIR_STATION = ITEMS.register("repair_station",
            () -> new BlockItem(ModBlocks.REPAIR_STATION.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
