package dev.ignis.aggressiveaircraft.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobType;

/**
 * 标记接口：显式实现此接口的实体是集束弹布撒器的有效攻击目标。
 * 玩家不是目标（子弹药不锁定玩家）。
 *
 * <p>此外，敌对类别的生物也视为有效目标，见 {@link #isHostile(LivingEntity)}。</p>
 */
public interface Enemy {

    /**
     * 是否为敌对目标：显式实现 Enemy 接口，或属于敌对类别，或为亡灵/节肢/灾厄生物。
     *
     * <p>敌对类别判定：{@link MobCategory#isFriendly()} 为 false 且非 MISC。
     * 这样能覆盖各 mod 自定义的敌对类别（如 Spore 的 INFECTED/ORGANOID/EXPERIMENTS），
     * 同时排除 MISC 下的村民、铁傀儡等被动/中立实体（MISC 中真正的敌怪如幻术师
     * 由 MobType.ILLAGER 捕获）。</p>
     */
    static boolean isHostile(LivingEntity entity) {
        MobType type = entity.getMobType();
        if (type == MobType.UNDEAD || type == MobType.ARTHROPOD || type == MobType.ILLAGER) {
            return true;
        }
        MobCategory category = entity.getType().getCategory();
        return category != MobCategory.MISC && !category.isFriendly();
    }
}
