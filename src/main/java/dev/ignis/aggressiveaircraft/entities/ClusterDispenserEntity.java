package dev.ignis.aggressiveaircraft.entities;

import dev.ignis.aggressiveaircraft.ModConfig;
import dev.ignis.aggressiveaircraft.combat.Enemy;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class ClusterDispenserEntity extends AbstractHurtingProjectile {
    private static final double MAX_LIFETIME = 60; // 3秒 = 60 ticks（1秒飞行 + 2秒布撒）
    private static final double DESCENT_START_TIME = 20; // 1秒后开始转为水平
    private static final double FIRE_START_TIME = 20; // 1秒后开始布撒
    private static final double FIRE_END_TIME = 60; // 3秒后结束布撒（布撒持续2秒）
    private static final double FIRE_INTERVAL = 1.0; // 每tick发射1发
    private static final double HORIZONTAL_SPEED = 2.0; // 水平飞行速度

    private static final double MIN_ATTACK_RADIUS = 8.0; // 攻击范围下限：AABB半宽
    private static final double MAX_ATTACK_RADIUS = 24.0; // 攻击范围上限：AABB半宽（对应离地48格）
    private static final double MAX_LOCK_ALTITUDE = 48.0; // 发射点离地超过此高度不锁定（子弹药够不到目标）
    private static final int SAMPLE_INTERVAL = 4; // 沿飞行路径的AABB采样间隔（tick）

    private int lifetime = 0;
    private double fireTimer = 0;
    private boolean hasFired = false;
    private Vec3 inheritedVelocity = Vec3.ZERO;

    // 发射时按血量计算的目标份额（UUID -> 血量占比），之后固定不变
    private Map<UUID, Float> targetShares = new HashMap<>();
    
    private final Random random = new Random();

    public ClusterDispenserEntity(EntityType<? extends ClusterDispenserEntity> entityType, Level level) {
        super(entityType, level);
    }

    public void setInheritedVelocity(Vec3 velocity) {
        this.inheritedVelocity = velocity;
    }

    /**
     * 发射时指定攻击目标及其血量份额。份额固定不变，子弹药布撒时按其加权分配。
     */
    public void setTargetShares(Map<UUID, Float> targetShares) {
        this.targetShares = targetShares;
    }

    /**
     * 发射时索敌：沿预测飞行路径，对底下的地面用多个AABB进行判定，
     * 收集攻击范围内且为敌对目标（实现Enemy接口或敌对类别生物）的实体。
     * 发射点离地超过 {@link #MAX_LOCK_ALTITUDE} 格时不锁定。
     * 与 {@link #updateFlightPath()} 使用相同的飞行模型逐tick推演位置。
     */
    public static List<LivingEntity> selectTargets(Level level, Vec3 startPos, Vec3 initialVelocity) {
        // 离地超过48格不锁定：子弹药下落途中速度被阻尼衰减，够不到更远的目标
        int startGroundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(startPos.x), Mth.floor(startPos.z));
        if (startPos.y - startGroundY > MAX_LOCK_ALTITUDE) {
            return List.of();
        }

        Set<UUID> seen = new HashSet<>();
        List<LivingEntity> targets = new ArrayList<>();

        Vec3 pos = startPos;
        Vec3 vel = initialVelocity;
        collect(level, pos, seen, targets);
        for (int tick = 1; tick <= MAX_LIFETIME; tick++) {
            pos = pos.add(vel);
            vel = flightStep(tick, vel, initialVelocity);
            if (tick % SAMPLE_INTERVAL == 0) {
                collect(level, pos, seen, targets);
            }
        }
        return targets;
    }

    private static void collect(Level level, Vec3 pos, Set<UUID> seen, List<LivingEntity> targets) {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
        // 索敌半宽随离地高度自适应：高度16格→8，48格→24，线性插值，夹取在[8,24]
        double altitude = pos.y - groundY;
        double radius = Math.max(MIN_ATTACK_RADIUS, Math.min(MAX_ATTACK_RADIUS, altitude * 0.5));
        // AABB 从地面延伸到飞行高度，符合飞行路径的走廊式攻击范围
        AABB box = new AABB(
                pos.x - radius, groundY, pos.z - radius,
                pos.x + radius, pos.y + 2.0, pos.z + radius
        );
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                ClusterDispenserEntity::isValidClusterTarget)) {
            if (seen.add(entity.getUUID())) {
                targets.add(entity);
            }
        }
    }

    private static boolean isValidClusterTarget(LivingEntity entity) {
        if (!entity.isAlive() || !entity.isPickable() || entity.isSpectator()) {
            return false;
        }
        // 仅限敌对目标：实现 Enemy 接口或敌对类别生物（不锁定玩家）
        if (!(entity instanceof Enemy) && !Enemy.isHostile(entity)) {
            return false;
        }
        // 不锁定任何载具上的乘客（含发射飞机的乘员与任何飞机上的乘客）
        return entity.getVehicle() == null;
    }

    /**
     * 与 {@link #updateFlightPath()} 相同的飞行推演单步，用于发射时预测弹道。
     */
    private static Vec3 flightStep(int lifetime, Vec3 currentVel, Vec3 inheritedVelocity) {
        if (lifetime < DESCENT_START_TIME) {
            // 前1秒：垂直速度衰减30%，趋于水平
            double progress = lifetime / DESCENT_START_TIME;
            double targetY = currentVel.y * (1.0 - progress * 0.3);
            return new Vec3(currentVel.x, targetY, currentVel.z);
        } else {
            // 后3秒：水平飞行 + 轻微下降
            double horizontalProgress = Math.min(1.0, (lifetime - DESCENT_START_TIME) / 20.0);
            Vec3 horizontalDir = new Vec3(inheritedVelocity.x, 0, inheritedVelocity.z);
            if (horizontalDir.lengthSqr() < 0.001) {
                horizontalDir = new Vec3(0, 0, 1);
            } else {
                horizontalDir = horizontalDir.normalize();
            }
            double targetX = horizontalDir.x * HORIZONTAL_SPEED;
            double targetZ = horizontalDir.z * HORIZONTAL_SPEED;
            double targetY = -0.1 * horizontalProgress;
            return new Vec3(
                    currentVel.x + (targetX - currentVel.x) * 0.1,
                    currentVel.y + (targetY - currentVel.y) * 0.1,
                    currentVel.z + (targetZ - currentVel.z) * 0.1
            );
        }
    }

    @Override
    public void tick() {
        // 先调用父类tick处理运动（客户端和服务端都需要）
        super.tick();

        if (this.level().isClientSide) {
            // 烟雾尾迹
            for (int i = 0; i < 3; i++) {
                double offsetX = (this.random.nextDouble() - 0.5) * 0.5;
                double offsetY = (this.random.nextDouble() - 0.5) * 0.5;
                double offsetZ = (this.random.nextDouble() - 0.5) * 0.5;
                this.level().addParticle(
                    ParticleTypes.SMOKE,
                    this.getX() + offsetX,
                    this.getY() + offsetY,
                    this.getZ() + offsetZ,
                    0.0, 0.02, 0.0
                );
            }
            
            // 客户端：只在刚创建时同步载具角度（避免初始朝向错误）
            if (this.tickCount < 5) {
                syncRotationFromVehicle();
            }
            
            return;
        }

        // 服务端逻辑
        lifetime++;

        // 超过5秒爆炸
        if (lifetime > MAX_LIFETIME) {
            explode();
            this.discard();
            return;
        }

        // 飞行轨迹控制 - 在父类tick之后设置速度，确保下一tick使用新速度
        updateFlightPath();

        // 发射子弹阶段（后3秒）
        if (lifetime >= FIRE_START_TIME) {
            fireTimer += 1.0;
            while (fireTimer >= FIRE_INTERVAL) {
                fireTimer -= FIRE_INTERVAL;
                fireBullet();
            }
        }
    }

    private void updateFlightPath() {
        Vec3 currentVel = this.getDeltaMovement();

        if (lifetime < DESCENT_START_TIME) {
            // 前1秒：保持当前速度（从BulletWeapon.fire()继承的飞机速度）
            // 逐渐减小垂直速度，让布撒器趋于水平
            double progress = lifetime / DESCENT_START_TIME;
            // 使用当前速度作为基础，而不是inheritedVelocity
            // 这样可以确保方向正确
            double targetY = currentVel.y * (1.0 - progress * 0.3); // 垂直速度衰减30%
            this.setDeltaMovement(
                currentVel.x,
                targetY,
                currentVel.z
            );
        } else {
            // 后3秒：转为水平飞行
            double horizontalProgress = Math.min(1.0, (lifetime - DESCENT_START_TIME) / 20.0);

            // 水平方向继承原速度方向，但大小趋近恒定
            Vec3 horizontalDir = new Vec3(inheritedVelocity.x, 0, inheritedVelocity.z).normalize();
            if (horizontalDir.lengthSqr() < 0.001) {
                horizontalDir = new Vec3(0, 0, 1);
            }

            double targetX = horizontalDir.x * HORIZONTAL_SPEED;
            double targetZ = horizontalDir.z * HORIZONTAL_SPEED;
            double targetY = -0.1 * horizontalProgress; // 轻微下降

            // 平滑过渡
            this.setDeltaMovement(
                currentVel.x + (targetX - currentVel.x) * 0.1,
                currentVel.y + (targetY - currentVel.y) * 0.1,
                currentVel.z + (targetZ - currentVel.z) * 0.1
            );
        }
    }

    private void fireBullet() {
        var bomblet = ModEntities.CLUSTER_BOMBLET.get().create(this.level());
        if (bomblet == null) return;

        // Read config values
        bomblet.setDamage(ModConfig.CLUSTER_DISPENSER_BOMBLET_HIT_DAMAGE.get());
        bomblet.setHitCount(ModConfig.CLUSTER_DISPENSER_BOMBLET_HIT_COUNT.get());
        bomblet.setOwner(this.getOwner());
        bomblet.setPos(this.getX(), this.getY(), this.getZ());

        // 弱制导：按发射时指定的血量份额，加权随机分配制导目标
        LivingEntity guidedTarget = pickTargetByShare();
        bomblet.setTarget(guidedTarget);

        // 发射方向：有目标时大致朝向目标（带明显随机偏差），无目标时垂直下落
        double speed = 2.0 + random.nextDouble() * 0.25;
        Vec3 dir;
        if (guidedTarget != null) {
            Vec3 toTarget = guidedTarget.getBoundingBox().getCenter().subtract(this.position());
            double yaw = Math.atan2(toTarget.x, toTarget.z);
            double horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
            double pitch = Math.atan2(toTarget.y, horizontal);
            if (pitch > -0.2) {
                pitch = -0.2; // 钳制俯仰角，保证只向下打，不会朝上飞回飞机
            }
            // 明显的随机偏差：偏航/俯仰各 ±0.5 弧度（约±29°）
            double spread = 0.5;
            yaw += (random.nextDouble() - 0.5) * 2.0 * spread;
            pitch += (random.nextDouble() - 0.5) * 2.0 * spread;
            dir = new Vec3(
                    Math.cos(pitch) * Math.sin(yaw),
                    Math.sin(pitch),
                    Math.cos(pitch) * Math.cos(yaw)
            );
        } else {
            dir = new Vec3(0, -1, 0);
        }

        bomblet.shoot(dir.x, dir.y, dir.z, (float) speed, 0.0f);
        this.level().addFreshEntity(bomblet);
        
        // 播放拾起物品的声音，音量2.0（更大声）
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 2.0f, 1.0f);
    }

    /**
     * 按发射时指定的血量份额加权随机选取制导目标。
     * 血量越高的目标分到的子弹药越多，即"按血量平分给选中的目标"。
     * 目标已死亡/消失时自动剔除；无有效目标返回null（子弹药自由下落）。
     * 仅在服务端tick中调用，level() 必为 ServerLevel。
     */
    private LivingEntity pickTargetByShare() {
        if (targetShares.isEmpty()) {
            return null;
        }
        ServerLevel serverLevel = (ServerLevel) this.level();
        List<Map.Entry<UUID, Float>> valid = new ArrayList<>();
        double total = 0.0;
        for (Map.Entry<UUID, Float> entry : targetShares.entrySet()) {
            Entity entity = serverLevel.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living && living.isAlive() && living.getVehicle() == null) {
                valid.add(entry);
                total += entry.getValue();
            }
        }
        if (valid.isEmpty()) {
            return null;
        }

        double roll = random.nextDouble() * total;
        double acc = 0.0;
        for (Map.Entry<UUID, Float> entry : valid) {
            acc += entry.getValue();
            if (roll < acc) {
                return (LivingEntity) serverLevel.getEntity(entry.getKey());
            }
        }
        return (LivingEntity) serverLevel.getEntity(valid.get(valid.size() - 1).getKey());
    }

    private void explode() {
        if (!this.level().isClientSide) {
            this.level().explode(
                this.getOwner() != null ? this.getOwner() : this,
                this.getX(),
                this.getY(),
                this.getZ(),
                2.0f,
                Level.ExplosionInteraction.NONE
            );
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        explode();
        this.discard();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            explode();
            this.discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (target.isSpectator() || !target.isAlive() || !target.isPickable()) {
            return false;
        }
        // 不撞发射飞机及其乘员（飞机速度大于布撒器时会追上来撞上机身导致贴脸爆炸）
        Entity owner = this.getOwner();
        if (owner == null) {
            return true;
        }
        if (target == owner) {
            return false;
        }
        Entity vehicle = owner.getVehicle();
        if (vehicle == null) {
            return true;
        }
        return target != vehicle && !target.isPassengerOfSameVehicle(vehicle);
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    protected void pushEntities() {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Lifetime", lifetime);
        tag.putDouble("InheritedVelX", inheritedVelocity.x);
        tag.putDouble("InheritedVelY", inheritedVelocity.y);
        tag.putDouble("InheritedVelZ", inheritedVelocity.z);
        ListTag shares = new ListTag();
        for (Map.Entry<UUID, Float> entry : targetShares.entrySet()) {
            CompoundTag target = new CompoundTag();
            target.putUUID("Target", entry.getKey());
            target.putFloat("Share", entry.getValue());
            shares.add(target);
        }
        tag.put("TargetShares", shares);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Lifetime")) {
            lifetime = tag.getInt("Lifetime");
        }
        if (tag.contains("InheritedVelX")) {
            inheritedVelocity = new Vec3(
                tag.getDouble("InheritedVelX"),
                tag.getDouble("InheritedVelY"),
                tag.getDouble("InheritedVelZ")
            );
        }
        targetShares.clear();
        if (tag.contains("TargetShares", Tag.TAG_LIST)) {
            ListTag shares = tag.getList("TargetShares", Tag.TAG_COMPOUND);
            for (int i = 0; i < shares.size(); i++) {
                CompoundTag target = shares.getCompound(i);
                targetShares.put(target.getUUID("Target"), target.getFloat("Share"));
            }
        }
    }

    public float getRoll(float tickDelta) {
        return 0.0f; // 布撒器无滚动
    }
    
    /**
     * 从载具同步旋转角度（用于初始朝向）
     */
    private void syncRotationFromVehicle() {
        Entity owner = this.getOwner();
        if (owner == null) return;
        
        // 获取玩家乘坐的载具
        Entity vehicle = owner.getVehicle();
        if (vehicle == null) return;
        
        // 同步载具的旋转角度
        this.setYRot(vehicle.getYRot());
        this.setXRot(vehicle.getXRot());
        this.yRotO = vehicle.getYRot();
        this.xRotO = vehicle.getXRot();
    }
    
    /**
     * 根据速度向量更新实体旋转
     */
    private void updateRotation(Vec3 velocity) {
        // 计算水平方向角度（yaw）
        double horizontalDist = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(velocity.x, velocity.z)));
        float pitch = (float) (Math.toDegrees(Math.atan2(velocity.y, horizontalDist)));
        
        // 设置旋转
        this.setYRot(yaw);
        this.setXRot(pitch);
        
        // 同步到旧系统
        this.yRotO = yaw;
        this.xRotO = pitch;
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        // 覆盖默认的插值运动，直接使用服务器速度
        this.setDeltaMovement(x, y, z);
    }
}
