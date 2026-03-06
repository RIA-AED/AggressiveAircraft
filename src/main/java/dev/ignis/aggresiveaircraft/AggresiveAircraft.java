package dev.ignis.aggresiveaircraft;

import com.mojang.logging.LogUtils;
import dev.ignis.aggresiveaircraft.client.render.ExplosiveBulletRenderer;
import dev.ignis.aggresiveaircraft.client.render.HeavyBombRenderer;
import dev.ignis.aggresiveaircraft.entities.ModEntities;
import dev.ignis.aggresiveaircraft.items.ModItems;
import dev.ignis.aggresiveaircraft.weapons.ModWeapons;
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

import static dev.ignis.aggresiveaircraft.ModConfig.SPEC;

@Mod(AggresiveAircraft.MODID)
public class AggresiveAircraft {

    public static final String MODID = "aggresiveaircraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AggresiveAircraft() {
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
            LOGGER.info("AggresiveAircraft weapons registered");
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
