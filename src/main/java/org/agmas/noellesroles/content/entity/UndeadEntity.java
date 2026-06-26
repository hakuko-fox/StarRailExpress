package org.agmas.noellesroles.content.entity;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 亡灵实体（亡灵之主召唤的无意识亡灵）。
 *
 * <p>特性：
 * <ul>
 *   <li>使用死者生前的皮肤外观，周身环绕淡紫色雾气。</li>
 *   <li>未发现目标时在召唤点附近随机徘徊；发现 15 格内活人后直线追击。</li>
 *   <li>攻击不造成伤害，而是每 1.5 秒为目标增加感染值（由亡灵之主组件统一结算）。</li>
 *   <li>持续 90 秒后化为紫色烟雾消散；剩余 30 秒减速、剩余 10 秒闪烁示警。</li>
 *   <li>无法开门、无法使用道具，可走楼梯（寻路自动处理）。</li>
 * </ul>
 */
public class UndeadEntity extends PathfinderMob {

    /** 皮肤所属玩家 UUID（死者），仅用于客户端渲染外观。 */
    private static final EntityDataAccessor<Optional<UUID>> SKIN_UUID = SynchedEntityData.defineId(
            UndeadEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** 是否进入消散预警（剩余 10 秒），客户端用于闪烁渲染。 */
    private static final EntityDataAccessor<Boolean> FLICKERING = SynchedEntityData.defineId(
            UndeadEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int DEFAULT_LIFETIME = 90 * 20;
    private static final int WARNING_FADE_TICKS = 30 * 20;
    private static final int WARNING_FLICKER_TICKS = 10 * 20;
    private static final double PERCEPTION_RANGE = 15.0D;
    private static final int ATTACK_INTERVAL = 30; // 1.5 秒
    private static final double TOUCH_RANGE = 1.6D;
    private static final float INFECTION_PER_HIT = 15.0f;
    private static final double BASE_SPEED = 0.25D;
    /** 重新选择目标的间隔（计算可抵达性较重，节流处理）。 */
    private static final int RETARGET_INTERVAL = 10;
    /** 重新寻路的间隔（追击过程中周期性刷新路径）。 */
    private static final int REPATH_INTERVAL = 10;
    /** 单次重新选择时最多对几个最近目标做寻路可抵达性判定。 */
    private static final int MAX_REACH_CHECKS = 4;

    /** 召唤者（亡灵之主）UUID，用于结算感染与统计存活数量。 */
    private UUID ownerUuid = null;
    private Player ownerCache = null;

    private int remainingLifetime = DEFAULT_LIFETIME;
    private int attackCooldown = 0;
    /** 灵魂锁链：> 0 时强制跟随召唤者移动。 */
    private int followOwnerTicks = 0;
    private boolean slowedNearEnd = false;
    /** 当前追踪目标 UUID；周期性重新评估（优先可抵达目标）。 */
    private UUID currentTargetUuid = null;
    private int retargetCooldown = 0;

    public UndeadEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setHealth(this.getMaxHealth());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FOLLOW_RANGE, PERCEPTION_RANGE)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void registerGoals() {
        // 仅保留徘徊与张望；追击与感染逻辑在 tick() 中手动处理。
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_UUID, Optional.empty());
        builder.define(FLICKERING, false);
    }

    /** 初始化亡灵：设置召唤者、皮肤来源与持续时间。 */
    public void setup(Player owner, UUID skinPlayerUuid, int lifetimeTicks) {
        this.ownerUuid = owner.getUUID();
        this.ownerCache = owner;
        this.entityData.set(SKIN_UUID, Optional.ofNullable(skinPlayerUuid));
        this.remainingLifetime = lifetimeTicks;
//        this.addEffect(new MobEffectInstance(MobEffects.GLOWING, lifetimeTicks + 200, 0, false, false, false));
    }

    public UUID getSkinUuid() {
        return this.entityData.get(SKIN_UUID).orElse(null);
    }

    public boolean isFlickering() {
        return this.entityData.get(FLICKERING);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public Player getOwner() {
        if (ownerCache != null && ownerCache.isAlive()) {
            return ownerCache;
        }
        if (ownerUuid != null) {
            ownerCache = level().getPlayerByUUID(ownerUuid);
        }
        return ownerCache;
    }

    /** 延长存活时间（亡灵延命药剂 / 时之沙漏调用）。 */
    public void addLifetime(int ticks) {
        this.remainingLifetime += ticks;
    }

    public void setLifetime(int ticks) {
        this.remainingLifetime = ticks;
    }

    public int getRemainingLifetime() {
        return remainingLifetime;
    }

    /** 灵魂锁链：使亡灵跟随召唤者一段时间。 */
    public void leashToOwner(int ticks) {
        this.followOwnerTicks = ticks;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(level());
        if (gameWorldComponent == null || !gameWorldComponent.isRunning()) {
            disappear(serverLevel, false);
            return;
        }

        // 召唤者掉线/死亡则消散
        Player owner = getOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator()) {
            disappear(serverLevel, false);
            return;
        }

        remainingLifetime--;
        if (remainingLifetime <= 0) {
            disappear(serverLevel, true);
            return;
        }

        // 剩余 30 秒：减速 10%
        if (!slowedNearEnd && remainingLifetime <= WARNING_FADE_TICKS) {
            slowedNearEnd = true;
            var attr = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) {
                attr.setBaseValue(BASE_SPEED * 0.9D);
            }
        }
        // 剩余 10 秒：闪烁 + 低沉音效
        boolean flicker = remainingLifetime <= WARNING_FLICKER_TICKS;
        if (flicker != this.entityData.get(FLICKERING)) {
            this.entityData.set(FLICKERING, flicker);
            if (flicker) {
                serverLevel.playSound(null, blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.HOSTILE, 1.2f, 0.5f);
            }
        }

        spawnMistParticles(serverLevel);

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        // 灵魂锁链：跟随召唤者
        if (followOwnerTicks > 0) {
            followOwnerTicks--;
            getNavigation().moveTo(owner, 1.0D);
            setTarget(null);
            return;
        }

        if (retargetCooldown > 0) {
            retargetCooldown--;
        }

        // 当前目标若仍有效（存活、在感知范围内、可抵达）则保持，否则重新选择。
        ServerPlayer victim = resolveCurrentTarget(serverLevel, gameWorldComponent);
        if (victim == null || retargetCooldown <= 0) {
            retargetCooldown = RETARGET_INTERVAL;
            ServerPlayer reselected = selectBestTarget(serverLevel, gameWorldComponent);
            if (reselected != null) {
                victim = reselected;
                currentTargetUuid = reselected.getUUID();
            } else if (victim == null) {
                currentTargetUuid = null;
            }
        }

        if (victim != null) {
            setTarget(victim);
            getLookControl().setLookAt(victim);
            // 周期性刷新寻路，避免目标移动后停滞。
            if (getNavigation().isDone() || tickCount % REPATH_INTERVAL == 0) {
                getNavigation().moveTo(victim, 1.0D);
            }

            if (attackCooldown <= 0 && this.distanceToSqr(victim) <= TOUCH_RANGE * TOUCH_RANGE) {
                performAttack(serverLevel, owner, victim);
            }
        } else {
            setTarget(null);
        }
    }

    /** 校验当前目标是否仍可作为攻击对象（存活、感知范围内、可抵达）。 */
    private ServerPlayer resolveCurrentTarget(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent) {
        if (currentTargetUuid == null) {
            return null;
        }
        if (!(serverLevel.getPlayerByUUID(currentTargetUuid) instanceof ServerPlayer victim)) {
            return null;
        }
        if (!isValidVictim(victim, gameWorldComponent)) {
            return null;
        }
        if (this.distanceToSqr(victim) > PERCEPTION_RANGE * PERCEPTION_RANGE) {
            return null;
        }
        // 当前目标已变得不可抵达：放弃，交由 selectBestTarget 切换到其它可抵达目标。
        if (!isReachable(victim)) {
            return null;
        }
        return victim;
    }

    /**
     * 选择最佳目标：在感知范围内的候选目标中，优先返回最近的「可寻路抵达」目标；
     * 若全部不可抵达，则退而追踪最近者（路径可能稍后打开）。
     */
    private ServerPlayer selectBestTarget(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent) {
        List<ServerPlayer> candidates = serverLevel.players().stream()
                .filter(p -> isValidVictim(p, gameWorldComponent))
                .filter(p -> this.distanceToSqr(p) <= PERCEPTION_RANGE * PERCEPTION_RANGE)
                .sorted(Comparator.comparingDouble(this::distanceToSqr))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        int checks = Math.min(MAX_REACH_CHECKS, candidates.size());
        for (int i = 0; i < checks; i++) {
            if (isReachable(candidates.get(i))) {
                return candidates.get(i);
            }
        }
        return candidates.get(0);
    }

    /** 目标筛选条件：存活、冒险模式、非杀手阵营、非召唤者。 */
    private boolean isValidVictim(ServerPlayer p, SREGameWorldComponent gameWorldComponent) {
        return p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE
                && GameUtils.isPlayerAliveAndSurvival(p)
                && !p.getUUID().equals(ownerUuid)
                && !gameWorldComponent.isKillerTeam(p);
    }

    /** 是否能寻路抵达目标。 */
    private boolean isReachable(ServerPlayer victim) {
        Path path = getNavigation().createPath(victim, 0);
        return path != null && path.canReach();
    }

    /** 执行一次攻击：挥手、注入感染并造成真实伤害（归属于亡灵之主）。 */
    private void performAttack(ServerLevel serverLevel, Player owner, ServerPlayer victim) {
        attackCooldown = ATTACK_INTERVAL;
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        ModComponents.UNDEAD_LORD.maybeGet(owner).ifPresent(comp -> comp.addInfection(victim, INFECTION_PER_HIT));

        float damage = (float) NoellesRolesConfig.HANDLER.instance().undeadLordUndeadAttackDamage;
        if (damage > 0f) {
            // 以召唤者身份造成伤害，使击杀归属于亡灵之主（杀手阵营）。
            DamageSource source = owner.damageSources().playerAttack(owner);
            victim.hurt(source, damage);
        }

        serverLevel.playSound(null, victim.blockPosition(), SoundEvents.ZOMBIE_INFECT,
                SoundSource.HOSTILE, 0.8f, 0.7f);
    }

    private void spawnMistParticles(ServerLevel serverLevel) {
        if (this.tickCount % 4 != 0) {
            return;
        }
        // 剩余 30 秒后雾气变淡
        int count = remainingLifetime <= WARNING_FADE_TICKS ? 1 : 2;
        serverLevel.sendParticles(ParticleTypes.WITCH,
                getX(), getY() + 1.0D, getZ(), count, 0.3D, 0.5D, 0.3D, 0.01D);
    }

    private void disappear(ServerLevel serverLevel, boolean fullyAged) {
        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                getX(), getY() + 1.0D, getZ(), 25, 0.4D, 0.8D, 0.4D, 0.02D);
        serverLevel.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 0.7f, 0.6f);
        Player owner = getOwner();
        if (owner != null) {
            ModComponents.UNDEAD_LORD.maybeGet(owner).ifPresent(comp -> comp.onUndeadRemoved(this.getUUID()));
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        // 亡灵免疫常规伤害（仅随时间消散），但虚空伤害正常致死。
        if (damageSource.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            if (level() instanceof ServerLevel serverLevel) {
                disappear(serverLevel, false);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canRide(net.minecraft.world.entity.Entity entity) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) {
            tag.putUUID("OwnerUUID", ownerUuid);
        }
        UUID skin = getSkinUuid();
        if (skin != null) {
            tag.putUUID("SkinUUID", skin);
        }
        tag.putInt("RemainingLifetime", remainingLifetime);
        tag.putInt("FollowOwnerTicks", followOwnerTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) {
            ownerUuid = tag.getUUID("OwnerUUID");
        }
        if (tag.hasUUID("SkinUUID")) {
            this.entityData.set(SKIN_UUID, Optional.of(tag.getUUID("SkinUUID")));
        }
        remainingLifetime = tag.contains("RemainingLifetime") ? tag.getInt("RemainingLifetime") : DEFAULT_LIFETIME;
        followOwnerTicks = tag.getInt("FollowOwnerTicks");
    }
}
