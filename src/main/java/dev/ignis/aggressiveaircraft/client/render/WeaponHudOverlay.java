package dev.ignis.aggressiveaircraft.client.render;

import dev.ignis.aggressiveaircraft.ModConfig;
import dev.ignis.aggressiveaircraft.items.ModItems;
import immersive_aircraft.entity.InventoryVehicleEntity;
import immersive_aircraft.entity.weapon.Weapon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WeaponHudOverlay {

    /**
     * Represents one weapon entry to render on the HUD.
     */
    private record WeaponEntry(ItemStack weaponIcon, int remainingShots, boolean isCreative) {}

    /**
     * Maps a weapon item to its ammo config (ammo resource ID) and consumption value.
     * Returns null if the weapon is not recognized.
     */
    private static AmmoInfo getAmmoInfo(Item weaponItem) {
        if (weaponItem == ModItems.MACHINE_GUN.get()) {
            return new AmmoInfo(ModConfig.MACHINE_GUN_AMMO, ModConfig.MACHINE_GUN_AMMO_CONSUMPTION);
        } else if (weaponItem == ModItems.HEAVY_CANNON.get()) {
            return new AmmoInfo(ModConfig.HEAVY_CANNON_AMMO, ModConfig.HEAVY_CANNON_AMMO_CONSUMPTION);
        } else if (weaponItem == ModItems.HEAVY_BOMB_BAY.get()) {
            return new AmmoInfo(ModConfig.HEAVY_BOMB_BAY_AMMO, ModConfig.HEAVY_BOMB_BAY_AMMO_CONSUMPTION);
        } else if (weaponItem == ModItems.HOMING_ROCKET_LAUNCHER.get()) {
            return new AmmoInfo(ModConfig.HOMING_ROCKET_AMMO, ModConfig.HOMING_ROCKET_AMMO_CONSUMPTION);
        } else if (weaponItem == ModItems.CLUSTER_DISPENSER.get()) {
            return new AmmoInfo(ModConfig.CLUSTER_DISPENSER_AMMO, ModConfig.CLUSTER_DISPENSER_AMMO_CONSUMPTION);
        } else if (weaponItem == ModItems.ROTARY_CANNON.get()) {
            return new AmmoInfo(ModConfig.ROTARY_CANNON_AMMO, ModConfig.ROTARY_CANNON_AMMO_CONSUMPTION);
        } else if (weaponItem == ModItems.ROCKET_POD.get()) {
            return new AmmoInfo(ModConfig.ROCKET_POD_AMMO, ModConfig.ROCKET_POD_AMMO_CONSUMPTION);
        }
        return null;
    }

    private record AmmoInfo(ForgeConfigSpec.ConfigValue<String> ammoId, ForgeConfigSpec.IntValue consumption) {}

    /**
     * Count how many matching ammo items are in the vehicle's inventory.
     */
    private static int countAmmoInInventory(InventoryVehicleEntity vehicle, String ammoId) {
        int count = 0;
        var inventory = vehicle.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (itemId.equals(ammoId)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    @SubscribeEvent
    public void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        // Only render after the crosshair overlay
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof InventoryVehicleEntity inventoryVehicle)) return;

        Map<Integer, List<Weapon>> weaponsMap = inventoryVehicle.getWeapons();
        if (weaponsMap.isEmpty()) return;

        boolean isCreative = player.isCreative();

        // Collect all weapon entries (deduplicate same-slot weapons, show one per slot)
        List<WeaponEntry> entries = new ArrayList<>();
        for (Map.Entry<Integer, List<Weapon>> entry : weaponsMap.entrySet()) {
            List<Weapon> weaponList = entry.getValue();
            if (weaponList.isEmpty()) continue;

            // Use the first weapon in the slot as representative
            Weapon weapon = weaponList.get(0);
            ItemStack weaponStack = weapon.getStack();
            if (weaponStack.isEmpty()) continue;

            AmmoInfo ammoInfo = getAmmoInfo(weaponStack.getItem());
            int remainingShots;
            if (ammoInfo != null) {
                String ammoId = ammoInfo.ammoId().get();
                int consumption = ammoInfo.consumption().get();
                int totalAmmo = countAmmoInInventory(inventoryVehicle, ammoId);
                remainingShots = consumption > 0 ? totalAmmo / consumption : 0;
            } else {
                remainingShots = 0;
            }

            entries.add(new WeaponEntry(weaponStack, remainingShots, isCreative));
        }

        if (entries.isEmpty()) return;

        // Render parameters
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();

        int iconSize = 16;
        int textGap = 4;     // gap between icon and text
        int rowHeight = 20;  // vertical spacing per row

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // Position: left of crosshair, with some margin
        int hudRightEdge = centerX - 20; // 20px left of center

        // Vertical centering
        int totalHeight = entries.size() * rowHeight;
        int startY = centerY - totalHeight / 2;

        for (int i = 0; i < entries.size(); i++) {
            WeaponEntry we = entries.get(i);
            int rowY = startY + i * rowHeight;

            // Draw weapon icon
            int iconX = hudRightEdge - iconSize - textGap - mc.font.width(we.isCreative ? "\u221E" : String.valueOf(we.remainingShots));
            int iconY = rowY + (rowHeight - iconSize) / 2;
            guiGraphics.renderItem(we.weaponIcon, iconX, iconY);

            // Draw remaining shots text
            String text = we.isCreative ? "\u221E" : String.valueOf(we.remainingShots);
            int textX = iconX + iconSize + textGap;
            int textY = rowY + (rowHeight - mc.font.lineHeight) / 2;

            // Color: white normally, red if out of ammo
            int color = (we.remainingShots <= 0 && !we.isCreative) ? 0xFFFF5555 : 0xFFFFFFFF;
            guiGraphics.drawString(mc.font, text, textX, textY, color, true);
        }
    }
}
