package dev.ignis.aggressiveaircraft.client.render;

import dev.ignis.aggressiveaircraft.client.ClientAmmoData;
import immersive_aircraft.entity.InventoryVehicleEntity;
import immersive_aircraft.entity.weapon.Weapon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders a weapon HUD to the left of the crosshair, showing weapon icons
 * and remaining ammo counts computed server-side.
 * <p>
 * Ammo data is received via {@link AmmoSyncMessage} and cached in
 * {@link ClientAmmoData}. The server computes {@code remainingShots = (weapon.ammo + inventoryCount) / consumption}
 * and pushes updates every 10 ticks (2 Hz).
 */
public class WeaponHudOverlay {

    /**
     * Represents one weapon entry to render on the HUD.
     */
    private record WeaponEntry(ItemStack weaponIcon, int remainingShots, boolean isCreative) {}

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
        int vehicleId = inventoryVehicle.getId();

        // Collect weapon entries from server-computed ammo data
        List<WeaponEntry> entries = new ArrayList<>();
        for (Map.Entry<Integer, List<Weapon>> entry : weaponsMap.entrySet()) {
            int slot = entry.getKey();
            List<Weapon> weaponList = entry.getValue();
            if (weaponList.isEmpty()) continue;

            // Use the first weapon in the slot as representative
            Weapon weapon = weaponList.get(0);
            ItemStack weaponStack = weapon.getStack();
            if (weaponStack.isEmpty()) continue;

            // Read server-computed remaining shots; null if not yet synced or unsupported weapon
            Integer remaining = ClientAmmoData.getRemainingShots(vehicleId, slot);
            if (remaining == null) continue;

            entries.add(new WeaponEntry(weaponStack, remaining, isCreative));
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
            int iconX = hudRightEdge - iconSize - textGap - mc.font.width(we.isCreative ? "∞" : String.valueOf(we.remainingShots));
            int iconY = rowY + (rowHeight - iconSize) / 2;
            guiGraphics.renderItem(we.weaponIcon, iconX, iconY);

            // Draw remaining shots text
            String text = we.isCreative ? "∞" : String.valueOf(we.remainingShots);
            int textX = iconX + iconSize + textGap;
            int textY = rowY + (rowHeight - mc.font.lineHeight) / 2;

            // Color: white normally, red if out of ammo
            int color = (we.remainingShots <= 0 && !we.isCreative) ? 0xFFFF5555 : 0xFFFFFFFF;
            guiGraphics.drawString(mc.font, text, textX, textY, color, true);
        }
    }
}
