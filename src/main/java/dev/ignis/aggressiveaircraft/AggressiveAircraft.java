package dev.ignis.aggressiveaircraft;

import com.mojang.logging.LogUtils;
import dev.ignis.aggressiveaircraft.client.render.ExplosiveBulletRenderer;
import dev.ignis.aggressiveaircraft.client.render.HeavyBombRenderer;
import dev.ignis.aggressiveaircraft.entities.ModEntities;
import dev.ignis.aggressiveaircraft.items.ModItems;
import dev.ignis.aggressiveaircraft.weapons.ModWeapons;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import static dev.ignis.aggressiveaircraft.ModConfig.SPEC;

@Mod(AggressiveAircraft.MODID)
public class AggressiveAircraft {

    public static final String MODID = "aggressiveaircraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal")
    public AggressiveAircraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
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
        }
    }
}
