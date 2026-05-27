package dev.ignis.aggressiveaircraft.mixin.client;

import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class VehicleEntityClientMixin {

    @Unique
    private boolean aggressiveAircraft$isLocalPlayerPiloting() {
        VehicleEntity aircraft = (VehicleEntity)(Object)this;
        for (Entity passenger : aircraft.getPassengers()) {
            if (passenger instanceof net.minecraft.client.player.LocalPlayer) {
                return true;
            }
        }
        return false;
    }

    /**
     * 客户端：当飞机完全无乘客（真正停放）时，强制返回 onGround=true
     * 解决客户端 onGround 不同步导致的渲染翻跟斗问题
     * 注意：当其他玩家在驾驶时不应触发，否则会导致观察者看到错误的姿态
     */
    @Inject(
            method = "onGround",
            at = @At("HEAD"),
            cancellable = true
    )
    private void forceOnGroundWhenNoPilot(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        // 只在客户端执行，且是 VehicleEntity，且飞机完全没有乘客
        if (self.level().isClientSide() && self instanceof VehicleEntity vehicle) {
            if (vehicle.getPassengers().isEmpty() && !aggressiveAircraft$isLocalPlayerPiloting()) {
                cir.setReturnValue(true);
            }
        }
    }
}
