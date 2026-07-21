package dev.ignis.aggressiveaircraft.blocks;

import net.minecraftforge.energy.EnergyStorage;
import org.jetbrains.annotations.Nullable;

public class SupplyStationEnergyStorage extends EnergyStorage {
    @Nullable
    private Runnable onChanged;

    public SupplyStationEnergyStorage(int capacity, int maxReceive, int maxExtract, int energy) {
        super(capacity, maxReceive, maxExtract, energy);
    }

    public void setOnChanged(@Nullable Runnable callback) {
        this.onChanged = callback;
    }

    public void consumeEnergy(int amount) {
        this.energy = Math.max(0, this.energy - amount);
        if (onChanged != null) onChanged.run();
    }

    public void setEnergy(int amount) {
        this.energy = Math.max(0, Math.min(amount, this.capacity));
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int received = super.receiveEnergy(maxReceive, simulate);
        if (!simulate && received > 0 && onChanged != null) onChanged.run();
        return received;
    }
}
