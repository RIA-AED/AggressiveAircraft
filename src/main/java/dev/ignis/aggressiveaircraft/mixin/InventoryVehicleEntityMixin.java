package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.ModConfig;
import immersive_aircraft.entity.InventoryVehicleEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryVehicleEntity.class)
public class InventoryVehicleEntityMixin {

    @Inject(
            method = {
                    "interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
                    "m_6096_(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void preventInventoryOpenWhileFlying(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        // 创造模式玩家始终允许打开物品栏
        if (player.isCreative()) {
            return;
        }
        InventoryVehicleEntity vehicle = (InventoryVehicleEntity) (Object) this;
        double heightThreshold = ModConfig.INVENTORY_HEIGHT_THRESHOLD.get();
        double currentY = vehicle.getY();
        if (!vehicle.onGround() || (heightThreshold > 0 && currentY < heightThreshold)) {
            // 只拦截会打开库存的路径，上飞机路径放行：
            //   - isSecondaryUseActive (蹲下) → kick/inventory → 拦截
            //   - getPassengerSpace() == 0 (无座位) → inventory → 拦截
            //   - !isSecondaryUseActive && getPassengerSpace() > 0 → mount → 放行
            if (player.isSecondaryUseActive() || vehicle.getPassengerSpace() <= 0) {
                cir.setReturnValue(InteractionResult.PASS);
            }
        }
    }

    /**
     * 拦截 E 键（背包键）打开库存的路径：
     * ClientPlayerEntityMixin.sendOpenInventory -> CommandMessage(INVENTORY) -> openInventory(ServerPlayer)
     * 这跳过了 interact 方法，需要在 openInventory 层面再做一次拦截
     */
    @Inject(method = "openInventory(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void preventOpenInventoryViaKey(ServerPlayer player, CallbackInfo ci) {
        if (player.isCreative()) {
            return;
        }
        InventoryVehicleEntity vehicle = (InventoryVehicleEntity) (Object) this;
        double heightThreshold = ModConfig.INVENTORY_HEIGHT_THRESHOLD.get();
        double currentY = vehicle.getY();
        if (!vehicle.onGround() || (heightThreshold > 0 && currentY < heightThreshold)) {
            ci.cancel();
        }
    }
}


