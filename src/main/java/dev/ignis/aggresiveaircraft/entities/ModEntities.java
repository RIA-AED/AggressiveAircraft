package dev.ignis.aggresiveaircraft.entities;

import dev.ignis.aggresiveaircraft.AggresiveAircraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AggresiveAircraft.MODID);

    public static final RegistryObject<EntityType<ExplosiveBulletEntity>> EXPLOSIVE_BULLET = ENTITIES.register("explosive_bullet",
            () -> EntityType.Builder.<ExplosiveBulletEntity>of(ExplosiveBulletEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .fireImmune()
                    .build(ResourceLocation.tryBuild(AggresiveAircraft.MODID, "explosive_bullet").toString()));

    public static final RegistryObject<EntityType<HeavyBombEntity>> HEAVY_BOMB = ENTITIES.register("heavy_bomb",
            () -> EntityType.Builder.<HeavyBombEntity>of(HeavyBombEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .fireImmune()
                    .build(ResourceLocation.tryBuild(AggresiveAircraft.MODID, "heavy_bomb").toString()));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
