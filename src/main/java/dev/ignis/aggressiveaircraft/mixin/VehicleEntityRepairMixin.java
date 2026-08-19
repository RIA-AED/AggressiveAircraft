package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.ModConfig;
import immersive_aircraft.config.Config;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * 机上自动维修：乘客/驾驶员快捷栏内携带配置表中的物品时，
 * 每秒自动消耗一个，为飞机恢复对应点数血量。
 *
 * <p>配置格式为 物品ID -> 恢复的血量点数。血量点数与伤害同一量纲：
 * 满血飞机 = damagePerHealthPoint 点伤害（默认30），
 * 即恢复量 = 点数 / durability / damagePerHealthPoint，与 {@code VehicleEntity.hurt} 的伤害换算互逆。</p>
 */
@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleEntityRepairMixin {

    private static final int REPAIR_INTERVAL = 20; // 每秒一次

    @Unique
    private int aggressiveAircraft$repairTimer = 0;

    @Inject(method = "tick", at = @At("TAIL"), remap = true)
    private void aggressiveAircraft$tickAutoRepair(CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (vehicle.level().isClientSide) {
            return;
        }

        aggressiveAircraft$repairTimer++;
        if (aggressiveAircraft$repairTimer < REPAIR_INTERVAL) {
            return;
        }
        aggressiveAircraft$repairTimer = 0;

        // 解析 "物品ID:点数" 列表（ID本身含冒号，按最后一个冒号分割）
        Map<String, Integer> repairItems = new HashMap<>();
        for (String entry : ModConfig.AIRCRAFT_REPAIR_ITEMS.get()) {
            int sep = entry.lastIndexOf(':');
            if (sep <= 0 || sep == entry.length() - 1) {
                continue; // 无效条目
            }
            try {
                int heal = Integer.parseInt(entry.substring(sep + 1));
                if (heal > 0) {
                    repairItems.put(entry.substring(0, sep), heal);
                }
            } catch (NumberFormatException ignored) {
                // 点数非数字，跳过
            }
        }
        if (repairItems.isEmpty() || vehicle.getHealth() >= 1.0f) {
            return; // 未配置或满血时不消耗物品
        }

        for (Entity passenger : vehicle.getPassengers()) {
            if (!(passenger instanceof Player player)) {
                continue;
            }
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < 9; slot++) { // 仅快捷栏
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                Integer heal = key != null ? repairItems.get(key.toString()) : null;
                if (heal == null || heal <= 0) {
                    continue;
                }
                // 消耗一个，恢复对应点数血量
                stack.shrink(1);
                float healAmount = heal / vehicle.getDurability() / Config.getInstance().damagePerHealthPoint;
                vehicle.setHealth(Math.min(1.0f, vehicle.getHealth() + healAmount));
                break; // 每位乘员每秒只消耗一个
            }
        }
    }
}
