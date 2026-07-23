package dev.ignis.aggressiveaircraft.blocks;

import dev.ignis.aggressiveaircraft.AggressiveAircraft;
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
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

import java.util.*;

public class SupplyStationBlockEntity extends BlockEntity {
    private int tickOffset = -1;
    private long lastSupplyTime = 0;
    private static final int CHECK_INTERVAL = 5;
    private static final int COOLDOWN_TICKS = 1200; // 60 seconds

    // VS ship cache: ship lookups are expensive, cache for a few ticks
    private Ship cachedShip = null;
    private int shipCacheTick = 0;
    private static final int SHIP_CACHE_INTERVAL = 20; // 1 second

    private boolean isInCooldown(Level level) {
        return lastSupplyTime > 0 && (level.getGameTime() - lastSupplyTime) < COOLDOWN_TICKS;
    }

    public int getCooldownSecondsRemaining(Level level) {
        if (lastSupplyTime <= 0) return 0;
        long elapsed = level.getGameTime() - lastSupplyTime;
        long remaining = COOLDOWN_TICKS - elapsed;
        return remaining > 0 ? (int) ((remaining + 19) / 20) : 0; // ceil division
    }


    private SupplyStationEnergyStorage energyStorage;
    private LazyOptional<IEnergyStorage> energyLazyOptional;

    public SupplyStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SUPPLY_STATION_BLOCK_ENTITY.get(), pos, state);
    }

    private boolean isAmmoStation() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof SupplyStationBlock block
                && block.getStationType() == SupplyStationBlock.StationType.AMMO;
    }

    private SupplyStationEnergyStorage getOrCreateEnergy() {
        if (energyStorage == null) {
            int rfPerStack = ModConfig.SUPPLY_STATION_RF_PER_STACK.get();
            int capacity = Math.max(1, rfPerStack * 50);
            int maxReceive = ModConfig.SUPPLY_STATION_MAX_RECEIVE.get();
            energyStorage = new SupplyStationEnergyStorage(capacity, maxReceive, 0, 0);
            energyStorage.setOnChanged(this::setChanged);
            energyLazyOptional = LazyOptional.of(() -> energyStorage);
        }
        return energyStorage;
    }

    public String getEnergyStatus() {
        if (!isAmmoStation()) return "";
        SupplyStationEnergyStorage storage = getOrCreateEnergy();
        return "§b能量: " + storage.getEnergyStored()
                + "/" + storage.getMaxEnergyStored() + " RF";
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("LastSupplyTime", lastSupplyTime);
        if (energyStorage != null) {
            tag.putInt("Energy", energyStorage.getEnergyStored());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        lastSupplyTime = tag.getLong("LastSupplyTime");
        if (tag.contains("Energy")) {
            getOrCreateEnergy().setEnergy(tag.getInt("Energy"));
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY && isAmmoStation()
                && (side == null || side.getAxis() != Direction.Axis.Y)) {
            getOrCreateEnergy();
            return energyLazyOptional.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        if (energyLazyOptional != null) energyLazyOptional.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        if (energyStorage != null) {
            energyLazyOptional = LazyOptional.of(() -> energyStorage);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SupplyStationBlockEntity be) {
        // Initialize tick offset randomly (0-4) on first tick (both sides)
        if (be.tickOffset < 0) {
            be.tickOffset = level.random.nextInt(CHECK_INTERVAL);
        }

        if (level.isClientSide) {
            clientTick(level, pos, state, be);
            return;
        }

        SupplyStationBlock block = (SupplyStationBlock) state.getBlock();
        SupplyStationBlock.StationType stationType = block.getStationType();

        // Smoke particles during cooldown — every tick, independent of staggered check
        if (stationType != SupplyStationBlock.StationType.REPAIR && be.isInCooldown(level)) {
            spawnSmokeParticles(level, pos);
        }

        // Staggered check: only run every CHECK_INTERVAL ticks using per-block offset
        if ((level.getGameTime() + be.tickOffset) % CHECK_INTERVAL != 0) return;

        // Skip supply if in cooldown for ammo/fuel (smoke already handled above)
        if (stationType != SupplyStationBlock.StationType.REPAIR && be.isInCooldown(level)) {
            return;
        }

        // Detect aircraft whose AABB overlaps with this block's 1x1x1 volume
        List<VehicleEntity> aircraft = be.detectAircraft(level, pos);

        for (VehicleEntity vehicle : aircraft) {
            BlockPos belowPos = pos.below();
            IItemHandler container = getContainerBelow(level, belowPos);

            boolean supplied = switch (stationType) {
                case AMMO -> handleAmmoReplenish(vehicle, container, be.getOrCreateEnergy());
                case FUEL -> handleFuelReplenish(vehicle, container);
                case REPAIR -> handleRepair(vehicle);
            };

            if (supplied) {
                spawnGreenParticles(level, pos);
                if (stationType != SupplyStationBlock.StationType.REPAIR) {
                    be.lastSupplyTime = level.getGameTime();
                }
            }
        }
    }

    private static final int CLIENT_PARTICLE_INTERVAL = 4; // spawn particles every 4 ticks

    /**
     * Client-side tick: spawn range indicator particles when a player looks at this block.
     */
    private static void clientTick(Level level, BlockPos pos, BlockState state, SupplyStationBlockEntity be) {
        // Rate-limit particle spawning using tick offset
        if ((level.getGameTime() + be.tickOffset) % CLIENT_PARTICLE_INTERVAL != 0) return;
        spawnRangeIndicatorParticles(level, pos, state);
    }

    /**
     * Spawn redstone dust particles along a beam extending forward from the block
     * in its facing direction, but only when the local player is looking at this block.
     */
    private static void spawnRangeIndicatorParticles(Level level, BlockPos pos, BlockState state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.hitResult == null) return;
        if (!(minecraft.hitResult instanceof BlockHitResult blockHit)) return;
        if (!blockHit.getBlockPos().equals(pos)) return;

        Direction facing = state.getValue(SupplyStationBlock.FACING);

        // Beam origin: center of the block's front face
        double originX = pos.getX() + 0.5 + facing.getStepX() * 0.55;
        double originY = pos.getY() + 0.15; // slightly above the slab surface
        double originZ = pos.getZ() + 0.5 + facing.getStepZ() * 0.55;

        // Beam extends 1.0 blocks forward (matching the detection AABB extent)
        double beamLength = 1.0;

        // Redstone dust color: bright red
        Vector3f redColor = new Vector3f(1.0f, 0.15f, 0.0f);

        for (int i = 0; i < 5; i++) {
            double t = i / 4.0; // 0.0 to 1.0 along the beam
            double x = originX + facing.getStepX() * t * beamLength;
            double y = originY + level.random.nextDouble() * 0.08;
            double z = originZ + facing.getStepZ() * t * beamLength;

            // Minimal perpendicular spread for a thinner beam
            x += (level.random.nextDouble() - 0.5) * 0.03;
            z += (level.random.nextDouble() - 0.5) * 0.03;

            level.addParticle(new DustParticleOptions(redColor, 0.8f),
                    x, y, z, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Detect aircraft whose collision box (AABB) overlaps with this block's 1x1x1 volume.
     * <p>
     * When the block is on a Valkyrien Skies ship, the block coordinates are
     * in ship-local space. We transform them to world space so that
     * {@code level.getEntitiesOfClass} (which searches world space) works correctly.
     */
    private List<VehicleEntity> detectAircraft(Level level, BlockPos pos) {
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = pos.getX() + 1;
        double maxY = pos.getY() + 1;
        double maxZ = pos.getZ() + 1;

        AABB detectionBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        // VS ship: transform block corners from ship-local to world space
        if (AggressiveAircraft.VALKYRIENSKIES_LOADED) {
            Ship ship = getShipManagingPos(level, pos);
            if (ship != null) {
                Vector3d wMin = new Vector3d();
                Vector3d wMax = new Vector3d();
                ship.getShipToWorld().transformPosition(minX, minY, minZ, wMin);
                ship.getShipToWorld().transformPosition(maxX, maxY, maxZ, wMax);
                detectionBox = new AABB(
                        Math.min(wMin.x, wMax.x), Math.min(wMin.y, wMax.y), Math.min(wMin.z, wMax.z),
                        Math.max(wMin.x, wMax.x), Math.max(wMin.y, wMax.y), Math.max(wMin.z, wMax.z)
                );
            }
        }

        return new ArrayList<>(level.getEntitiesOfClass(VehicleEntity.class, detectionBox, e -> e.isAlive()));
    }

    /**
     * Get the VS ship managing this block position, with caching.
     * Returns null if VS is not loaded or no ship manages this block.
     */
    private Ship getShipManagingPos(Level level, BlockPos pos) {
        int currentTick = (int) level.getGameTime();
        if (currentTick - shipCacheTick >= SHIP_CACHE_INTERVAL) {
            shipCacheTick = currentTick;
            cachedShip = ValkyrienSkies.getShipManagingBlock(level, pos);
        }
        return cachedShip;
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
     * Round-robin balances the number of occupied cargo slots per ammo type:
     * always picks the ammo type with the fewest occupied slots to supply next.
     * If an ammo type runs out in the container, it is skipped and others continue.
     */
    private static boolean handleAmmoReplenish(VehicleEntity vehicle, @Nullable IItemHandler container, @Nullable SupplyStationEnergyStorage energy) {
        if (!(vehicle instanceof InventoryVehicleEntity inventoryVehicle)) return false;
        if (container == null) return false;

        int rfPerStack = ModConfig.SUPPLY_STATION_RF_PER_STACK.get();

        Map<Integer, List<Weapon>> allWeapons = inventoryVehicle.getWeapons();
        if (allWeapons.isEmpty()) return false;

        // Collect weapons grouped by ammoId
        record WeaponInfo(Weapon weapon, String ammoId) {}
        Map<String, List<WeaponInfo>> ammoWeapons = new LinkedHashMap<>();
        Set<String> allAmmoIds = new HashSet<>();

        for (List<Weapon> weaponList : allWeapons.values()) {
            for (Weapon weapon : weaponList) {
                if (weapon instanceof BulletWeapon) {
                    String weaponItemId = BuiltInRegistries.ITEM.getKey(weapon.getStack().getItem()).toString();
                    String ammoId = getAmmoForWeapon(weaponItemId);
                    if (ammoId != null) {
                        ammoWeapons.computeIfAbsent(ammoId, k -> new ArrayList<>())
                                .add(new WeaponInfo(weapon, ammoId));
                        allAmmoIds.add(ammoId);
                    }
                }
            }
        }

        if (ammoWeapons.isEmpty()) return false;

        Set<String> depleted = new HashSet<>(); // ammoIds no longer available in container
        boolean anyReplenished = false;
        int maxIterations = 256;

        for (int iter = 0; iter < maxIterations; iter++) {
            // Count how many cargo slots each ammoId occupies right now
            Map<String, Integer> slotCounts = countCargoSlotsPerAmmo(inventoryVehicle, allAmmoIds);

            // Pick the ammoId with fewest occupied slots (skip depleted)
            String target = null;
            int minSlots = Integer.MAX_VALUE;
            for (String ammoId : allAmmoIds) {
                if (depleted.contains(ammoId)) continue;
                int slots = slotCounts.getOrDefault(ammoId, 0);
                if (slots < minSlots) {
                    minSlots = slots;
                    target = ammoId;
                }
            }

            if (target == null) break; // all ammo types depleted

            // Find and extract one item of the target ammo from container
            boolean replenished = false;
            for (int slot = 0; slot < container.getSlots(); slot++) {
                ItemStack stack = container.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                String stackId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (stackId.equals(target)) {
                    int maxStack = stack.getMaxStackSize();
                    int perItemCost = rfPerStack > 0 ? (rfPerStack + maxStack - 1) / maxStack : 0;
                    if (energy != null && perItemCost > 0 && energy.getEnergyStored() < perItemCost) {
                        return anyReplenished;
                    }
                    ItemStack extracted = container.extractItem(slot, 1, false);
                    if (!extracted.isEmpty()) {
                        if (addToVehicleInventory(inventoryVehicle, extracted)) {
                            // Increment ammo on the weapon with least ammo for this type
                            WeaponInfo leastWeapon = null;
                            int leastAmmo = Integer.MAX_VALUE;
                            for (WeaponInfo info : ammoWeapons.get(target)) {
                                int ammo = ((BulletWeaponAccessor) info.weapon).getAmmo();
                                if (ammo < leastAmmo) {
                                    leastAmmo = ammo;
                                    leastWeapon = info;
                                }
                            }
                            if (leastWeapon != null) {
                                ((BulletWeaponAccessor) leastWeapon.weapon).setAmmo(leastAmmo + 1);
                            }
                            if (energy != null && perItemCost > 0) {
                                energy.consumeEnergy(perItemCost);
                            }
                            replenished = true;
                            anyReplenished = true;
                        } else {
                            // Cargo completely full — stop
                            container.insertItem(slot, extracted, false);
                            return anyReplenished;
                        }
                    }
                    break;
                }
            }

            if (!replenished) {
                depleted.add(target); // this ammo type exhausted in container
            }
        }
        return anyReplenished;
    }

    /**
     * Count how many cargo slots each ammoId occupies in the vehicle.
     */
    private static Map<String, Integer> countCargoSlotsPerAmmo(InventoryVehicleEntity vehicle, Set<String> ammoIds) {
        Map<String, Integer> counts = new HashMap<>();
        for (String id : ammoIds) {
            counts.put(id, 0);
        }
        VehicleInventoryDescription desc = vehicle.getInventoryDescription();
        for (SlotDescription slotDesc : desc.getSlots(VehicleInventoryDescription.INVENTORY)) {
            ItemStack stack = vehicle.getInventory().getItem(slotDesc.index());
            if (!stack.isEmpty()) {
                String stackId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (ammoIds.contains(stackId)) {
                    counts.merge(stackId, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    /**
     * Try to insert a single item into the vehicle's cargo slots only.
     * Prefers merging into an existing same-item stack (fill to maxStackSize),
     * then falls back to an empty slot.
     * @return true if placed, false if all cargo slots are full
     */
    private static boolean addToVehicleInventory(InventoryVehicleEntity vehicle, ItemStack stack) {
        VehicleInventoryDescription desc = vehicle.getInventoryDescription();
        List<SlotDescription> cargoSlots = desc.getSlots(VehicleInventoryDescription.INVENTORY);

        // First pass: merge into an existing same-item slot that isn't full
        for (SlotDescription slotDesc : cargoSlots) {
            ItemStack existing = vehicle.getInventory().getItem(slotDesc.index());
            if (ItemStack.isSameItem(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                existing.grow(1);
                return true;
            }
        }

        // Second pass: use any empty slot
        for (SlotDescription slotDesc : cargoSlots) {
            if (vehicle.getInventory().getItem(slotDesc.index()).isEmpty()) {
                vehicle.getInventory().setItem(slotDesc.index(), stack.copy());
                return true;
            }
        }

        return false;
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
    private static boolean handleFuelReplenish(VehicleEntity vehicle, @Nullable IItemHandler container) {
        if (!(vehicle instanceof InventoryVehicleEntity inventoryVehicle)) return false;
        if (container == null) return false;

        VehicleInventoryDescription desc = inventoryVehicle.getInventoryDescription();
        List<SlotDescription> boilerSlots = desc.getSlots(VehicleInventoryDescription.BOILER);
        if (boilerSlots.isEmpty()) return false;

        boolean fuelSupplied = false;

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
                            fuelSupplied = true;
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
                            fuelSupplied = true;
                            if (space <= 0) break;
                        }
                    }
                }
            }
            // If boiler slot is full or contains non-fuel, skip
        }
        return fuelSupplied;
    }

    // ==================== Repair ====================

    /**
     * Repair the aircraft to full health.
     */
    private static boolean handleRepair(VehicleEntity vehicle) {
        if (!vehicle.isAlive()) return false;
        if (vehicle.getHealth() >= 1.0f) return false; // already full health
        vehicle.setHealth(1.0f); // 100% health
        return true;
    }

    // ==================== Particles ====================

    private static void spawnSmokeParticles(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 0.15 + level.random.nextDouble() * 0.2;
            double z = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 1, 0.0, 0.02, 0.0, 0.01);
        }
    }

    private static void spawnGreenParticles(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (int i = 0; i < 5; i++) {
            double x = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.8;
            double y = pos.getY() + 0.3 + level.random.nextDouble() * 0.6;
            double z = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.8;
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0.0, 0.0, 0.0, 0.2);
        }
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
