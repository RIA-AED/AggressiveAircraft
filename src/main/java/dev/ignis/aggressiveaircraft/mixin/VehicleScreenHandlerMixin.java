package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.ModConfig;
import immersive_aircraft.entity.InventoryVehicleEntity;
import immersive_aircraft.screen.VehicleScreenHandler;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VehicleScreenHandler.class)
public class VehicleScreenHandlerMixin {

    @Inject(method = "m_6875_(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void closeInventoryWhenFlying(Player player, CallbackInfoReturnable<Boolean> cir) {
        VehicleScreenHandler handler = (VehicleScreenHandler) (Object) this;
        InventoryVehicleEntity vehicle = handler.getVehicle();
        double heightThreshold = ModConfig.INVENTORY_HEIGHT_THRESHOLD.get();
        double currentY = vehicle.getY();
        // 如果飞行器不在地面上，或者高度低于配置阈值，关闭库存界面
        if (!vehicle.onGround() || (heightThreshold > 0 && currentY < heightThreshold)) {
            cir.setReturnValue(false);
        }
    }
}
