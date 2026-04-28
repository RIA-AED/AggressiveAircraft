package dev.ignis.aggressiveaircraft.weapons;

import dev.ignis.aggressiveaircraft.ModConfig;
import dev.ignis.aggressiveaircraft.entities.ModEntities;
import dev.ignis.aggressiveaircraft.entities.RocketPodRocketEntity;
import immersive_aircraft.cobalt.network.NetworkHandler;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.entity.misc.WeaponMount;
import immersive_aircraft.entity.weapon.BulletWeapon;
import immersive_aircraft.network.c2s.FireMessage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;

public class RocketPod extends BulletWeapon {
    private float fireAccumulator = 0.0f;

    public RocketPod(VehicleEntity entity, ItemStack stack, WeaponMount mount, int slot) {
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
        return 2.0f; // 初速度较低，动力段内会逐渐加速
    }

    public float getInaccuracy() {
        return 2.0f;
    }

    @Override
    protected Entity getBullet(Entity shooter, Vector4f position, Vector3f direction) {
        RocketPodRocketEntity rocket = ModEntities.ROCKET_POD_ROCKET.get().create(shooter.level());
        assert rocket != null;
        rocket.setDamage(ModConfig.ROCKET_POD_DAMAGE.get());
        rocket.setExplosionPower(ModConfig.ROCKET_POD_EXPLOSION_POWER.get().floatValue());
        rocket.setDestroyBlocks(ModConfig.ROCKET_POD_DESTROY_BLOCKS.get());
        rocket.setOwner(shooter);
        rocket.setPos(position.x(), position.y(), position.z());

        // 继承载具速度
        Vec3 vehicleSpeed = getEntity().getSpeedVector();
        rocket.setInheritedVelocity(vehicleSpeed);

        rocket.shoot(direction.x(), direction.y(), direction.z(), getVelocity(), getInaccuracy());
        return rocket;
    }

    @Override
    public void tick() {
        // 使用accumulator模式在clientFire中处理射速
    }

    @Override
    public void fire(Vector3f direction) {
        String ammoId = ModConfig.ROCKET_POD_AMMO.get();
        int consumption = ModConfig.ROCKET_POD_AMMO_CONSUMPTION.get();
        if (spentAmmo(Map.of(ammoId, 1), consumption)) {
            super.fire(direction);
        }
    }

    @Override
    public void clientFire(int index) {
        float fireIntervalSeconds = 1.0f / (float)(double) ModConfig.ROCKET_POD_FIRE_RATE.get();

        // 先检查是否可以发射（首次或CD已好）
        if (fireAccumulator <= 0.0f) {
            // 先发射
            NetworkHandler.sendToServer(new FireMessage(getSlot(), index, getDirection()));
            // 再进入CD
            fireAccumulator = fireIntervalSeconds;
        }

        // 每tick减少CD时间（1tick = 1/20秒）
        float tickDelta = 1.0f / 20.0f;
        fireAccumulator -= tickDelta;
    }

    private Vector3f getDirection() {
        Vector3f direction = new Vector3f(0, 0, 1.0f);
        direction.mul(new Matrix3f(getMount().transform()));
        direction.mul(getEntity().getVehicleNormalTransform());
        return direction;
    }

    public float getCooldown() {
        float fireIntervalSeconds = 1.0f / (float)(double) ModConfig.ROCKET_POD_FIRE_RATE.get();
        return Math.max(0.0f, 1.0f - fireAccumulator / fireIntervalSeconds);
    }
}
