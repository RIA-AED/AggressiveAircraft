package dev.ignis.aggressiveaircraft.weapons;

import dev.ignis.aggressiveaircraft.ModConfig;
import dev.ignis.aggressiveaircraft.entities.ModEntities;
import dev.ignis.aggressiveaircraft.utils.TargetSelector;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.entity.misc.WeaponMount;
import immersive_aircraft.cobalt.network.NetworkHandler;
import immersive_aircraft.entity.weapon.BulletWeapon;
import immersive_aircraft.network.c2s.FireMessage;
import immersive_aircraft.resources.bbmodel.BBAnimationVariables;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Map;

public class HomingRocketLauncher extends BulletWeapon {
    private static final double MAX_RANGE = 128.0;
    private static final double SEARCH_RADIUS = 32.0;
    private static final int LOCK_SCAN_INTERVAL = 5; // 每5tick扫描一次
    private static final float LOCK_HEALTH_THRESHOLD = 50.0f; // 血量阈值50
    private static final String TRACKED_TAG = "airstrikepointers:tracked";
    private static final SoundEvent LOCK_SOUND = SoundEvent.createVariableRangeEvent(
            ResourceLocation.tryBuild("minecraft", "block.note_block.xylophone"));

    private float cooldown = 0.0f;
    private LivingEntity lockedTarget = null;
    private boolean clientLockState = false; // 客户端锁定状态
    private int lockScanTimer = 0; // 扫描计时器

    public HomingRocketLauncher(VehicleEntity entity, ItemStack stack, WeaponMount mount, int slot) {
        super(entity, stack, mount, slot);
    }

    @Override
    protected Vector4f getBarrelOffset() {
        return new Vector4f(0.0f, 0.5f, 0.0f, 1.0f);
    }

    public float getVelocity() {
        return 1.8f;
    }

    public float getInaccuracy() {
        return 0.0f;
    }

    /**
     * 尝试锁定目标，返回是否成功
     * 在clientFire中调用，只有锁定成功才发送开火消息
     */
    public boolean tryLockTarget(Vector3f direction) {
        lockedTarget = findTarget(direction);
        return lockedTarget != null;
    }

    private LivingEntity findTarget(Vector3f direction) {
        Vec3 startPos = new Vec3(
            getEntity().getX(),
            getEntity().getY() + getEntity().getEyeHeight(),
            getEntity().getZ()
        );

        Vec3 endPos = startPos.add(
            direction.x * MAX_RANGE,
            direction.y * MAX_RANGE,
            direction.z * MAX_RANGE
        );

        BlockHitResult blockHit = getEntity().level().clip(
            new ClipContext(startPos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, getEntity())
        );

        Vec3 scanCenter;
        if (blockHit.getType() != HitResult.Type.MISS) {
            scanCenter = blockHit.getLocation();
        } else {
            scanCenter = startPos.add(
                direction.x * 40.0,
                direction.y * 40.0,
                direction.z * 40.0
            );
        }

        AABB searchBox = new AABB(
            scanCenter.x - 40.0, scanCenter.y - 40.0, scanCenter.z - 40.0,
            scanCenter.x + 40.0, scanCenter.y + 40.0, scanCenter.z + 40.0
        );

        List<LivingEntity> entities = getEntity().level().getEntitiesOfClass(
            LivingEntity.class,
            searchBox,
            this::isValidTarget
        );

        return TargetSelector.selectTarget(getEntity().level(), scanCenter, startPos, entities, getEntity());
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (!entity.isAlive() || !entity.isPickable()) {
            return false;
        }

        // 敌对生物判断
        if (entity.getMobType() == MobType.UNDEAD
            || entity.getMobType() == MobType.ARTHROPOD
            || entity.getMobType() == MobType.ILLAGER) {
            // 是敌对生物，继续检查
        } else if (entity.getType().getCategory().isFriendly()) {
            return false;
        }

        // 不能是发射者或其乘客
        if (entity.isPassengerOfSameVehicle(getEntity())) {
            return false;
        }

        return true;
    }

    @Override
    protected Entity getBullet(Entity shooter, Vector4f position, Vector3f direction) {
        var rocket = ModEntities.HOMING_ROCKET.get().create(shooter.level());
        assert rocket != null;

        rocket.setDamage(ModConfig.HOMING_ROCKET_DAMAGE.get());
        rocket.setExplosionPower(ModConfig.HOMING_ROCKET_EXPLOSION_POWER.get().floatValue());
        Entity pilot = getEntity().getControllingPassenger();
        rocket.setOwner(pilot != null ? pilot : shooter);
        rocket.setPos(position.x(), position.y(), position.z());

        // 服务端重新锁定目标（客户端锁定仅用于判断是否可发射）
        LivingEntity target = findTarget(direction);
        if (target != null && target.isAlive()) {
            rocket.setTarget(target);
        }

        rocket.shoot(direction.x(), direction.y(), direction.z(), getVelocity(), getInaccuracy());
        return rocket;
    }

    @Override
    public void tick() {
        float cooldownSeconds = ModConfig.HOMING_ROCKET_COOLDOWN.get().floatValue();
        cooldown -= 1.0f / 20.0f;
        if (cooldown < -cooldownSeconds) {
            cooldown = -cooldownSeconds;
        }

        // 客户端：每5tick扫描目标，播放锁定音效
        if (getEntity().level().isClientSide) {
            lockScanTimer++;
            if (lockScanTimer >= LOCK_SCAN_INTERVAL) {
                lockScanTimer = 0;
                LivingEntity target = findTarget(getDirection());
                clientLockState = target != null && target.getHealth() >= LOCK_HEALTH_THRESHOLD;
            }

            // 锁定状态下每tick播放音效
            if (clientLockState) {
                playLockSound();
            }
        }
    }

    private void playLockSound() {
        // 为所有乘客播放音效
        getEntity().getPassengers().forEach(passenger -> {
            if (passenger instanceof net.minecraft.world.entity.player.Player player) {
                player.playSound(LOCK_SOUND, 0.2f, 0.5f);
            }
        });
    }

    @Override
    public void fire(Vector3f direction) {
        String ammoId = ModConfig.HOMING_ROCKET_AMMO.get();
        int consumption = ModConfig.HOMING_ROCKET_AMMO_CONSUMPTION.get();
        if (spentAmmo(Map.of(ammoId, 1), consumption)) {
            super.fire(direction);
        }
    }

    @Override
    public void clientFire(int index) {
        float cooldownSeconds = ModConfig.HOMING_ROCKET_COOLDOWN.get().floatValue();
        if (cooldown > 0.0f) {
            return;
        }

        Vector3f direction = getDirection();

        // 总是开火，不再依赖锁定状态
        cooldown = cooldownSeconds;
        NetworkHandler.sendToServer(new FireMessage(getSlot(), index, direction));
    }

    private Vector3f getDirection() {
        Vector3f direction = new Vector3f(0, 0, 1.0f);
        direction.mul(new org.joml.Matrix3f(getMount().transform()));
        direction.mul(getEntity().getVehicleNormalTransform());
        return direction;
    }

    public float getCooldown() {
        float cooldownSeconds = ModConfig.HOMING_ROCKET_COOLDOWN.get().floatValue();
        return Math.max(0.0f, cooldown / cooldownSeconds);
    }

    @Override
    public <T extends VehicleEntity> void setAnimationVariables(T entity, float time) {
        super.setAnimationVariables(entity, time);
        BBAnimationVariables.set("weapon_ready", (float) (cooldown <= 0.0f ? 1.0f : 0.0f));
    }
}
