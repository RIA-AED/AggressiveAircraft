package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.compat.vs.AircraftShipContextProvider;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 阻止飞机在 Valkyrien Skies 船舶上受到任何伤害。
 *
 * <p>由于 VS 船舶的碰撞检测与飞机相对运动计算存在精度问题，
 * 飞机在船舶甲板上时会莫名其妙地因碰撞而掉血。
 * 当飞机被检测到附着在 VS 船舶上时，完全屏蔽所有伤害。</p>
 */
@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleEntityShipDamageMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void aggressiveAircraft$preventDamageOnShip(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        VehicleEntity aircraft = (VehicleEntity) (Object) this;
        if (aircraft instanceof AircraftShipContextProvider provider
                && provider.aggressiveAircraft$getShipContext().isAttached()) {
            cir.setReturnValue(false);
        }
    }
}
