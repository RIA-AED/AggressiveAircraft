package dev.ignis.aggressiveaircraft.weapons;

import dev.ignis.aggressiveaircraft.ModConfig;
import dev.ignis.aggressiveaircraft.entities.HeavyBombEntity;
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

import static dev.ignis.aggressiveaircraft.entities.ModEntities.HEAVY_BOMB;

public class HeavyBombBay extends BulletWeapon {
    private float cooldown = 0.0f;

    public HeavyBombBay(VehicleEntity entity, ItemStack stack, WeaponMount mount, int slot) {
        super(entity, stack, mount, slot);
    }

    @Override
    protected float getBarrelLength() {
        return 0.25f;
    }

    @Override
    protected Vector4f getBarrelOffset() {
        return new Vector4f(0.0f, -0.8f, 0.0f, 1.0f);
    }

    public float getVelocity() {
        return 0.5f;
    }

    @Override
    protected Entity getBullet(Entity shooter, Vector4f position, Vector3f direction) {
        HeavyBombEntity bomb = HEAVY_BOMB.get().create(shooter.level());
        assert bomb != null;
        bomb.setExplosionPower(ModConfig.HEAVY_BOMB_BAY_EXPLOSION_POWER.get());
        bomb.setDestroyBlocks(ModConfig.HEAVY_BOMB_BAY_DESTROY_BLOCKS.get());
        // PrimedTnt doesn't have setOwner, owner is set via NBT or EntityType.create
        bomb.setPos(position.x(), position.y(), position.z());
        
        // Add plane's velocity to bomb
        Vector3f vel = direction.mul(getVelocity(), new Vector3f());
        bomb.setDeltaMovement(vel.x(), vel.y(), vel.z());
        
        return bomb;
    }

    @Override
    public void tick() {
        float cooldownSeconds = (float)(double)ModConfig.HEAVY_BOMB_BAY_COOLDOWN.get();
        cooldown -= 1.0f / 20.0f;
        if (cooldown < -cooldownSeconds) {
            cooldown = -cooldownSeconds;
        }
    }

    @Override
    public void fire(Vector3f direction) {
        String ammoId = ModConfig.HEAVY_BOMB_BAY_AMMO.get();
        int consumption = ModConfig.HEAVY_BOMB_BAY_AMMO_CONSUMPTION.get();
        if (spentAmmo(Map.of(ammoId, 100), consumption)) {
            super.fire(direction);
        }
    }

    @Override
    public void clientFire(int index) {
        float cooldownSeconds = (float)(double)ModConfig.HEAVY_BOMB_BAY_COOLDOWN.get();
        if (cooldown <= 0.0f) {
            cooldown = cooldownSeconds;
            NetworkHandler.sendToServer(new FireMessage(getSlot(), index, getDirection()));
        }
    }

    private Vector3f getDirection() {
        Vector3f direction = new Vector3f(0, 1.0f, 0);
        direction.mul(new Matrix3f(getMount().transform()));
        direction.mul(getEntity().getVehicleNormalTransform());
        return direction;
    }

    public float getCooldown() {
        float cooldownSeconds = (float)(double)ModConfig.HEAVY_BOMB_BAY_COOLDOWN.get();
        return Math.max(0.0f, cooldown / cooldownSeconds);
    }

    @Override
    public <T extends VehicleEntity> void setAnimationVariables(T entity, float time) {
        super.setAnimationVariables(entity, time);
        BBAnimationVariables.set("weapon_ready", (float) (cooldown <= 0.0f ? 1.0f : 0.0f));
    }
}
