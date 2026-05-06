package dev.ignis.aggressiveaircraft.mixin;

import immersive_aircraft.entity.BambooHopperEntity;
import net.minecraft.tags.FluidTags;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BambooHopperEntity.class)
public class BambooHopperEntityMixin {

    @Inject(method = "getGravity", at = @At("HEAD"), cancellable = true, remap = false)
    private void aggressiveAircraft$applyFixedWingGravity(CallbackInfoReturnable<Float> cir) {
        BambooHopperEntity self = (BambooHopperEntity) (Object) this;
        double water = self.getFluidHeight(FluidTags.WATER);

        // 保留水上特性：在水中时不拦截，让原方法继续执行
        if (water > 0) {
            return;
        }

        // 空中使用 AirplaneEntity 的固定翼升力逻辑
        Vector3f direction = self.getForwardDirection();
        float speed = (float) self.getDeltaMovement().length() * (1.0f - Math.abs(direction.y));
        float baseGravity = -0.04f; // VehicleEntity.getGravity() 的基础值
        float gravity = Math.max(0.0f, 1.0f - speed * 1.5f) * baseGravity;
        cir.setReturnValue(gravity);
    }
}
