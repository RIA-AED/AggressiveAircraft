package dev.ignis.aggressiveaircraft.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

/**
 * 暴露 PrimedTnt 的 private owner 字段，用于设置炸弹的伤害来源。
 */
@Mixin(PrimedTnt.class)
public interface PrimedTntAccessor {
    @Accessor(value = "owner")
    void setOwner(@Nullable LivingEntity owner);
}
