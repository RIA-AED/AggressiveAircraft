package dev.ignis.aggressiveaircraft.utils;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class TargetSelector {
    private static final String TRACKED_TAG = "airstrikepointers:tracked";
    private static final float HEALTH_THRESHOLD = 25.0f;

    /**
     * 分层索敌逻辑
     * 1. 优先选择带tracked标签的目标（距离搜索点最近）
     * 2. 否则按生命值分层：200+ → 100+ → 50+，每层找距离最近且无阻碍的目标
     */
    public static LivingEntity selectTarget(Level level, Vec3 scanCenter, Vec3 startPos,
                                            List<LivingEntity> candidates, Entity clipEntity) {
        List<LivingEntity> validTargets = candidates.stream()
            .filter(e -> e.getHealth() >= HEALTH_THRESHOLD)
            .toList();

        if (validTargets.isEmpty()) {
            return null;
        }

        // 优先选择带tracked标签的目标
        LivingEntity trackedTarget = validTargets.stream()
            .filter(e -> e.getPersistentData().getBoolean(TRACKED_TAG))
            .min(Comparator.comparingDouble(e -> e.distanceToSqr(scanCenter.x, scanCenter.y, scanCenter.z)))
            .orElse(null);

        if (trackedTarget != null) {
            return trackedTarget;
        }

        // 分层搜索：200+ → 100+ → 50+
        LivingEntity target = findNearestVisibleTarget(level, validTargets, scanCenter, startPos, 200.0f, clipEntity);
        if (target != null) return target;

        target = findNearestVisibleTarget(level, validTargets, scanCenter, startPos, 100.0f, clipEntity);
        if (target != null) return target;

        return findNearestVisibleTarget(level, validTargets, scanCenter, startPos, 50.0f, clipEntity);
    }

    private static LivingEntity findNearestVisibleTarget(Level level, List<LivingEntity> targets,
                                                         Vec3 scanCenter, Vec3 startPos,
                                                         float minMaxHealth, Entity clipEntity) {
        return targets.stream()
            .filter(e -> e.getMaxHealth() >= minMaxHealth)
            .filter(e -> hasLineOfSight(level, startPos, e, clipEntity))
            .min(Comparator.comparingDouble(e -> e.distanceToSqr(scanCenter.x, scanCenter.y, scanCenter.z)))
            .orElse(null);
    }

    private static boolean hasLineOfSight(Level level, Vec3 start, LivingEntity target, Entity clipEntity) {
        Vec3 targetPos = new Vec3(target.getX(), target.getY() + target.getEyeHeight(), target.getZ());
        BlockHitResult result = level.clip(
            new ClipContext(start, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, clipEntity)
        );
        return result.getType() == HitResult.Type.MISS;
    }
}
