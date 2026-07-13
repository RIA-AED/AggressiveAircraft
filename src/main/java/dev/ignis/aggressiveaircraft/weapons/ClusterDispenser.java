package dev.ignis.aggressiveaircraft.weapons;

import dev.ignis.aggressiveaircraft.ModConfig;
import dev.ignis.aggressiveaircraft.entities.ModEntities;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.entity.misc.WeaponMount;
import immersive_aircraft.cobalt.network.NetworkHandler;
import immersive_aircraft.entity.weapon.BulletWeapon;
import immersive_aircraft.network.c2s.FireMessage;
import immersive_aircraft.resources.bbmodel.BBAnimationVariables;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;

public class ClusterDispenser extends BulletWeapon {
    private float cooldown = 0.0f;
    private final java.util.Random random = new java.util.Random();

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

        dispenser.setPos(position.x(), position.y(), position.z());

        Entity pilot = getEntity().getControllingPassenger();
        dispenser.setOwner(pilot != null ? pilot : shooter);

        // 获取飞机速度
        Vec3 planeVel = getEntity().getSpeedVector();
        
        // 设置继承的速度，用于后续飞行控制
        dispenser.setInheritedVelocity(planeVel);

        return dispenser;
    }
    
    @Override
    public void fire(Vector3f direction) {
        String ammoId = ModConfig.CLUSTER_DISPENSER_AMMO.get();
        int consumption = ModConfig.CLUSTER_DISPENSER_AMMO_CONSUMPTION.get();
        if (!spentAmmo(Map.of(ammoId, 1), consumption)) {
            return;
        }
        
        // 自定义发射逻辑，不调用 super.fire() 以避免速度叠加问题
        Vector4f position = getBarrelOffset();
        VehicleEntity entity = getEntity();
        position.mul(getMount().transform());
        position.mul(entity.getVehicleTransform());

        Vec3 speed = entity.getSpeedVector();

        // 生成实体
        Entity bullet = getBullet(entity, position, direction);
        // 设置速度为飞机速度（不叠加）
        bullet.setDeltaMovement(speed);
        entity.level().addFreshEntity(bullet);

        // 播放声音
        getEntity().playSound(getSound(), 1.0f, random.nextFloat() * 0.2f + 0.9f);
        
        // 发射时生成烟雾效果
        if (entity.level().isClientSide) {
            for (int i = 0; i < 8; i++) {
                double offsetX = (random.nextDouble() - 0.5) * 0.8;
                double offsetY = (random.nextDouble() - 0.5) * 0.8;
                double offsetZ = (random.nextDouble() - 0.5) * 0.8;
                entity.level().addParticle(
                    ParticleTypes.SMOKE,
                    position.x() + offsetX,
                    position.y() + offsetY,
                    position.z() + offsetZ,
                    speed.x * 0.1, speed.y * 0.1, speed.z * 0.1
                );
            }
            // 一些火焰粒子
            for (int i = 0; i < 3; i++) {
                double offsetX = (random.nextDouble() - 0.5) * 0.5;
                double offsetY = (random.nextDouble() - 0.5) * 0.5;
                double offsetZ = (random.nextDouble() - 0.5) * 0.5;
                entity.level().addParticle(
                    ParticleTypes.FLAME,
                    position.x() + offsetX,
                    position.y() + offsetY,
                    position.z() + offsetZ,
                    speed.x * 0.05, speed.y * 0.05, speed.z * 0.05
                );
            }
        }
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
