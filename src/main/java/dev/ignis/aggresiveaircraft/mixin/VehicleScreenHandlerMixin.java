package dev.ignis.aggresiveaircraft.mixin;

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
        // 如果飞行器不在地面上，关闭库存界面
        if (!vehicle.onGround()) {
            cir.setReturnValue(false);
        }
    }
}
