package dev.ignis.aggressiveaircraft.weapons;

import dev.ignis.aggressiveaircraft.ModConfig;
import dev.ignis.aggressiveaircraft.entities.ModEntities;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.entity.misc.WeaponMount;
import immersive_aircraft.cobalt.network.NetworkHandler;
import immersive_aircraft.entity.weapon.BulletWeapon;
import immersive_aircraft.network.c2s.FireMessage;
import immersive_aircraft.resources.bbmodel.BBAnimationVariables;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;

public class ClusterDispenser extends BulletWeapon {
    private float cooldown = 0.0f;

    public ClusterDispenser(VehicleEntity entity, ItemStack stack, WeaponMount mount, int slot) {
        super(entity, stack, mount, slot);
    }

    @Override
    protected Vector4f getBarrelOffset() {
        return new Vector4f(0.0f, -0.5f, 0.0f, 1.0f);
    }

    public float getVelocity() {
        return 0.0f; // 无初速度，完全继承飞机速度
    }

    public float getInaccuracy() {
        return 0.0f;
    }

    @Override
    protected Entity getBullet(Entity shooter, Vector4f position, Vector3f direction) {
        var dispenser = ModEntities.CLUSTER_DISPENSER.get().create(shooter.level());
        assert dispenser != null;

        dispenser.setOwner(shooter);
        dispenser.setPos(position.x(), position.y(), position.z());

        // 继承飞机速度
        Vec3 planeVel = shooter.getDeltaMovement();
        dispenser.setInheritedVelocity(planeVel);
        
        // 初始速度 = 飞机速度
        dispenser.setDeltaMovement(planeVel.x, planeVel.y, planeVel.z);

        return dispenser;
    }

    @Override
    public void tick() {
        float cooldownSeconds = ModConfig.CLUSTER_DISPENSER_COOLDOWN.get().floatValue();
        cooldown -= 1.0f / 20.0f;
        if (cooldown < -cooldownSeconds) {
            cooldown = -cooldownSeconds;
        }
    }

    @Override
    public void fire(Vector3f direction) {
        String ammoId = ModConfig.CLUSTER_DISPENSER_AMMO.get();
        int consumption = ModConfig.CLUSTER_DISPENSER_AMMO_CONSUMPTION.get();
        if (spentAmmo(Map.of(ammoId, 1), consumption)) {
            super.fire(direction);
        }
    }

    @Override
    public void clientFire(int index) {
        float cooldownSeconds = ModConfig.CLUSTER_DISPENSER_COOLDOWN.get().floatValue();
        if (cooldown > 0.0f) {
            return;
        }

        Vector3f direction = getDirection();
        cooldown = cooldownSeconds;
        NetworkHandler.sendToServer(new FireMessage(getSlot(), index, direction));
    }

    private Vector3f getDirection() {
        Vector3f direction = new Vector3f(0, 1.0f, 0); // 向下发射
        direction.mul(new org.joml.Matrix3f(getMount().transform()));
        direction.mul(getEntity().getVehicleNormalTransform());
        return direction;
    }

    public float getCooldown() {
        float cooldownSeconds = ModConfig.CLUSTER_DISPENSER_COOLDOWN.get().floatValue();
        return Math.max(0.0f, cooldown / cooldownSeconds);
    }

    @Override
    public <T extends VehicleEntity> void setAnimationVariables(T entity, float time) {
        super.setAnimationVariables(entity, time);
        BBAnimationVariables.set("weapon_ready", (float) (cooldown <= 0.0f ? 1.0f : 0.0f));
    }
}
