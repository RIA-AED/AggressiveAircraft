package dev.ignis.aggressiveaircraft.mixin;

import immersive_aircraft.entity.AircraftEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AircraftEntity.class)
public class TailGroundDetectionMixin {

    // 最大允许的下沉角度（俯仰角），单位：度
    // 负值表示机头向上（机尾下沉），这里限制为-15度
    @Unique
    private static final float MAX_TAIL_SINK_PITCH = -15.0f;

    // 接近地面检测的垂直距离阈值
    @Unique
    private static final double GROUND_PROXIMITY_THRESHOLD = 0.3;

    @Inject(method = "updateVelocity()V", at = @At("HEAD"), remap = false)
    private void limitTailSinkOnGround(CallbackInfo ci) {
        AircraftEntity aircraft = (AircraftEntity)(Object)this;

        // 只有在地面时才进行限制
        if (!aircraft.onGround()) {
            return;
        }

        float currentPitch = aircraft.getXRot();

        // 如果机尾下沉太严重（俯仰角小于阈值，即机头太向上）
        if (currentPitch < MAX_TAIL_SINK_PITCH) {
            // 检查是否接近地面（防止飞行中误判）
            if (aggressiveAircraft$isNearGround(aircraft)) {
                // 限制俯仰角，防止机尾继续下沉
                aircraft.setXRot(MAX_TAIL_SINK_PITCH);

                // 停止垂直下沉，防止翻跟头
                if (aircraft.getDeltaMovement().y < 0) {
                    aircraft.setDeltaMovement(
                            aircraft.getDeltaMovement().x,
                            0,
                            aircraft.getDeltaMovement().z
                    );
                }
            }
        }
    }

    /**
     * 检查实体是否接近地面
     * 通过向下检测碰撞箱来实现
     */
    @Unique
    private boolean aggressiveAircraft$isNearGround(AircraftEntity aircraft) {
        AABB boundingBox = aircraft.getBoundingBox();

        // 向下扩展碰撞箱检测地面
        AABB checkBox = new AABB(
                boundingBox.minX,
                boundingBox.minY - GROUND_PROXIMITY_THRESHOLD,
                boundingBox.minZ,
                boundingBox.maxX,
                boundingBox.minY + 0.1, // 稍微向上一点，确保检测当前位置
                boundingBox.maxZ
        );

        // 检查是否有碰撞（即是否有方块）
        return !aircraft.level().noCollision(checkBox);
    }
}
