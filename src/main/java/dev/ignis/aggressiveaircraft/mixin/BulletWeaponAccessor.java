package dev.ignis.aggressiveaircraft.mixin;

import immersive_aircraft.entity.weapon.BulletWeapon;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BulletWeapon.class)
public interface BulletWeaponAccessor {
    @Accessor(remap = false)
    int getAmmo();

    @Accessor(remap = false)
    void setAmmo(int ammo);

    @Accessor(remap = false)
    ItemStack getAmmoStack();
}
