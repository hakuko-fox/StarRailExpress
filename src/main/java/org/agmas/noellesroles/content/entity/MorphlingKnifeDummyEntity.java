package org.agmas.noellesroles.content.entity;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModEntities;

/**
 * 变形者「举刀假人」实体。
 *
 * <p>变形者按技能键后在其面前生成本实体：手持匕首向前突进，外观复制变形者当前的样貌
 * （若正在变形则为伪装对象，否则为自身）。
 *
 * <ul>
 *   <li>感知范围内发现非杀手阵营的活人后直线追击，贴近目标即「砍下」将其击杀，随后自身消散。</li>
 *   <li>若在此期间被任何实体攻击，则立即消散并在当前位置生成一颗闪光弹。</li>
 *   <li>持续时间耗尽（默认 10 秒）或召唤者死亡/掉线时静默消散。</li>
 * </ul>
 */
public class MorphlingKnifeDummyEntity extends PathfinderMob {

    /** 皮肤所属玩家 UUID，仅用于客户端渲染外观。 */
    private static final EntityDataAccessor<Optional<UUID>> SKIN_UUID = SynchedEntityData.defineId(
            MorphlingKnifeDummyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final double PERCEPTION_RANGE = 12.0D;
    private static final double TOUCH_RANGE = 1.8D;
    private static final double BASE_SPEED = 0.42D;

    /** 召唤者（变形者）UUID。 */
    private UUID ownerUuid = null;
    private Player ownerCache = null;

    private int remainingLifetime = 10 * 20;
    /** 初始朝向（无目标时沿此方向直线前进）。 */
    private float forwardYaw = 0.0F;
    private int repathTimer = 0;

    public MorphlingKnifeDummyEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setHealth(2.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 2.0)
                .add(Attributes.FOLLOW_RANGE, PERCEPTION_RANGE)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void registerGoals() {
        // 追击与前进逻辑均在 tick() 中手动处理，不使用默认 AI。
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_UUID, Optional.empty());
    }

    /** 初始化假人：召唤者、渲染皮肤、持续时间与初始朝向。 */
    public void setup(Player owner, UUID skinPlayerUuid, int lifetimeTicks, float yaw) {
        this.ownerUuid = owner.getUUID();
        this.ownerCache = owner;
        this.entityData.set(SKIN_UUID, Optional.ofNullable(skinPlayerUuid));
        this.remainingLifetime = lifetimeTicks;
        this.forwardYaw = yaw;
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
    }

    public UUID getSkinUuid() {
        return this.entityData.get(SKIN_UUID).orElse(null);
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

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(level());
        if (gameWorldComponent == null || !gameWorldComponent.isRunning()) {
            vanish(serverLevel);
            return;
        }

        Player owner = getOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator()) {
            vanish(serverLevel);
            return;
        }

        remainingLifetime--;
        if (remainingLifetime <= 0) {
            vanish(serverLevel);
            return;
        }

        spawnTrailParticles(serverLevel);

        // 寻找最近的可击杀目标（存活、冒险模式、非杀手阵营、非召唤者）
        Optional<ServerPlayer> target = serverLevel.players().stream()
                .filter(p -> p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .filter(p -> !p.getUUID().equals(ownerUuid))
                .filter(p -> !gameWorldComponent.isKillerTeam(p))
                .filter(p -> this.distanceToSqr(p) <= PERCEPTION_RANGE * PERCEPTION_RANGE)
                .min(Comparator.comparingDouble(this::distanceToSqr));

        if (target.isPresent()) {
            ServerPlayer victim = target.get();
            getNavigation().moveTo(victim, 1.0D);
            getLookControl().setLookAt(victim);

            if (this.distanceToSqr(victim) <= TOUCH_RANGE * TOUCH_RANGE) {
                slashKill(serverLevel, owner, victim);
            }
        } else {
            // 无目标：沿初始朝向直线前进
            if (--repathTimer <= 0 || getNavigation().isDone()) {
                repathTimer = 10;
                double rad = Math.toRadians(forwardYaw);
                double dx = -Math.sin(rad);
                double dz = Math.cos(rad);
                getNavigation().moveTo(getX() + dx * 8.0D, getY(), getZ() + dz * 8.0D, 1.0D);
            }
        }
    }

    /** 砍下：击杀目标后自身消散。 */
    private void slashKill(ServerLevel serverLevel, Player owner, ServerPlayer victim) {
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        serverLevel.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE, 1.0f, 1.0f);
        GameUtils.killPlayer(victim, true, owner, GameConstants.DeathReasons.KNIFE);
        vanish(serverLevel);
    }

    private void spawnTrailParticles(ServerLevel serverLevel) {
        if (this.tickCount % 3 != 0) {
            return;
        }
        serverLevel.sendParticles(ParticleTypes.SMOKE,
                getX(), getY() + 1.0D, getZ(), 1, 0.2D, 0.4D, 0.2D, 0.01D);
    }

    /** 静默消散（时间耗尽 / 召唤者失效 / 击杀完成）。 */
    private void vanish(ServerLevel serverLevel) {
        serverLevel.sendParticles(ParticleTypes.POOF,
                getX(), getY() + 1.0D, getZ(), 15, 0.3D, 0.6D, 0.3D, 0.02D);
        serverLevel.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 0.6f, 1.4f);
        discard();
    }

    /** 被攻击：消散并在当前位置生成一颗闪光弹。 */
    private void burstIntoFlash(ServerLevel serverLevel) {
        FlashGrenadeEntity flash = new FlashGrenadeEntity(ModEntities.FLASH_GRENADE, serverLevel);
        Player owner = getOwner();
        if (owner != null) {
            flash.setOwner(owner);
        }
        flash.setPosRaw(getX(), getY() + 0.5D, getZ());
        flash.setDeltaMovement(0.0D, -0.2D, 0.0D);
        serverLevel.addFreshEntity(flash);

        serverLevel.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 0.6f, 1.2f);
        discard();
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Entity attacker = damageSource.getEntity();
        if (attacker != null) {
            // 被攻击：化作一颗闪光弹
            burstIntoFlash(serverLevel);
        } else {
            // 环境伤害（坠落 / 虚空等）：直接消散
            vanish(serverLevel);
        }
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canRide(Entity entity) {
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
        tag.putFloat("ForwardYaw", forwardYaw);
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
        remainingLifetime = tag.contains("RemainingLifetime") ? tag.getInt("RemainingLifetime") : 200;
        forwardYaw = tag.getFloat("ForwardYaw");
    }
}
