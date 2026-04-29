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

    // Homing Rocket Config
    public static final ForgeConfigSpec.ConfigValue<String> HOMING_ROCKET_AMMO;
    public static final ForgeConfigSpec.IntValue HOMING_ROCKET_AMMO_CONSUMPTION;
    public static final ForgeConfigSpec.DoubleValue HOMING_ROCKET_COOLDOWN;
    public static final ForgeConfigSpec.IntValue HOMING_ROCKET_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue HOMING_ROCKET_EXPLOSION_POWER;

    // Cluster Dispenser Config
    public static final ForgeConfigSpec.ConfigValue<String> CLUSTER_DISPENSER_AMMO;
    public static final ForgeConfigSpec.IntValue CLUSTER_DISPENSER_AMMO_CONSUMPTION;
    public static final ForgeConfigSpec.DoubleValue CLUSTER_DISPENSER_COOLDOWN;
    public static final ForgeConfigSpec.IntValue CLUSTER_DISPENSER_BOMBLET_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue CLUSTER_DISPENSER_BOMBLET_EXPLOSION_POWER;
    public static final ForgeConfigSpec.BooleanValue CLUSTER_DISPENSER_BOMBLET_DESTROY_BLOCKS;

    // Rocket Pod Config
    public static final ForgeConfigSpec.ConfigValue<String> ROCKET_POD_AMMO;
    public static final ForgeConfigSpec.IntValue ROCKET_POD_AMMO_CONSUMPTION;
    public static final ForgeConfigSpec.IntValue ROCKET_POD_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue ROCKET_POD_FIRE_RATE;
    public static final ForgeConfigSpec.DoubleValue ROCKET_POD_EXPLOSION_POWER;
    public static final ForgeConfigSpec.BooleanValue ROCKET_POD_DESTROY_BLOCKS;

    // Rotary Cannon Config
    public static final ForgeConfigSpec.ConfigValue<String> ROTARY_CANNON_AMMO;
    public static final ForgeConfigSpec.IntValue ROTARY_CANNON_AMMO_CONSUMPTION;
    public static final ForgeConfigSpec.IntValue ROTARY_CANNON_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue ROTARY_CANNON_FIRE_RATE;
    public static final ForgeConfigSpec.DoubleValue ROTARY_CANNON_EXPLOSION_POWER;
    public static final ForgeConfigSpec.BooleanValue ROTARY_CANNON_DESTROY_BLOCKS;

    // Inventory Control Config
    public static final ForgeConfigSpec.DoubleValue INVENTORY_HEIGHT_THRESHOLD;

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
                .defineInRange("explosion_power", 3.0, 0.0, 20.0);
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

        BUILDER.push("homing_rocket");
        HOMING_ROCKET_AMMO = BUILDER
                .comment("Ammo item ID for Homing Rocket Launcher")
                .define("ammo", "aggressiveaircraft:homing_rocket_ammo");
        HOMING_ROCKET_AMMO_CONSUMPTION = BUILDER
                .comment("Ammo consumption per shot")
                .defineInRange("ammo_consumption", 1, 1, 64);
        HOMING_ROCKET_COOLDOWN = BUILDER
                .comment("Cooldown time in seconds")
                .defineInRange("cooldown", 5.0, 0.0, 60.0);
        HOMING_ROCKET_DAMAGE = BUILDER
                .comment("Direct hit damage")
                .defineInRange("damage", 200, 1, 500);
        HOMING_ROCKET_EXPLOSION_POWER = BUILDER
                .comment("Explosion power on impact")
                .defineInRange("explosion_power", 2.0, 0.0, 10.0);
        BUILDER.pop();

        BUILDER.push("cluster_dispenser");
        CLUSTER_DISPENSER_AMMO = BUILDER
                .comment("Ammo item ID for Cluster Dispenser")
                .define("ammo", "aggressiveaircraft:cluster_bomb");
        CLUSTER_DISPENSER_AMMO_CONSUMPTION = BUILDER
                .comment("Ammo consumption per drop")
                .defineInRange("ammo_consumption", 1, 1, 64);
        CLUSTER_DISPENSER_COOLDOWN = BUILDER
                .comment("Cooldown time in seconds")
                .defineInRange("cooldown", 30.0, 0.0, 300.0);
        CLUSTER_DISPENSER_BOMBLET_DAMAGE = BUILDER
                .comment("Damage per bomblet hit")
                .defineInRange("bomblet_damage", 1, 1, 500);
        CLUSTER_DISPENSER_BOMBLET_EXPLOSION_POWER = BUILDER
                .comment("Explosion power per bomblet")
                .defineInRange("bomblet_explosion_power", 4.0, 0.0, 20.0);
        CLUSTER_DISPENSER_BOMBLET_DESTROY_BLOCKS = BUILDER
                .comment("Whether bomblet explosions destroy blocks")
                .define("bomblet_destroy_blocks", false);
        BUILDER.pop();

        BUILDER.push("rocket_pod");
        ROCKET_POD_AMMO = BUILDER
                .comment("Ammo item ID for Rocket Pod")
                .define("ammo", "aggressiveaircraft:rocket_pod_ammo");
        ROCKET_POD_AMMO_CONSUMPTION = BUILDER
                .comment("Ammo consumption per shot")
                .defineInRange("ammo_consumption", 1, 1, 64);
        ROCKET_POD_DAMAGE = BUILDER
                .comment("Direct hit damage")
                .defineInRange("damage", 50, 1, 500);
        ROCKET_POD_FIRE_RATE = BUILDER
                .comment("Fire rate in shots per second")
                .defineInRange("fire_rate", 5.0, 0.1, 20.0);
        ROCKET_POD_EXPLOSION_POWER = BUILDER
                .comment("Explosion power on impact")
                .defineInRange("explosion_power", 2.0, 0.0, 20.0);
        ROCKET_POD_DESTROY_BLOCKS = BUILDER
                .comment("Whether explosion destroys blocks")
                .define("destroy_blocks", false);
        BUILDER.pop();

        BUILDER.push("rotary_cannon");
        ROTARY_CANNON_AMMO = BUILDER
                .comment("Ammo item ID for Rotary Cannon")
                .define("ammo", "aggressiveaircraft:he_cannon_ammo");
        ROTARY_CANNON_AMMO_CONSUMPTION = BUILDER
                .comment("Ammo consumption per shot")
                .defineInRange("ammo_consumption", 1, 1, 64);
        ROTARY_CANNON_DAMAGE = BUILDER
                .comment("Direct hit damage")
                .defineInRange("damage", 20, 1, 200);
        ROTARY_CANNON_FIRE_RATE = BUILDER
                .comment("Fire rate in shots per second")
                .defineInRange("fire_rate", 10.0, 0.1, 30.0);
        ROTARY_CANNON_EXPLOSION_POWER = BUILDER
                .comment("Explosion power on impact")
                .defineInRange("explosion_power", 2.0, 0.0, 20.0);
        ROTARY_CANNON_DESTROY_BLOCKS = BUILDER
                .comment("Whether explosion destroys blocks")
                .define("destroy_blocks", false);
        BUILDER.pop();

        BUILDER.push("inventory_control");
        INVENTORY_HEIGHT_THRESHOLD = BUILDER
                .comment("Height threshold below which inventory can be opened (blocks). Set to 0 to disable.")
                .defineInRange("height_threshold", 320.0, 0.0, 10000.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // Config values are accessed directly through the fields
    }
}
