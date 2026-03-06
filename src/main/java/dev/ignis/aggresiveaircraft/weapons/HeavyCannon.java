package dev.ignis.aggresiveaircraft.weapons;

import dev.ignis.aggresiveaircraft.ModConfig;
import dev.ignis.aggresiveaircraft.entities.ExplosiveBulletEntity;
import immersive_aircraft.cobalt.network.NetworkHandler;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.entity.misc.WeaponMount;
import immersive_aircraft.entity.weapon.BulletWeapon;
import immersive_aircraft.entity.weapon.RotationalManager;
import immersive_aircraft.network.c2s.FireMessage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;

import static dev.ignis.aggresiveaircraft.entities.ModEntities.EXPLOSIVE_BULLET;

public class HeavyCannon extends BulletWeapon {
    private final RotationalManager rotationalManager = new RotationalManager(this);
    private float fireAccumulator = 0.0f;

    public HeavyCannon(VehicleEntity entity, ItemStack stack, WeaponMount mount, int slot) {
        super(entity, stack, mount, slot);
    }

    @Override
    protected Vector4f getBarrelOffset() {
        return new Vector4f(0.0f, 0.825f, -0.375f, 1.0f);
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
        bullet.setDamage(ModConfig.HEAVY_CANNON_DAMAGE.get());
        bullet.setExplosionPower(ModConfig.HEAVY_CANNON_EXPLOSION_POWER.get());
        bullet.setDestroyBlocks(ModConfig.HEAVY_CANNON_DESTROY_BLOCKS.get());
        bullet.setOwner(shooter);
        bullet.setPos(position.x(), position.y(), position.z());
        bullet.shoot(direction.x(), direction.y(), direction.z(), getVelocity(), getInaccuracy());
        return bullet;
    }

    @Override
    public void tick() {
        rotationalManager.tick();
        rotationalManager.pointTo(getEntity());
    }

    @Override
    public void fire(Vector3f direction) {
        String ammoId = ModConfig.HEAVY_CANNON_AMMO.get();
        int consumption = ModConfig.HEAVY_CANNON_AMMO_CONSUMPTION.get();
        if (spentAmmo(Map.of(ammoId, 100), consumption)) {
            super.fire(direction);
        }
    }

    private Vector3f getDirection() {
        return rotationalManager.screenToGlobal(getEntity());
    }

    @Override
    public void clientFire(int index) {
        // 射速是每秒发射次数，所以间隔 = 1/射速（秒）
        float fireIntervalSeconds = 1.0f / (float)(double)ModConfig.HEAVY_CANNON_FIRE_RATE.get();
        // 每tick增加的时间（1tick = 1/20秒）
        float tickDelta = 1.0f / 20.0f;
        fireAccumulator += tickDelta;

        if (fireAccumulator >= fireIntervalSeconds) {
            fireAccumulator = 0;
            NetworkHandler.sendToServer(new FireMessage(getSlot(), index, getDirection()));
        }
    }
}
