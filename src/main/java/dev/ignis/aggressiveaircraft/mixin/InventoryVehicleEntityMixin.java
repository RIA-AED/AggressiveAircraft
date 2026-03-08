package dev.ignis.aggressiveaircraft.mixin;

import immersive_aircraft.entity.InventoryVehicleEntity;
import immersive_aircraft.screen.VehicleScreenHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryVehicleEntity.class)
public class InventoryVehicleEntityMixin {

    @Inject(method = "m_6096_(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true, remap = false)
    private void preventInventoryOpenWhileFlying(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        InventoryVehicleEntity vehicle = (InventoryVehicleEntity) (Object) this;
        // 如果飞行器不在地面上，阻止打开库存
        if (!vehicle.onGround() && player.isCrouching()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}


