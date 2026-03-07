package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.AggressiveAircraft;
import immersive_aircraft.entity.AircraftEntity;
import immersive_aircraft.entity.EngineVehicle;
import immersive_aircraft.item.upgrade.VehicleStat;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AircraftEntity.class)
public abstract class TailGroundDetectionMixin extends EngineVehicle {

    // 目标俯仰角：-5度表示机头微微上翘（机尾下沉），这样飞机不会翻跟斗
    @Unique
    private static final float TARGET_PITCH = -5.0f;

    public TailGroundDetectionMixin(EntityType<? extends EngineVehicle> entityType, Level world, boolean canExplodeOnCrash) {
        super(entityType, world, canExplodeOnCrash);
    }

    /**
     * 在updateVelocity方法返回前注入，覆盖原方法的地面俯仰处理
     * 原方法会在地面时将俯仰角收敛到 -GROUND_PITCH，这会导致使用瓦尔基里mod时飞机翻跟斗
     * 这里我们将俯仰角固定到一个合适的值，保持飞机稳定
     */
    @Inject(method = "updateVelocity()V", at = @At("RETURN"), remap = false)
    private void limitTailSinkOnGround(CallbackInfo ci) {
        /*
        AircraftEntity aircraft = (AircraftEntity)(Object)this;

        // 只有在地面且没有乘客控制时才进行干预
        if (!aircraft.onGround() || aircraft.hasControllingPassenger()) {
            return;
        }

        // 检查飞机是否真的接触地面（瓦尔基里mod可能导致onGround()返回true但实际上没有接触地面）
        // 通过检测飞机下方是否有 solid 方块来判断
        if (aircraft.onGround()) {
            // 如果没有真正接触地面，保持水平姿态
            var pitch = aircraft.getProperties().get(VehicleStat.GROUND_PITCH);
            aircraft.setXRot(-pitch);
        }*/
        if(onGround()){
            AggressiveAircraft.LOGGER.debug("Aircraft is on the ground");
        }
    }
}
