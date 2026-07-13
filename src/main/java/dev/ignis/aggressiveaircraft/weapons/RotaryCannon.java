package dev.ignis.aggressiveaircraft.weapons;

import dev.ignis.aggressiveaircraft.ModConfig;
import dev.ignis.aggressiveaircraft.entities.ExplosiveBulletEntity;
import immersive_aircraft.cobalt.network.NetworkHandler;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.entity.misc.WeaponMount;
import immersive_aircraft.entity.weapon.BulletWeapon;
import immersive_aircraft.network.c2s.FireMessage;
import immersive_aircraft.resources.bbmodel.BBAnimationVariables;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;

import static dev.ignis.aggressiveaircraft.entities.ModEntities.EXPLOSIVE_BULLET;

public class RotaryCannon extends BulletWeapon {
    private float fireAccumulator = 0.0f;

    public RotaryCannon(VehicleEntity entity, ItemStack stack, WeaponMount mount, int slot) {
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
        return 1.0f;
    }

    @Override
    protected Entity getBullet(Entity shooter, Vector4f position, Vector3f direction) {
        ExplosiveBulletEntity bullet = EXPLOSIVE_BULLET.get().create(shooter.level());
        assert bullet != null;
        bullet.setDamage(ModConfig.ROTARY_CANNON_DAMAGE.get());
        bullet.setExplosionPower(ModConfig.ROTARY_CANNON_EXPLOSION_POWER.get());
        bullet.setDestroyBlocks(ModConfig.ROTARY_CANNON_DESTROY_BLOCKS.get());
        Entity pilot = getEntity().getControllingPassenger();
        bullet.setOwner(pilot != null ? pilot : shooter);
        bullet.setPos(position.x(), position.y(), position.z());
        bullet.shoot(direction.x(), direction.y(), direction.z(), getVelocity(), getInaccuracy());
        return bullet;
    }

    @Override
    public void tick() {
        // 使用accumulator模式在clientFire中处理射速
    }

    @Override
    public void fire(Vector3f direction) {
        String ammoId = ModConfig.ROTARY_CANNON_AMMO.get();
        int consumption = ModConfig.ROTARY_CANNON_AMMO_CONSUMPTION.get();
        if (spentAmmo(Map.of(ammoId, 1), consumption)) {
            super.fire(direction);
        }
    }

    @Override
    public void clientFire(int index) {
        float fireIntervalSeconds = 1.0f / (float)(double) ModConfig.ROTARY_CANNON_FIRE_RATE.get();

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
        float fireIntervalSeconds = 1.0f / (float)(double) ModConfig.ROTARY_CANNON_FIRE_RATE.get();
        return Math.max(0.0f, 1.0f - fireAccumulator / fireIntervalSeconds);
    }

    @Override
    public <T extends VehicleEntity> void setAnimationVariables(T entity, float time) {
        super.setAnimationVariables(entity, time);

        // rotary_progress: 在两次开火之间从0到1变化
        // fireAccumulator 从 fireIntervalSeconds 递减到 0，因此 progress = 1 - (accumulator / interval)
        float fireIntervalSeconds = 1.0f / (float)(double) ModConfig.ROTARY_CANNON_FIRE_RATE.get();
        float progress;
        if (fireAccumulator <= 0.0f) {
            progress = 1.0f;
        } else {
            progress = 1.0f - (fireAccumulator / fireIntervalSeconds);
        }
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        BBAnimationVariables.set("rotary_progress", progress);
    }
}
