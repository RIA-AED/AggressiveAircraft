package dev.ignis.aggressiveaircraft.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache of ammo data received from the server via {@code AmmoSyncMessage}.
 * <p>
 * Outer map: vehicle entity ID → inner map (weapon slot index → remaining shots).
 * - A positive value means that many shots remain.
 * - -1 means creative mode / infinite ammo.
 * - Absence of an entry means no data received yet (or vehicle has no weapons).
 */
public class ClientAmmoData {
    private static final Map<Integer, Map<Integer, Integer>> DATA = new HashMap<>();

    public static void update(int vehicleId, int[] slotIndices, int[] remainingShots) {
        Map<Integer, Integer> vehicleData = DATA.computeIfAbsent(vehicleId,
                k -> new HashMap<>());
        vehicleData.clear();
        for (int i = 0; i < slotIndices.length; i++) {
            vehicleData.put(slotIndices[i], remainingShots[i]);
        }
    }

    /**
     * Get remaining shots for a vehicle's weapon slot.
     *
     * @return remaining shots, or {@code null} if no data available
     */
    public static Integer getRemainingShots(int vehicleId, int slotIndex) {
        Map<Integer, Integer> vehicleData = DATA.get(vehicleId);
        if (vehicleData == null) return null;
        return vehicleData.get(slotIndex);
    }

    /**
     * Remove stale data when a vehicle is unloaded.
     */
    public static void remove(int vehicleId) {
        DATA.remove(vehicleId);
    }

    public static void clear() {
        DATA.clear();
    }
}
