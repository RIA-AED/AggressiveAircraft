package dev.ignis.aggressiveaircraft.mixin.client;

import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class VehicleEntityClientMixin {

    /**
     * 客户端：当飞机无乘客时，强制返回 onGround=true
     * 解决客户端 onGround 不同步导致的渲染翻跟斗问题
     */
    @Inject(
            method = "onGround",
            at = @At("HEAD"),
            cancellable = true
    )
    private void forceOnGroundWhenNoPilot(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        // 只在客户端执行，且是 VehicleEntity
        if (self.level().isClientSide() && self instanceof VehicleEntity vehicle) {
            if (vehicle.getPassengers().isEmpty()) {
                cir.setReturnValue(true);
            }
        }
    }
}
