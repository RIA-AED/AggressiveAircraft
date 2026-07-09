package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.ModConfig;
import dev.ignis.aggressiveaircraft.items.ModItems;
import dev.ignis.aggressiveaircraft.network.s2c.AmmoSyncMessage;
import immersive_aircraft.cobalt.network.NetworkHandler;
import immersive_aircraft.entity.InventoryVehicleEntity;
import immersive_aircraft.entity.inventory.SparseSimpleInventory;
import immersive_aircraft.entity.weapon.BulletWeapon;
import immersive_aircraft.entity.weapon.Weapon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server-side: periodically computes remaining ammo for each weapon slot and
 * sends a lightweight {@link AmmoSyncMessage} to riding players only.
 * <p>
 * This replaces the old per-slot full inventory sync approach, which had two problems:
 * <ol>
 *   <li>Syncing weapon slots caused client-side weapon object recreation
 *       (due to ItemStack reference comparison), resetting {@code RotationalManager}
 *       and causing turret visual flash.</li>
 *   <li>Syncing every inventory slot (hundreds of bytes per slot as NBT) consumed
 *       excessive bandwidth.</li>
 * </ol>
 * <p>
 * The new approach: server computes {@code remainingShots = (weapon.ammo + inventoryCount) / consumption}
 * and sends only that integer per weapon slot. ~5 bytes per weapon vs ~100-500 bytes
 * per inventory slot with the old method.
 */
@Mixin(SparseSimpleInventory.class)
public abstract class SparseSimpleInventoryMixin extends SimpleContainer {

    public SparseSimpleInventoryMixin(int size) {
        super(size);
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void syncAmmoToClients(InventoryVehicleEntity entity, CallbackInfo ci) {
        // Server side only, every 10 ticks (2 Hz) for responsive HUD
        if (entity.level().isClientSide || entity.tickCount % 10 != 0) {
            return;
        }

        Map<Integer, List<Weapon>> weaponsMap = entity.getWeapons();
        if (weaponsMap.isEmpty()) {
            return;
        }

        List<Integer> slotList = new ArrayList<>();
        List<Integer> ammoList = new ArrayList<>();

        for (Map.Entry<Integer, List<Weapon>> entry : weaponsMap.entrySet()) {
            int slot = entry.getKey();
            List<Weapon> weaponList = entry.getValue();
            if (weaponList.isEmpty()) continue;

            Weapon weapon = weaponList.get(0);
            ItemStack weaponStack = weapon.getStack();
            if (weaponStack.isEmpty()) continue;

            Item weaponItem = weaponStack.getItem();
            AmmoConfig ammoConfig = getAmmoConfig(weaponItem);
            if (ammoConfig == null) continue;

            String ammoId = ammoConfig.ammoId().get();
            int consumption = ammoConfig.consumption().get();
            if (consumption <= 0) continue;

            // Count matching ammo items in inventory
            int inventoryCount = 0;
            for (int i = 0; i < this.getContainerSize(); i++) {
                ItemStack invStack = this.getItem(i);
                if (!invStack.isEmpty()) {
                    String itemId = BuiltInRegistries.ITEM.getKey(invStack.getItem()).toString();
                    if (itemId.equals(ammoId)) {
                        inventoryCount += invStack.getCount();
                    }
                }
            }

            // Get weapon's internal ammo buffer (already loaded from inventory)
            int internalAmmo = 0;
            if (weapon instanceof BulletWeapon) {
                internalAmmo = ((BulletWeaponAccessor) (Object) weapon).getAmmo();
            }

            // Total remaining shots = (internal ammo + inventory ammo) / consumption
            // For our weapons, ammoPerItem=1, so totalAmmoUnits = internalAmmo + inventoryCount
            int remainingShots = (internalAmmo + inventoryCount) / consumption;

            slotList.add(slot);
            ammoList.add(remainingShots);
        }

        if (slotList.isEmpty()) return;

        int[] slots = slotList.stream().mapToInt(i -> i).toArray();
        int[] ammo = ammoList.stream().mapToInt(i -> i).toArray();

        AmmoSyncMessage message = new AmmoSyncMessage(entity.getId(), slots, ammo);
        // Only send to players riding this vehicle — others don't need ammo data
        for (var player : entity.level().players()) {
            if (player.getVehicle() == entity && player instanceof ServerPlayer sp) {
                NetworkHandler.sendToPlayer(message, sp);
            }
        }
    }

    /**
     * Maps a weapon item to its ammo config, mirroring the client-side
     * {@code WeaponHudOverlay.getAmmoInfo()}.
     */
    private static AmmoConfig getAmmoConfig(Item weaponItem) {
        if (weaponItem == ModItems.MACHINE_GUN.get()) {
            return new AmmoConfig(ModConfig.MACHINE_GUN_AMMO, ModConfig.MACHINE_GUN_AMMO_CONSUMPTION);
        } else if (weaponItem == ModItems.HEAVY_CANNON.get()) {
            return new AmmoConfig(ModConfig.HEAVY_CANNON_AMMO, ModConfig.HEAVY_CANNON_AMMO_CONSUMPTION);
        } else if (weaponItem == ModItems.HEAVY_BOMB_BAY.get()) {
            return new AmmoConfig(ModConfig.HEAVY_BOMB_BAY_AMMO, ModConfig.HEAVY_BOMB_BAY_AMMO_CONSUMPTION);
        } else if (weaponItem == ModItems.HOMING_ROCKET_LAUNCHER.get()) {
            return new AmmoConfig(ModConfig.HOMING_ROCKET_AMMO, ModConfig.HOMING_ROCKET_AMMO_CONSUMPTION);
        } else if (weaponItem == ModItems.CLUSTER_DISPENSER.get()) {
            return new AmmoConfig(ModConfig.CLUSTER_DISPENSER_AMMO, ModConfig.CLUSTER_DISPENSER_AMMO_CONSUMPTION);
        } else if (weaponItem == ModItems.ROTARY_CANNON.get()) {
            return new AmmoConfig(ModConfig.ROTARY_CANNON_AMMO, ModConfig.ROTARY_CANNON_AMMO_CONSUMPTION);
        } else if (weaponItem == ModItems.ROCKET_POD.get()) {
            return new AmmoConfig(ModConfig.ROCKET_POD_AMMO, ModConfig.ROCKET_POD_AMMO_CONSUMPTION);
        } else if (weaponItem == ModItems.NAPALM_BOMB_BAY.get()) {
            return new AmmoConfig(ModConfig.NAPALM_BOMB_AMMO, ModConfig.NAPALM_BOMB_AMMO_CONSUMPTION);
        }
        return null;
    }

    private record AmmoConfig(ForgeConfigSpec.ConfigValue<String> ammoId, ForgeConfigSpec.IntValue consumption) {}
}
