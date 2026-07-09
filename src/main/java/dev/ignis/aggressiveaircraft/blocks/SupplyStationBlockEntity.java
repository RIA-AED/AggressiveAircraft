package dev.ignis.aggressiveaircraft.blocks;

import dev.ignis.aggressiveaircraft.ModConfig;
import dev.ignis.aggressiveaircraft.mixin.BulletWeaponAccessor;
import immersive_aircraft.entity.InventoryVehicleEntity;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.entity.inventory.VehicleInventoryDescription;
import immersive_aircraft.entity.inventory.slots.SlotDescription;
import immersive_aircraft.entity.weapon.BulletWeapon;
import immersive_aircraft.entity.weapon.Weapon;
import immersive_aircraft.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SupplyStationBlockEntity extends BlockEntity {
    private int tickOffset = -1; // -1 means uninitialized
    private static final int CHECK_INTERVAL = 5;

    // Raycast detection: 5m forward
    private static final double DETECT_RANGE = 5.0;

    public SupplyStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SUPPLY_STATION_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SupplyStationBlockEntity be) {
        if (level.isClientSide) return;

        // Initialize tick offset randomly (0-4) on first tick, so not all blocks check simultaneously
        if (be.tickOffset < 0) {
            be.tickOffset = level.random.nextInt(CHECK_INTERVAL);
        }

        // Staggered check: only run every CHECK_INTERVAL ticks using per-block offset
        if ((level.getGameTime() + be.tickOffset) % CHECK_INTERVAL != 0) return;

        SupplyStationBlock block = (SupplyStationBlock) state.getBlock();
        SupplyStationBlock.StationType stationType = block.getStationType();
        Direction facing = state.getValue(SupplyStationBlock.FACING);

        // Raycast from block front face to find aircraft within 5m
        List<VehicleEntity> aircraft = raycastAircraft(level, pos, facing);

        for (VehicleEntity vehicle : aircraft) {
            // Get container directly below the block
            BlockPos belowPos = pos.below();
            IItemHandler container = getContainerBelow(level, belowPos);

            switch (stationType) {
                case AMMO -> handleAmmoReplenish(vehicle, container);
                case FUEL -> handleFuelReplenish(vehicle, container);
                case REPAIR -> handleRepair(vehicle);
            }
        }
    }

    /**
     * Cast a ray from the block's front face 5m forward.
     * Uses the minimal AABB formed by the ray segment for entity lookup,
     * then precise AABB.clip() intersection test against each candidate.
     */
    private static List<VehicleEntity> raycastAircraft(Level level, BlockPos pos, Direction facing) {
        Vec3 start = new Vec3(
                pos.getX() + 0.5 + facing.getStepX() * 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5 + facing.getStepZ() * 0.5
        );
        Vec3 end = start.add(
                facing.getStepX() * DETECT_RANGE,
                0,
                facing.getStepZ() * DETECT_RANGE
        );

        // The ray itself forms the search box — minimal, no inflate
        AABB searchBox = new AABB(start, end);

        List<VehicleEntity> result = new ArrayList<>();
        for (VehicleEntity vehicle : level.getEntitiesOfClass(VehicleEntity.class, searchBox, e -> e.isAlive())) {
            if (vehicle.getBoundingBox().clip(start, end).isPresent()) {
                result.add(vehicle);
            }
        }
        return result;
    }

    /**
     * Get the item handler capability from the block entity below.
     */
    @Nullable
    private static IItemHandler getContainerBelow(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            return be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        }
        return null;
    }

    // ==================== Ammo Replenish ====================

    /**
     * Replenish aircraft weapons with ammo from the container below.
     * Uses round-robin: tries to keep ammo group counts balanced across different weapon types.
     */
    private static void handleAmmoReplenish(VehicleEntity vehicle, @Nullable IItemHandler container) {
        if (!(vehicle instanceof InventoryVehicleEntity inventoryVehicle)) return;
        if (container == null) return;

        Map<Integer, List<Weapon>> allWeapons = inventoryVehicle.getWeapons();
        if (allWeapons.isEmpty()) return;

        // Collect all BulletWeapon instances with their ammo type info
        record WeaponAmmoInfo(Weapon weapon, String ammoId, int consumption) {}
        List<WeaponAmmoInfo> weaponInfos = new ArrayList<>();

        for (List<Weapon> weaponList : allWeapons.values()) {
            for (Weapon weapon : weaponList) {
                if (weapon instanceof BulletWeapon) {
                    String weaponItemId = BuiltInRegistries.ITEM.getKey(weapon.getStack().getItem()).toString();
                    String ammoId = getAmmoForWeapon(weaponItemId);
                    int consumption = getAmmoConsumptionForWeapon(weaponItemId);
                    if (ammoId != null) {
                        weaponInfos.add(new WeaponAmmoInfo(weapon, ammoId, consumption));
                    }
                }
            }
        }

        if (weaponInfos.isEmpty()) return;

        // Round-robin replenish loop
        int maxIterations = 256; // safety limit
        for (int iter = 0; iter < maxIterations; iter++) {
            // Calculate groups (ammo / consumption) for each weapon
            // Find the weapon with minimum groups
            WeaponAmmoInfo targetWeapon = null;
            double minGroups = Double.MAX_VALUE;

            for (WeaponAmmoInfo info : weaponInfos) {
                BulletWeaponAccessor accessor = (BulletWeaponAccessor) info.weapon;
                int ammo = accessor.getAmmo();
                double groups = info.consumption > 0 ? (double) ammo / info.consumption : Double.MAX_VALUE;
                if (groups < minGroups) {
                    minGroups = groups;
                    targetWeapon = info;
                }
            }

            if (targetWeapon == null) break;

            // Try to extract one ammo item from container — only if vehicle has cargo space
            boolean replenished = false;
            for (int slot = 0; slot < container.getSlots(); slot++) {
                ItemStack stack = container.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                String stackId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (stackId.equals(targetWeapon.ammoId)) {
                    // Extract one item
                    ItemStack extracted = container.extractItem(slot, 1, false);
                    if (!extracted.isEmpty()) {
                        // Only accept into cargo slots — never weapon/upgrade/fuel slots
                        if (addToVehicleInventory(inventoryVehicle, extracted)) {
                            BulletWeaponAccessor accessor = (BulletWeaponAccessor) targetWeapon.weapon;
                            accessor.setAmmo(accessor.getAmmo() + 1);
                            replenished = true;
                        } else {
                            // Cargo full — return item to container
                            container.insertItem(slot, extracted, false);
                            replenished = false;
                        }
                    }
                    break;
                }
            }

            if (!replenished) break; // cargo full or no more matching ammo
        }
    }

    /**
     * Try to insert an item stack into the vehicle's cargo slots only.
     * Picks the slot with the lowest matching item count to balance
     * distribution across left/right inventory panels.
     * @return true if the item was placed, false if all cargo slots are full
     */
    private static boolean addToVehicleInventory(InventoryVehicleEntity vehicle, ItemStack stack) {
        VehicleInventoryDescription desc = vehicle.getInventoryDescription();
        List<SlotDescription> cargoSlots = desc.getSlots(VehicleInventoryDescription.INVENTORY);

        // Find the cargo slot with the lowest existing count for this item type
        int bestSlot = -1;
        int lowestCount = Integer.MAX_VALUE;

        for (SlotDescription slotDesc : cargoSlots) {
            ItemStack existing = vehicle.getInventory().getItem(slotDesc.index());
            if (existing.isEmpty()) {
                // Empty slot is the best choice — use it immediately
                bestSlot = slotDesc.index();
                break;
            }
            if (ItemStack.isSameItem(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                if (existing.getCount() < lowestCount) {
                    lowestCount = existing.getCount();
                    bestSlot = slotDesc.index();
                }
            }
        }

        if (bestSlot >= 0) {
            ItemStack existing = vehicle.getInventory().getItem(bestSlot);
            if (existing.isEmpty()) {
                vehicle.getInventory().setItem(bestSlot, stack.copy());
            } else {
                int canAdd = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(canAdd);
                stack.shrink(canAdd);
            }
            return true;
        }

        return false; // All cargo slots are full — stop replenishing
    }

    // ==================== Fuel Replenish ====================

    /**
     * Replenish aircraft fuel by filling the vehicle's boiler slot(s)
     * with fuel items from the container below.
     * <p>
     * Logic per boiler slot:
     * <ul>
     * <li>If empty — take any fuel item from the container and fill the slot.</li>
     * <li>If has fuel but not full — merge same fuel type from the container.</li>
     * <li>If full — skip.</li>
     * </ul>
     * The aircraft's own refuel() tick will convert boiler items to fuel time.
     */
    private static void handleFuelReplenish(VehicleEntity vehicle, @Nullable IItemHandler container) {
        if (!(vehicle instanceof InventoryVehicleEntity inventoryVehicle)) return;
        if (container == null) return;

        VehicleInventoryDescription desc = inventoryVehicle.getInventoryDescription();
        List<SlotDescription> boilerSlots = desc.getSlots(VehicleInventoryDescription.BOILER);
        if (boilerSlots.isEmpty()) return;

        for (SlotDescription boilerSlot : boilerSlots) {
            ItemStack existing = inventoryVehicle.getInventory().getItem(boilerSlot.index());

            if (existing.isEmpty()) {
                // Empty boiler slot — fill with any fuel from container
                for (int slot = 0; slot < container.getSlots(); slot++) {
                    ItemStack containerStack = container.getStackInSlot(slot);
                    if (!containerStack.isEmpty() && Utils.getFuelTime(containerStack) > 0) {
                        int toTake = Math.min(containerStack.getCount(), containerStack.getMaxStackSize());
                        ItemStack extracted = container.extractItem(slot, toTake, false);
                        if (!extracted.isEmpty()) {
                            inventoryVehicle.getInventory().setItem(boilerSlot.index(), extracted.copy());
                        }
                        break; // Filled this slot, move to next
                    }
                }
            } else if (existing.getCount() < existing.getMaxStackSize() && Utils.getFuelTime(existing) > 0) {
                // Boiler slot has fuel but not full — merge same type from container
                int space = existing.getMaxStackSize() - existing.getCount();
                for (int slot = 0; slot < container.getSlots(); slot++) {
                    ItemStack containerStack = container.getStackInSlot(slot);
                    if (!containerStack.isEmpty() && ItemStack.isSameItem(containerStack, existing)) {
                        int toTake = Math.min(containerStack.getCount(), space);
                        ItemStack extracted = container.extractItem(slot, toTake, false);
                        if (!extracted.isEmpty()) {
                            existing.grow(extracted.getCount());
                            space -= extracted.getCount();
                            if (space <= 0) break;
                        }
                    }
                }
            }
            // If boiler slot is full or contains non-fuel, skip
        }
    }

    // ==================== Repair ====================

    /**
     * Repair the aircraft to full health.
     */
    private static void handleRepair(VehicleEntity vehicle) {
        if (!vehicle.isAlive()) return;
        vehicle.setHealth(1.0f); // 100% health
    }

    // ==================== Weapon -> Ammo Mapping ====================

    @Nullable
    private static String getAmmoForWeapon(String weaponItemId) {
        return switch (weaponItemId) {
            case "aggressiveaircraft:machine_gun" -> ModConfig.MACHINE_GUN_AMMO.get();
            case "aggressiveaircraft:heavy_cannon" -> ModConfig.HEAVY_CANNON_AMMO.get();
            case "aggressiveaircraft:heavy_bomb_bay" -> ModConfig.HEAVY_BOMB_BAY_AMMO.get();
            case "aggressiveaircraft:homing_rocket_launcher" -> ModConfig.HOMING_ROCKET_AMMO.get();
            case "aggressiveaircraft:cluster_dispenser" -> ModConfig.CLUSTER_DISPENSER_AMMO.get();
            case "aggressiveaircraft:rotary_cannon" -> ModConfig.ROTARY_CANNON_AMMO.get();
            case "aggressiveaircraft:rocket_pod" -> ModConfig.ROCKET_POD_AMMO.get();
            case "aggressiveaircraft:napalm_bomb_bay" -> ModConfig.NAPALM_BOMB_AMMO.get();
            default -> null;
        };
    }

    private static int getAmmoConsumptionForWeapon(String weaponItemId) {
        return switch (weaponItemId) {
            case "aggressiveaircraft:machine_gun" -> ModConfig.MACHINE_GUN_AMMO_CONSUMPTION.get();
            case "aggressiveaircraft:heavy_cannon" -> ModConfig.HEAVY_CANNON_AMMO_CONSUMPTION.get();
            case "aggressiveaircraft:heavy_bomb_bay" -> ModConfig.HEAVY_BOMB_BAY_AMMO_CONSUMPTION.get();
            case "aggressiveaircraft:homing_rocket_launcher" -> ModConfig.HOMING_ROCKET_AMMO_CONSUMPTION.get();
            case "aggressiveaircraft:cluster_dispenser" -> ModConfig.CLUSTER_DISPENSER_AMMO_CONSUMPTION.get();
            case "aggressiveaircraft:rotary_cannon" -> ModConfig.ROTARY_CANNON_AMMO_CONSUMPTION.get();
            case "aggressiveaircraft:rocket_pod" -> ModConfig.ROCKET_POD_AMMO_CONSUMPTION.get();
            case "aggressiveaircraft:napalm_bomb_bay" -> ModConfig.NAPALM_BOMB_AMMO_CONSUMPTION.get();
            default -> 1;
        };
    }
}
