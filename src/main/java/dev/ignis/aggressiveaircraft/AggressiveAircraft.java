package dev.ignis.aggressiveaircraft;

import com.mojang.logging.LogUtils;
import dev.ignis.aggressiveaircraft.client.render.ClusterDispenserRenderer;
import dev.ignis.aggressiveaircraft.client.render.ExplosiveBulletRenderer;
import dev.ignis.aggressiveaircraft.client.render.HeavyBombRenderer;
import dev.ignis.aggressiveaircraft.client.render.HomingRocketRenderer;
import immersive_aircraft.WeaponRendererRegistry;
import immersive_aircraft.client.render.entity.weaponRenderer.SimpleWeaponRenderer;
import immersive_aircraft.resources.bbmodel.BBAnimationVariables;
import dev.ignis.aggressiveaircraft.entities.ModEntities;
import dev.ignis.aggressiveaircraft.items.ModItems;
import dev.ignis.aggressiveaircraft.weapons.ModWeapons;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import org.slf4j.Logger;

import static dev.ignis.aggressiveaircraft.ModConfig.SPEC;

@Mod(AggressiveAircraft.MODID)
public class AggressiveAircraft {

    public static final String MODID = "aggressiveaircraft";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean VALKYRIENSKIES_LOADED = true;

    @SuppressWarnings("removal")
    public AggressiveAircraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        ResourceKey<CreativeModeTab> immersiveAircraftTab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.tryBuild("immersive_aircraft", "immersive_aircraft"));
        if (event.getTabKey().equals(immersiveAircraftTab)) {
            event.accept(ModItems.MACHINE_GUN.get());
            event.accept(ModItems.HEAVY_CANNON.get());
            event.accept(ModItems.HEAVY_BOMB_BAY.get());
            event.accept(ModItems.LIGHT_MG_AMMO.get());
            event.accept(ModItems.HE_CANNON_AMMO.get());
            event.accept(ModItems.BOMB_200LB.get());
            event.accept(ModItems.HOMING_ROCKET_LAUNCHER.get());
            event.accept(ModItems.HOMING_ROCKET_AMMO.get());
            event.accept(ModItems.CLUSTER_DISPENSER.get());
            event.accept(ModItems.CLUSTER_BOMB.get());
            event.accept(ModItems.ROTARY_CANNON.get());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModWeapons.register();
            LOGGER.info("AggressiveAircraft weapons registered");
        });
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.EXPLOSIVE_BULLET.get(), ExplosiveBulletRenderer::new);
            event.registerEntityRenderer(ModEntities.HEAVY_BOMB.get(), HeavyBombRenderer::new);
            event.registerEntityRenderer(ModEntities.HOMING_ROCKET.get(), HomingRocketRenderer::new);
            event.registerEntityRenderer(ModEntities.CLUSTER_DISPENSER.get(), ClusterDispenserRenderer::new);

            // Register custom animation variables
            BBAnimationVariables.register("weapon_ready");
            BBAnimationVariables.register("rotary_progress");

            // Register weapon renderers
            WeaponRendererRegistry.register(
                    ResourceLocation.tryBuild(MODID, "machine_gun"),
                    new SimpleWeaponRenderer(ResourceLocation.tryBuild(MODID, "machine_gun"))
            );
            WeaponRendererRegistry.register(
                    ResourceLocation.tryBuild(MODID, "heavy_cannon"),
                    new SimpleWeaponRenderer(ResourceLocation.tryBuild(MODID, "heavy_cannon"))
            );
            WeaponRendererRegistry.register(
                    ResourceLocation.tryBuild(MODID, "heavy_bomb_bay"),
                    new SimpleWeaponRenderer(ResourceLocation.tryBuild(MODID, "heavy_bomb_bay"))
            );
            WeaponRendererRegistry.register(
                    ResourceLocation.tryBuild(MODID, "homing_rocket_launcher"),
                    new SimpleWeaponRenderer(ResourceLocation.tryBuild(MODID, "homing_rocket_launcher"))
            );
            WeaponRendererRegistry.register(
                    ResourceLocation.tryBuild(MODID, "cluster_dispenser"),
                    new SimpleWeaponRenderer(ResourceLocation.tryBuild(MODID, "cluster_dispenser"))
            );
            WeaponRendererRegistry.register(
                    ResourceLocation.tryBuild(MODID, "rotary_cannon"),
                    new SimpleWeaponRenderer(ResourceLocation.tryBuild(MODID, "rotary_cannon"))
            );
        }
    }
}
