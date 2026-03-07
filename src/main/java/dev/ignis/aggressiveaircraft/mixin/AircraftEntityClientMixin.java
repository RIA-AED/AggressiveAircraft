package dev.ignis.aggressiveaircraft.mixin;

import immersive_aircraft.entity.AircraftEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AircraftEntity.class)
public class AircraftEntityClientMixin {

    /**
     * 修复客户端物理计算导致的俯仰角漂移
     */
    @Inject(
            method = "updateVelocity",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void skipVelocityUpdateWhenNoPilot(CallbackInfo ci) {
        AircraftEntity aircraft = (AircraftEntity)(Object)this;

        // 客户端且没有乘客时，跳过物理计算
        if (aircraft.level().isClientSide() && aircraft.getPassengers().isEmpty()) {
            ci.cancel();
        }
    }

    /**
     * 修复客户端控制器更新导致的俯仰角漂移
     */
    @Inject(
            method = "updateController",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void skipControllerUpdateWhenNoPilot(CallbackInfo ci) {
        AircraftEntity aircraft = (AircraftEntity)(Object)this;

        // 客户端且没有乘客时，跳过控制器更新
        if (aircraft.level().isClientSide() && aircraft.getPassengers().isEmpty()) {
            ci.cancel();
        }
    }
}
