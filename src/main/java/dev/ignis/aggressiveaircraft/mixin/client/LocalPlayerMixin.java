package dev.ignis.aggressiveaircraft.mixin.client;

import dev.ignis.aggressiveaircraft.ModConfig;
import immersive_aircraft.entity.InventoryVehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LocalPlayer.class, priority = 999)
public class LocalPlayerMixin {

    /**
     * 在 IA 的 ClientPlayerEntityMixin.sendOpenInventory 之前拦截 E 键。
     * 如果飞行条件不满足 → 直接打开原版玩家库存，不让 IA 发 CommandMessage。
     * 如果在地面 → 放行，让 IA 正常打开飞机库存。
     * priority=999 > IA 的默认 1000，确保先运行。
     */
    @Inject(method = "sendOpenInventory()V", at = @At("HEAD"), cancellable = true)
    private void aa$interceptInventoryKey(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (self.getRootVehicle() instanceof InventoryVehicleEntity vehicle) {
            double heightThreshold = ModConfig.INVENTORY_HEIGHT_THRESHOLD.get();
            double currentY = vehicle.getY();
            // 与服务器端相同条件：飞行中 或 高度不足 → 打开原版背包
            if (!vehicle.onGround() || (heightThreshold > 0 && currentY < heightThreshold)) {
                Minecraft.getInstance().setScreen(new InventoryScreen(self));
                ci.cancel();
            }
        }
    }
}
