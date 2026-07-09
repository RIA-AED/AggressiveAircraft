package dev.ignis.aggressiveaircraft.blocks;

import dev.ignis.aggressiveaircraft.AggressiveAircraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, AggressiveAircraft.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AggressiveAircraft.MODID);

    // --- Block instances ---

    public static final RegistryObject<Block> AMMO_SUPPLY_STATION = BLOCKS.register("ammo_supply_station",
            () -> new SupplyStationBlock(
                    BlockBehaviour.Properties.of()
                            .strength(3.5f, 6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops(),
                    SupplyStationBlock.StationType.AMMO
            ));

    public static final RegistryObject<Block> FUEL_SUPPLY_STATION = BLOCKS.register("fuel_supply_station",
            () -> new SupplyStationBlock(
                    BlockBehaviour.Properties.of()
                            .strength(3.5f, 6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops(),
                    SupplyStationBlock.StationType.FUEL
            ));

    public static final RegistryObject<Block> REPAIR_STATION = BLOCKS.register("repair_station",
            () -> new SupplyStationBlock(
                    BlockBehaviour.Properties.of()
                            .strength(3.5f, 6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops(),
                    SupplyStationBlock.StationType.REPAIR
            ));

    // --- Block Entity Type ---

    public static final RegistryObject<BlockEntityType<SupplyStationBlockEntity>> SUPPLY_STATION_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("supply_station",
                    () -> BlockEntityType.Builder.of(
                            SupplyStationBlockEntity::new,
                            AMMO_SUPPLY_STATION.get(),
                            FUEL_SUPPLY_STATION.get(),
                            REPAIR_STATION.get()
                    ).build(null));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
    }
}
