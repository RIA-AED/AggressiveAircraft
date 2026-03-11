package dev.ignis.aggressiveaircraft;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = dev.ignis.aggressiveaircraft.AggressiveAircraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec SPEC;

    // Machine Gun Config
    public static final ForgeConfigSpec.ConfigValue<String> MACHINE_GUN_AMMO;
    public static final ForgeConfigSpec.IntValue MACHINE_GUN_AMMO_CONSUMPTION;
    public static final ForgeConfigSpec.IntValue MACHINE_GUN_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue MACHINE_GUN_FIRE_RATE;

    // Heavy Cannon Config
    public static final ForgeConfigSpec.ConfigValue<String> HEAVY_CANNON_AMMO;
    public static final ForgeConfigSpec.IntValue HEAVY_CANNON_AMMO_CONSUMPTION;
    public static final ForgeConfigSpec.IntValue HEAVY_CANNON_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue HEAVY_CANNON_FIRE_RATE;
    public static final ForgeConfigSpec.DoubleValue HEAVY_CANNON_EXPLOSION_POWER;
    public static final ForgeConfigSpec.BooleanValue HEAVY_CANNON_DESTROY_BLOCKS;

    // Heavy Bomb Bay Config
    public static final ForgeConfigSpec.ConfigValue<String> HEAVY_BOMB_BAY_AMMO;
    public static final ForgeConfigSpec.IntValue HEAVY_BOMB_BAY_AMMO_CONSUMPTION;
    public static final ForgeConfigSpec.DoubleValue HEAVY_BOMB_BAY_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue HEAVY_BOMB_BAY_EXPLOSION_POWER;
    public static final ForgeConfigSpec.BooleanValue HEAVY_BOMB_BAY_DESTROY_BLOCKS;

    static {
        BUILDER.push("machine_gun");
        MACHINE_GUN_AMMO = BUILDER
                .comment("Ammo item ID for Machine Gun")
                .define("ammo", "aggressiveaircraft:light_mg_ammo");
        MACHINE_GUN_AMMO_CONSUMPTION = BUILDER
                .comment("Ammo consumption per shot")
                .defineInRange("ammo_consumption", 1, 1, 64);
        MACHINE_GUN_DAMAGE = BUILDER
                .comment("Damage per shot")
                .defineInRange("damage", 10, 1, 100);
        MACHINE_GUN_FIRE_RATE = BUILDER
                .comment("Fire rate in shots per second")
                .defineInRange("fire_rate", 8.0, 0.1, 20.0);
        BUILDER.pop();

        BUILDER.push("heavy_cannon");
        HEAVY_CANNON_AMMO = BUILDER
                .comment("Ammo item ID for Heavy Cannon")
                .define("ammo", "aggressiveaircraft:he_cannon_ammo");
        HEAVY_CANNON_AMMO_CONSUMPTION = BUILDER
                .comment("Ammo consumption per shot")
                .defineInRange("ammo_consumption", 1, 1, 64);
        HEAVY_CANNON_DAMAGE = BUILDER
                .comment("Direct hit damage")
                .defineInRange("damage", 30, 1, 200);
        HEAVY_CANNON_FIRE_RATE = BUILDER
                .comment("Fire rate in shots per second")
                .defineInRange("fire_rate", 1.5, 0.1, 10.0);
        HEAVY_CANNON_EXPLOSION_POWER = BUILDER
                .comment("Explosion power on impact")
                .defineInRange("explosion_power", 2.0, 0.0, 20.0);
        HEAVY_CANNON_DESTROY_BLOCKS = BUILDER
                .comment("Whether explosion destroys blocks")
                .define("destroy_blocks", false);
        BUILDER.pop();

        BUILDER.push("heavy_bomb_bay");
        HEAVY_BOMB_BAY_AMMO = BUILDER
                .comment("Ammo item ID for Heavy Bomb Bay")
                .define("ammo", "aggressiveaircraft:bomb_200lb");
        HEAVY_BOMB_BAY_AMMO_CONSUMPTION = BUILDER
                .comment("Ammo consumption per drop")
                .defineInRange("ammo_consumption", 4, 1, 64);
        HEAVY_BOMB_BAY_COOLDOWN = BUILDER
                .comment("Cooldown time in seconds")
                .defineInRange("cooldown", 15.0, 0.0, 300.0);
        HEAVY_BOMB_BAY_EXPLOSION_POWER = BUILDER
                .comment("Explosion power on impact")
                .defineInRange("explosion_power", 8.0, 0.0, 50.0);
        HEAVY_BOMB_BAY_DESTROY_BLOCKS = BUILDER
                .comment("Whether explosion destroys blocks")
                .define("destroy_blocks", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // Config values are accessed directly through the fields
    }
}
