package dev.ignis.aggressiveaircraft.network.s2c;

import dev.ignis.aggressiveaircraft.client.ClientAmmoData;
import immersive_aircraft.cobalt.network.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

/**
 * Server → Client: lightweight ammo summary for weapon HUD display.
 * Carries remaining shots per weapon slot, computed server-side from
 * weapon internal ammo buffer + inventory ammo item counts.
 * <p>
 * Bandwidth: ~5 bytes per weapon slot (slotIndex + remainingShots as varint),
 * vs ~hundreds of bytes for full ItemStack NBT in {@code InventoryUpdateMessage}.
 */
public class AmmoSyncMessage extends Message {
    private final int vehicleId;
    private final int[] slotIndices;
    private final int[] remainingShots;

    /**
     * @param vehicleId      entity ID of the {@code InventoryVehicleEntity}
     * @param slotIndices    weapon slot indices in the vehicle inventory
     * @param remainingShots remaining shots for each slot; -1 means creative/infinite
     */
    public AmmoSyncMessage(int vehicleId, int[] slotIndices, int[] remainingShots) {
        this.vehicleId = vehicleId;
        this.slotIndices = slotIndices;
        this.remainingShots = remainingShots;
    }

    public AmmoSyncMessage(FriendlyByteBuf buf) {
        vehicleId = buf.readVarInt();
        int count = buf.readVarInt();
        slotIndices = new int[count];
        remainingShots = new int[count];
        for (int i = 0; i < count; i++) {
            slotIndices[i] = buf.readVarInt();
            remainingShots[i] = buf.readVarInt();
        }
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(vehicleId);
        buf.writeVarInt(slotIndices.length);
        for (int i = 0; i < slotIndices.length; i++) {
            buf.writeVarInt(slotIndices[i]);
            buf.writeVarInt(remainingShots[i]);
        }
    }

    @Override
    public void receive(Player player) {
        // player is null on client side for S2C messages (Forge's getSender() returns null).
        // AmmoSyncMessage is always S2C, so we always update on the receiving (client) side.
        ClientAmmoData.update(vehicleId, slotIndices, remainingShots);
    }
}
