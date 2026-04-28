package dev.ignis.aggressiveaircraft.mixin;

import immersive_aircraft.cobalt.network.NetworkHandler;
import immersive_aircraft.entity.InventoryVehicleEntity;
import immersive_aircraft.entity.inventory.SparseSimpleInventory;
import immersive_aircraft.network.s2c.InventoryUpdateMessage;
import immersive_aircraft.screen.VehicleScreenHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Periodically sends a full inventory sync to all nearby players once per second (every 20 ticks).
 * This ensures the client has up-to-date ammo data for the weapon HUD display,
 * since the vanilla sync only covers non-inventory slots (weapons, upgrades, boosters).
 */
@Mixin(SparseSimpleInventory.class)
public abstract class SparseSimpleInventoryMixin extends SimpleContainer {

    public SparseSimpleInventoryMixin(int p_19150_) {
        super(p_19150_);
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void periodicFullInventorySync(InventoryVehicleEntity entity, CallbackInfo ci) {
        // Server side only, once per second
        if (entity.level().isClientSide || entity.tickCount % 20 != 0) {
            return;
        }

        int totalSlots = this.getContainerSize();
        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = this.getItem(i);
            final int slot = i;
            for (var player : entity.level().players()) {
                // Don't send to players who already have the vehicle GUI open (they get synced via container menu)
                if (player instanceof ServerPlayer serverPlayer
                        && !(serverPlayer.containerMenu instanceof VehicleScreenHandler vsh && vsh.getVehicle() == entity)) {
                    NetworkHandler.sendToPlayer(new InventoryUpdateMessage(entity.getId(), slot, stack), serverPlayer);
                }
            }
        }
    }
}
