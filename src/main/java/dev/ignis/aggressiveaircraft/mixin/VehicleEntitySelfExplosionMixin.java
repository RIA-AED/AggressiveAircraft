package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.entities.ClusterBombletEntity;
import dev.ignis.aggressiveaircraft.entities.ClusterDispenserEntity;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 飞机免疫集束弹布撒器的自伤：布撒器/子弹药爆炸的伤害来源是机上的驾驶员
 * （explode() 以驾驶员为爆炸源实体），此处拦截所有"由本机乘员引发"的爆炸，
 * 以及本 mod 布撒器/子弹药实体引发的爆炸（无驾驶员时源实体即投射物本身）。
 *
 * <p>防止低空/悬停投弹时被自己的集束弹炸下来（子弹药贴脸爆炸威力4.0，
 * 布撒器被飞机追上后撞机身爆炸），与高度无关。</p>
 */
@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleEntitySelfExplosionMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true, remap = true)
    private void aggressiveAircraft$blockSelfInflictedExplosion(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!source.is(DamageTypes.EXPLOSION)) {
            return;
        }
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        Entity direct = source.getDirectEntity();
        if (direct == null) {
            return;
        }
        // 本 mod 的集束弹布撒器/子弹药爆炸：任何飞机都免疫
        if (direct instanceof ClusterDispenserEntity || direct instanceof ClusterBombletEntity) {
            cir.setReturnValue(false);
            return;
        }
        // 本机乘员（驾驶员/炮手）引发的爆炸不伤害自己的飞机
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger == direct) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
