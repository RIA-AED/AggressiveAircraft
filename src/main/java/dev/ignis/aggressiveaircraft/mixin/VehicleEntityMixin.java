package dev.ignis.aggressiveaircraft.mixin;

import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.List;

/**
 * 修复 getGunner 中炮塔乘员分配颠倒的 bug。
 *
 * 背景：InventoryVehicleEntity.tick() 中 gunnerOffset 的赋值逻辑为：
 *   武器1 → gunnerOffset = passengers.size() - 1（即最后一个乘客的索引）
 *   武器2 → gunnerOffset = passengers.size() - 2（即倒数第二个乘客的索引）
 *   对应注释："The first weapon is assigned to the last passenger, the second to the second last"
 * clientFireWeapons 直接比较 gunnerOffset == passengerIndex，因此开火分配是正确的。
 *
 * 但是 getGunner 使用了公式 passengers.size() - 1 - offset，这导致：
 *   getGunner(1) = passengers[0]  → 武器1由乘客0（驾驶员）瞄准，而非乘客1（枪手）
 *   getGunner(0) = passengers[1]  → 武器2由乘客1（枪手）瞄准，而非乘客0（驾驶员）
 * 这让炮塔类武器（RotaryCannon 等通过 RotationalManager 使用 getGunner 获取瞄准者）的朝向被交换。
 *
 * 修复：getGunner 应直接使用 offset 作为乘客索引，与 clientFireWeapons 保持一致。
 */
@Mixin(VehicleEntity.class)
public class VehicleEntityMixin {

    /**
     * @author AggressiveAircraft
     * @reason 修复多乘员时炮塔武器朝向分配到错误的乘员
     */
    @Overwrite(remap = false)
    @Nullable
    public Entity getGunner(int offset) {
        List<Entity> passengers = ((VehicleEntity) (Object) this).getPassengers();
        if (passengers.isEmpty()) {
            return null;
        }
        int index = Math.min(Math.max(0, offset), passengers.size() - 1);
        return passengers.get(index);
    }
}
