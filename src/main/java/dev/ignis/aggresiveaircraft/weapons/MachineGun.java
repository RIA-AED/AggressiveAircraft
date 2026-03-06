package dev.ignis.aggresiveaircraft.weapons;

import dev.ignis.aggresiveaircraft.ModConfig;
import immersive_aircraft.cobalt.network.NetworkHandler;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.entity.bullet.BulletEntity;
import immersive_aircraft.entity.misc.WeaponMount;
import immersive_aircraft.entity.weapon.BulletWeapon;
import immersive_aircraft.network.c2s.FireMessage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;

import static immersive_aircraft.Entities.BULLET;

public class MachineGun extends BulletWeapon {
    private float fireAccumulator = 0.0f;

    public MachineGun(VehicleEntity entity, ItemStack stack, WeaponMount mount, int slot) {
        super(entity, stack, mount, slot);
    }

    @Override
    protected float getBarrelLength() {
        return 1.25f;
    }

    @Override
    protected Vector4f getBarrelOffset() {
        return new Vector4f(0.0f, 0.3f, 0.0f, 1.0f);
    }

    public float getVelocity() {
        return 4.0f;
    }

    public float getInaccuracy() {
        return 0.5f;
    }

    @Override
    protected Entity getBullet(Entity shooter, Vector4f position, Vector3f direction) {
        BulletEntity bullet = BULLET.get().create(shooter.level());
        assert bullet != null;
        bullet.setDamage(ModConfig.MACHINE_GUN_DAMAGE.get());
        bullet.setPos(position.x(), position.y(), position.z());
        bullet.setOwner(shooter);
        bullet.shoot(direction.x(), direction.y(), direction.z(), getVelocity(), getInaccuracy());
        return bullet;
    }

    @Override
    public void tick() {
        // 使用accumulator模式在clientFire中处理射速
    }

    @Override
    public void fire(Vector3f direction) {
        String ammoId = ModConfig.MACHINE_GUN_AMMO.get();
        int consumption = ModConfig.MACHINE_GUN_AMMO_CONSUMPTION.get();
        if (spentAmmo(Map.of(ammoId, 100), consumption)) {
            super.fire(direction);
        }
    }

    @Override
    public void clientFire(int index) {
        // 射速是每秒发射次数，所以间隔 = 1/射速（秒）
        float fireIntervalSeconds = 1.0f / (float)(double)ModConfig.MACHINE_GUN_FIRE_RATE.get();
        // 每tick增加的时间（1tick = 1/20秒）
        float tickDelta = 1.0f / 20.0f;
        fireAccumulator += tickDelta;

        if (fireAccumulator >= fireIntervalSeconds) {
            fireAccumulator = 0;
            NetworkHandler.sendToServer(new FireMessage(getSlot(), index, getDirection()));
        }
    }

    private Vector3f getDirection() {
        Vector3f direction = new Vector3f(0, 0, 1.0f);
        direction.mul(new Matrix3f(getMount().transform()));
        direction.mul(getEntity().getVehicleNormalTransform());
        return direction;
    }

    public float getCooldown() {
        float fireIntervalSeconds = 1.0f / (float)(double)ModConfig.MACHINE_GUN_FIRE_RATE.get();
        return Math.max(0.0f, 1.0f - fireAccumulator / fireIntervalSeconds);
    }
}
