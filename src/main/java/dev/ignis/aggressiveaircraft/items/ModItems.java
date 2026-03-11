package dev.ignis.aggressiveaircraft.items;

import dev.ignis.aggressiveaircraft.AggressiveAircraft;
import immersive_aircraft.item.WeaponItem;
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
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> HE_CANNON_AMMO = ITEMS.register("he_cannon_ammo",
            () -> new Item(new Item.Properties().stacksTo(4)));

    public static final RegistryObject<Item> BOMB_200LB = ITEMS.register("bomb_200lb",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
