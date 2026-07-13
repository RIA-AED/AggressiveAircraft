package dev.ignis.aggressiveaircraft.weapons;

import dev.ignis.aggressiveaircraft.ModConfig;
import dev.ignis.aggressiveaircraft.entities.ExplosiveBulletEntity;
import immersive_aircraft.cobalt.network.NetworkHandler;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.entity.misc.WeaponMount;
import immersive_aircraft.entity.weapon.BulletWeapon;
import immersive_aircraft.entity.weapon.RotationalManager;
import immersive_aircraft.network.c2s.FireMessage;
import immersive_aircraft.resources.bbmodel.BBAnimationVariables;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;

import static dev.ignis.aggressiveaircraft.entities.ModEntities.EXPLOSIVE_BULLET;

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
        Entity pilot = getEntity().getControllingPassenger();
        bullet.setOwner(pilot != null ? pilot : shooter);
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
        if (spentAmmo(Map.of(ammoId, 1), consumption)) {
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

    @Override
    public <T extends VehicleEntity> void setAnimationVariables(T entity, float time) {
        super.setAnimationVariables(entity, time);

        float tickDelta = time % 1.0f;
        BBAnimationVariables.set("pitch", (float) (rotationalManager.getPitch(tickDelta) / Math.PI * 180.0f));
        BBAnimationVariables.set("yaw", (float) (rotationalManager.getYaw(tickDelta) / Math.PI * 180.0f));
        BBAnimationVariables.set("roll", (float) (rotationalManager.getRoll(tickDelta) / Math.PI * 180.0f));
    }
}
