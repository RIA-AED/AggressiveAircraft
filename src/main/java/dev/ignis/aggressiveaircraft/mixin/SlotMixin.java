package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.ModConfig;
import immersive_aircraft.entity.inventory.SparseSimpleInventory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Prevents players from manually placing restricted ammo items into
 * aircraft cargo inventory via GUI, while still allowing:
 * <ul>
 *   <li>Supply stations to insert ammo programmatically (via setItem)</li>
 *   <li>Players to take ammo out of cargo</li>
 * </ul>
 * <p>
 * Only affects slots whose container is {@link SparseSimpleInventory},
 * i.e. the Immersive Aircraft vehicle cargo. Player inventory and
 * all other containers are unaffected.
 */
@Mixin(Slot.class)
public abstract class SlotMixin {

    @Final
    @Shadow
    public net.minecraft.world.Container container;

    @Unique
    private static final Set<String> RESTRICTED_AMMO;

    static {
        RESTRICTED_AMMO = Set.of(
                ModConfig.HEAVY_BOMB_BAY_AMMO.get(),
                ModConfig.HOMING_ROCKET_AMMO.get(),
                ModConfig.CLUSTER_DISPENSER_AMMO.get(),
                ModConfig.ROCKET_POD_AMMO.get(),
                ModConfig.NAPALM_BOMB_AMMO.get()
        );
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void restrictAmmoInVehicleCargo(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) return;
        if (!(this.container instanceof SparseSimpleInventory)) return;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (RESTRICTED_AMMO.contains(itemId)) {
            cir.setReturnValue(false);
        }
    }
}
