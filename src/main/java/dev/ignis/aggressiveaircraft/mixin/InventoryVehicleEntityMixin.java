package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.ModConfig;
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
        double heightThreshold = ModConfig.INVENTORY_HEIGHT_THRESHOLD.get();
        double currentY = vehicle.getY();
        // 如果飞行器不在地面上，或者高度低于配置阈值，阻止打开库存
        if ((!vehicle.onGround() || (heightThreshold > 0 && currentY < heightThreshold)) && player.isCrouching()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}


