package dev.ignis.aggressiveaircraft.mixin;

import immersive_aircraft.entity.VehicleEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VehicleEntity.class)
public class VehicleEntityMixin {

    @Shadow(remap = false)
    protected int interpolationSteps;
    @Shadow(remap = false)
    protected double serverYRot;
    @Shadow(remap = false)
    protected double serverXRot;

    /**
     * 修复客户端俯仰角同步问题
     * 当飞机没有乘客时，强制使用服务端的俯仰角，避免客户端物理计算导致的漂移
     */
    @Inject(
            method = "handleClientSync",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void fixClientSyncWhenNoPilot(CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity)(Object)this;

        // 如果没有乘客，跳过客户端插值，直接使用服务端数据
        if (vehicle.getPassengers().isEmpty() &&interpolationSteps > 0) {
            // 直接设置到服务端位置，不进行插值
            vehicle.setXRot((float)this.serverXRot);
            vehicle.setYRot((float)this.serverYRot);
            interpolationSteps = 0;
            ci.cancel();
        }
    }



    @Unique
    private static final Logger aggressiveAircraft$LOGGER = LogManager.getLogger("AircraftDebug");

    @Inject(
            method = "handleClientSync",
            at = @At("HEAD"),
            remap = false
    )
    private void debugHandleClientSyncStart(CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity)(Object)this;
        if (!vehicle.level().isClientSide()) return;
        if (!vehicle.getPassengers().isEmpty()) return;

        aggressiveAircraft$LOGGER.info(String.format(
                "[handleClientSync START] interpolationSteps: %d, serverXRot: %.2f, currentPitch: %.2f",
                interpolationSteps,
                serverXRot,
                vehicle.getXRot()
        ));
    }

    @Inject(
            method = "handleClientSync",
            at = @At("RETURN"),
            remap = false
    )
    private void debugHandleClientSyncEnd(CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity)(Object)this;
        if (!vehicle.level().isClientSide()) return;
        if (!vehicle.getPassengers().isEmpty()) return;

        aggressiveAircraft$LOGGER.info(String.format(
                "[handleClientSync END] currentPitch: %.2f",
                vehicle.getXRot()
        ));
    }
}
