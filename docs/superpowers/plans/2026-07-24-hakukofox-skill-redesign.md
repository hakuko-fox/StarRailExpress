# HakukoFox 技能重製 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite HakukoFox skills: beast form (Speed II + Jump II, toggle, no attack, nine lives) + fox clone (spawn/POV/possess state machine).

**Architecture:** Single-Player-entity approach — clone is a real Fox entity; POV uses `ServerPlayer#setCamera()`; possess teleports the player. All state lives in `HakukoFoxPlayerComponent` with a byte-coded state enum, synced via CCA.

**Tech Stack:** Fabric + CCA (Cardinal Components) + MixinExtras

## Global Constraints

- No new dependencies beyond what the mod already uses
- Do not bump the mod version
- Follow existing patterns (references: Halic, Adventurer, Magician mixins)
- All text in English in code; translations in JSON files
- Keybindings: G for skill 1, Shift+G for skill 2 (unchanged)
- Avoid keybinding conflicts with existing mod bindings

---

### Task 1: Rewrite HakukoFoxPlayerComponent — Beast Form + Clone State Machine + Nine Lives

**Files:**
- Modify: `src/main/java/org/agmas/noellesroles/game/roles/killer/hakukofox/HakukoFoxPlayerComponent.java`
- Test: N/A (no test framework in this mod)

**Interfaces:**
- Consumes: `SREPlayerShopComponent.KEY.get(sp)` for gold deduction
- Produces: `HakukoFoxPlayerComponent.KEY`, methods called by skill handlers

- [ ] **Step 1: Replace class body with new state machine**

```java
package org.agmas.noellesroles.game.roles.killer.hakukofox;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class HakukoFoxPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<HakukoFoxPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "hakukofox"),
            HakukoFoxPlayerComponent.class);

    public enum CloneState {
        NONE,
        EXISTS,
        POV,
        POSSESSED
    }

    private final Player player;
    private boolean beastFormActive;
    private boolean nineLivesUsed;
    private int cloneId; // -1 if no clone
    private CloneState cloneState;
    private double originalX, originalY, originalZ; // original pos for revert
    private float originalYaw, originalPitch;

    public HakukoFoxPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() { return player; }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) { return p == this.player; }

    public void sync() { KEY.sync(player); }

    @Override
    public void init() {
        beastFormActive = false;
        nineLivesUsed = false;
        cloneId = -1;
        cloneState = CloneState.NONE;
        sync();
    }

    @Override
    public void clear() {
        if (beastFormActive) {
            removeBeastEffects();
        }
        despawnClone();
        beastFormActive = false;
        nineLivesUsed = false;
        cloneId = -1;
        cloneState = CloneState.NONE;
        sync();
    }

    public boolean isBeastFormActive() { return beastFormActive; }
    public boolean isCloneActive() { return cloneState != CloneState.NONE; }

    public boolean isDisguised() { return beastFormActive; }

    public static boolean isDisguised(Player player) {
        HakukoFoxPlayerComponent comp = KEY.maybeGet(player).orElse(null);
        return comp != null && comp.isDisguised();
    }

    // ========== Skill 1: Beast Form ==========

    public boolean toggleBeastForm(ServerPlayer sp, RoleSkillContext context) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return false;

        if (beastFormActive) {
            // Exit beast form — start 180s cooldown
            removeBeastEffects();
            beastFormActive = false;
            sp.refreshDimensions();
            sp.displayClientMessage(
                    Component.translatable("skill.noellesroles.hakukofox.transform_off"),
                    true);
            context.setSkillCooldown(180 * 20);
            sync();
            return true;
        } else {
            // Enter beast form
            if (sp.hasEffect(MobEffects.MOVEMENT_SPEED)) sp.removeEffect(MobEffects.MOVEMENT_SPEED);
            if (sp.hasEffect(MobEffects.JUMP)) sp.removeEffect(MobEffects.JUMP);
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 1, false, false, true));
            sp.addEffect(new MobEffectInstance(MobEffects.JUMP, -1, 1, false, false, true));
            beastFormActive = true;
            nineLivesUsed = false; // reset nine lives on each transformation
            sp.refreshDimensions();

            ServerLevel world = sp.serverLevel();
            world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.FOX_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.0F);
            sp.displayClientMessage(
                    Component.translatable("skill.noellesroles.hakukofox.transform_on"),
                    true);
            sync();
            return true;
        }
    }

    private void removeBeastEffects() {
        if (player instanceof ServerPlayer sp) {
            if (sp.hasEffect(MobEffects.MOVEMENT_SPEED)) sp.removeEffect(MobEffects.MOVEMENT_SPEED);
            if (sp.hasEffect(MobEffects.JUMP)) sp.removeEffect(MobEffects.JUMP);
        }
    }

    // ========== Skill 2: Fox Clone ==========

    public boolean useCloneSkill(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return false;

        switch (cloneState) {
            case NONE -> { return spawnClone(sp); }
            case EXISTS -> { return enterPOV(sp); }
            case POV -> { return possessClone(sp); }
            case POSSESSED -> { return revertToOriginal(sp); }
            default -> { return false; }
        }
    }

    private boolean spawnClone(ServerPlayer sp) {
        int cost = 50;
        var shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.hakukofox.not_enough_money", cost),
                    true);
            return false;
        }
        shop.addToBalance(-cost);

        Fox fox = EntityType.FOX.create(sp.serverLevel());
        if (fox == null) return false;
        fox.setVariant(Fox.Type.SNOW);
        fox.setPos(sp.getX(), sp.getY(), sp.getZ());
        fox.setCustomNameVisible(false);
        fox.setCustomName(null);
        fox.setPersistenceRequired();
        sp.serverLevel().addFreshEntity(fox);

        cloneId = fox.getId();
        cloneState = CloneState.EXISTS;

        sp.playNotifySound(SoundEvents.FOX_SPIT, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("skill.noellesroles.hakukofox.clone_spawned"),
                true);
        sync();
        return true;
    }

    private boolean enterPOV(ServerPlayer sp) {
        Fox fox = getCloneFox(sp);
        if (fox == null) {
            cloneState = CloneState.NONE;
            cloneId = -1;
            sync();
            return false;
        }
        originalX = sp.getX();
        originalY = sp.getY();
        originalZ = sp.getZ();
        originalYaw = sp.getYRot();
        originalPitch = sp.getXRot();
        sp.setCamera(fox);
        cloneState = CloneState.POV;
        sp.displayClientMessage(
                Component.translatable("skill.noellesroles.hakukofox.clone_pov"),
                true);
        sync();
        return true;
    }

    private boolean possessClone(ServerPlayer sp) {
        Fox fox = getCloneFox(sp);
        if (fox == null) {
            cloneState = CloneState.NONE;
            cloneId = -1;
            sync();
            return false;
        }
        // Store fox position
        double fx = fox.getX();
        double fy = fox.getY();
        double fz = fox.getZ();
        float fyaw = fox.getYRot();
        float fpitch = fox.getXRot();
        fox.discard();

        // Teleport player to fox position
        sp.setCamera(null);
        sp.teleportTo(fx, fy, fz);
        sp.setYRot(fyaw);
        sp.setXRot(fpitch);
        sp.setCamera(null);

        cloneId = -1;
        cloneState = CloneState.POSSESSED;
        sp.displayClientMessage(
                Component.translatable("skill.noellesroles.hakukofox.clone_possess"),
                true);
        sync();
        return true;
    }

    private boolean revertToOriginal(ServerPlayer sp) {
        sp.setCamera(null);
        sp.teleportTo(originalX, originalY, originalZ);
        sp.setYRot(originalYaw);
        sp.setXRot(originalPitch);
        cloneState = CloneState.NONE;
        cloneId = -1;
        sp.displayClientMessage(
                Component.translatable("skill.noellesroles.hakukofox.clone_revert"),
                true);
        sync();
        return true;
    }

    // ========== POV ESC handler ==========

    public boolean exitPOV(ServerPlayer sp) {
        if (cloneState != CloneState.POV) return false;
        sp.setCamera(null);
        // Player is already at original position — just restore camera
        cloneState = CloneState.EXISTS;
        sync();
        return true;
    }

    // ========== Helper ==========

    private Fox getCloneFox(ServerPlayer sp) {
        if (cloneId < 0) return null;
        Entity e = sp.serverLevel().getEntity(cloneId);
        if (e instanceof Fox fox) return fox;
        return null;
    }

    private void despawnClone() {
        if (player instanceof ServerPlayer sp && cloneId >= 0) {
            Fox fox = getCloneFox(sp);
            if (fox != null) fox.discard();
        }
        cloneId = -1;
        cloneState = CloneState.NONE;
    }

    // ========== Nine Lives ==========

    public boolean tryUseNineLives() {
        if (beastFormActive && !nineLivesUsed) {
            nineLivesUsed = true;
            sync();
            return true;
        }
        return false;
    }

    public boolean hasNineLivesRemaining() {
        return beastFormActive && !nineLivesUsed;
    }

    // ========== Server Tick ==========

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayer sp) {
            // Clean up if clone despawned
            if (cloneState != CloneState.NONE && cloneState != CloneState.POSSESSED) {
                Fox fox = getCloneFox(sp);
                if (fox == null || !fox.isAlive()) {
                    cloneState = CloneState.NONE;
                    cloneId = -1;
                    sync();
                }
            }
            // If in beast form but somehow not alive, remove effects
            if (beastFormActive && !GameUtils.isPlayerAliveAndSurvival(sp)) {
                removeBeastEffects();
                beastFormActive = false;
                sync();
            }
        }
    }

    // ========== NBT ==========

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("beastFormActive", beastFormActive);
        tag.putBoolean("nineLivesUsed", nineLivesUsed);
        tag.putInt("cloneId", cloneId);
        tag.putString("cloneState", cloneState.name());
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        beastFormActive = tag.getBoolean("beastFormActive");
        nineLivesUsed = tag.getBoolean("nineLivesUsed");
        cloneId = tag.getInt("cloneId");
        try {
            cloneState = CloneState.valueOf(tag.getString("cloneState"));
        } catch (Exception e) {
            cloneState = CloneState.NONE;
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        writeToSyncNbt(tag, provider);
        tag.putDouble("originalX", originalX);
        tag.putDouble("originalY", originalY);
        tag.putDouble("originalZ", originalZ);
        tag.putFloat("originalYaw", originalYaw);
        tag.putFloat("originalPitch", originalPitch);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        readFromSyncNbt(tag, provider);
        originalX = tag.getDouble("originalX");
        originalY = tag.getDouble("originalY");
        originalZ = tag.getDouble("originalZ");
        originalYaw = tag.getFloat("originalYaw");
        originalPitch = tag.getFloat("originalPitch");
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew.bat build` (or whatever build command the project uses)
Expected: Build succeeds

---

### Task 2: Update Skill Registration in ModRolesInitialEventRegister

**Files:**
- Modify: `src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java`

**Interfaces:**
- Consumes: `HakukoFoxPlayerComponent.KEY`, `HakukoFoxPlayerComponent#toggleBeastForm`, `HakukoFoxPlayerComponent#useCloneSkill`
- Produces: Registered skill definitions for `ModRoles.HAKUKO_FOX`

- [ ] **Step 1: Replace HakukoFox skill registration block (lines 1208-1223)**

Replace the existing HakukoFox section (lines 1208-1223) with:

```java
        // ==================== HakukoFox 技能注册 ====================
        // 技能1（G）：兽化型态 — 变身为白色狐狸，获得速度 II, 跳跃 II，无限时间
        //   再按 G 回到人型，冷却 180 秒。
        //   被动：兽化时免疫一次致命伤害（狐有九命）。
        // 技能2（Shift+G）：狐狸分身 — 消耗50金生产狐狸分身，切换视角/接管/退回
        //   冷却 90 秒。
        RoleSkill.register(ModRoles.HAKUKO_FOX,
                RoleSkill.skill(SRE.id("hakukofox_transform"), "skill.noellesroles.hakukofox.transform", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return false;
                    return org.agmas.noellesroles.game.roles.killer.hakukofox.HakukoFoxPlayerComponent.KEY.get(player)
                            .toggleBeastForm(player, context);
                }).cooldownSeconds(0).toggleable(true).showOnHud(true).build(),
                RoleSkill.skill(SRE.id("hakukofox_clone"), "skill.noellesroles.hakukofox.clone", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return false;
                    return org.agmas.noellesroles.game.roles.killer.hakukofox.HakukoFoxPlayerComponent.KEY.get(player)
                            .useCloneSkill(player);
                }).shifted(true).cooldownSeconds(90).showOnHud(true).build());
```

Note: Skill 1 uses `cooldownSeconds(0)` + `toggleable(true)` — cooldown is manually set by the handler on exit only.

- [ ] **Step 2: Verify compilation**

Run build command.
Expected: Build succeeds

---

### Task 3: Create HakukoFoxAttackBlockMixin — Block attacks in beast form

**Files:**
- Create: `src/main/java/org/agmas/noellesroles/mixin/roles/hakukofox/HakukoFoxAttackBlockMixin.java`

**Interfaces:**
- Consumes: `HakukoFoxPlayerComponent.isDisguised(Player)` (uses `beastFormActive`)
- Produces: Mixin that cancels `Player#attack` when in beast form

- [ ] **Step 1: Create the mixin**

```java
package org.agmas.noellesroles.mixin.roles.hakukofox;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.hakukofox.HakukoFoxPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class HakukoFoxAttackBlockMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockAttackInBeastForm(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (HakukoFoxPlayerComponent.isDisguised(self)) {
            ci.cancel();
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Expected: Build succeeds

---

### Task 4: Create HakukoFoxDeathImmunityMixin — Nine Lives passive

**Files:**
- Create: `src/main/java/org/agmas/noellesroles/mixin/roles/hakukofox/HakukoFoxDeathImmunityMixin.java`

**Interfaces:**
- Consumes: `HakukoFoxPlayerComponent.KEY.get(player).tryUseNineLives()`
- Produces: Cancels death when nine lives is available

- [ ] **Step 1: Create the mixin**

```java
package org.agmas.noellesroles.mixin.roles.hakukofox;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.hakukofox.HakukoFoxPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class HakukoFoxDeathImmunityMixin {

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void noellesroles$nineLivesImmunity(DamageSource damageSource, CallbackInfo ci) {
        if (!((Object) this instanceof Player player)) return;
        if (player.level().isClientSide) return;
        var comp = HakukoFoxPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (comp != null && comp.tryUseNineLives()) {
            player.setHealth(20.0F);
            ci.cancel();
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Expected: Build succeeds

---

### Task 5: Register New Mixins in noellesroles.mixins.json

**Files:**
- Modify: `src/main/resources/noellesroles.mixins.json`

- [ ] **Step 1: Add new mixins to the mixins list**

Add these two lines after line 49 (`"roles.hakukofox.HakukoFoxEyeHeightMixin",`):

```json
    "roles.hakukofox.HakukoFoxAttackBlockMixin",
    "roles.hakukofox.HakukoFoxDeathImmunityMixin",
```

The relevant section should look like:
```json
    "roles.leather_pig.LeatherPigEyeHeightMixin",
    "roles.hakukofox.HakukoFoxEyeHeightMixin",
    "roles.hakukofox.HakukoFoxAttackBlockMixin",
    "roles.hakukofox.HakukoFoxDeathImmunityMixin",
    "roles.manipulator.PlayerControlMixin",
```

- [ ] **Step 2: Verify compilation**

Expected: Build succeeds

---

### Task 6: Update HakukoFoxDisguiseRenderer

**Files:**
- Modify: `src/main/java/org/agmas/noellesroles/client/HakukoFoxDisguiseRenderer.java`

The renderer already uses `HakukoFoxPlayerComponent.isDisguised()` which now returns `beastFormActive`. No change needed if compatibility is maintained.

- [ ] **Step 1: Verify the existing code works with the new component**

The `isDisguised` method in the component was renamed from checking `foxFormRemainingTicks > 0` to checking `beastFormActive`. Both mixins and the renderer call `HakukoFoxPlayerComponent.isDisguised(player)` (static method), which delegates to the instance method. The static method signature is unchanged, so all consumers remain compatible.

No code changes needed.

---

### Task 7: Update Language Files

**Files:**
- Modify: `src/main/resources/assets/noellesroles/lang/en_us.json`
- Modify: `src/main/resources/assets/noellesroles/lang/zh_tw.json`
- Modify: `src/main/resources/assets/noellesroles/lang/zh_cn.json`
- Modify: `src/main/resources/assets/role_modifier_intro/lang/en_us.json`
- Modify: `src/main/resources/assets/role_modifier_intro/lang/zh_tw.json`
- Modify: `src/main/resources/assets/role_modifier_intro/lang/zh_cn.json`

- [ ] **Step 1: Update en_us.json**

Replace old HakukoFox entries (around line 3651-3656) with:

```json
  "announcement.star.role.hakukofox": "HakukoFox",
  "announcement.star.role.noellesroles.hakukofox": "HakukoFox",
  "announcement.star.goals.hakukofox": "Use beast form and fox clones to eliminate all passengers!",
  "skill.noellesroles.hakukofox.transform": "Beast Form",
  "skill.noellesroles.hakukofox.transform_on": "§aTransformed into beast form! Gain Speed II and Jump II.",
  "skill.noellesroles.hakukofox.transform_off": "§cReverted to human form.",
  "skill.noellesroles.hakukofox.clone": "Fox Clone",
  "skill.noellesroles.hakukofox.clone_spawned": "§aSpent 50 coins to spawn a fox clone!",
  "skill.noellesroles.hakukofox.clone_pov": "§aNow viewing through the clone's eyes.",
  "skill.noellesroles.hakukofox.clone_possess": "§aPossessed the fox clone!",
  "skill.noellesroles.hakukofox.clone_revert": "§cReturned to original body.",
  "message.noellesroles.hakukofox.not_enough_money": "§cNot enough coins! Need %d coins.",
  "message.noellesroles.hakukofox.nine_lives_saved": "§6Nine Lives! You cheated death!",
```

- [ ] **Step 2: Update zh_tw.json**

Replace old HakukoFox entries (around line 3795-3800):

```json
  "announcement.star.role.hakukofox": "白狐",
  "announcement.star.role.noellesroles.hakukofox": "白狐",
  "announcement.star.goals.hakukofox": "利用獸化型態和狐狸分身，淘汰所有乘客！",
  "skill.noellesroles.hakukofox.transform": "獸化型態",
  "skill.noellesroles.hakukofox.transform_on": "§a變身為獸化型態！獲得速度 II 和跳躍 II！",
  "skill.noellesroles.hakukofox.transform_off": "§c回到人類型態。",
  "skill.noellesroles.hakukofox.clone": "狐狸分身",
  "skill.noellesroles.hakukofox.clone_spawned": "§a消耗50金幣，生產了一隻狐狸分身！",
  "skill.noellesroles.hakukofox.clone_pov": "§a進入狐狸分身視角。",
  "skill.noellesroles.hakukofox.clone_possess": "§a接管了狐狸分身！",
  "skill.noellesroles.hakukofox.clone_revert": "§c退回原身體。",
  "message.noellesroles.hakukofox.not_enough_money": "§c金幣不足！需要 %d 金幣。",
  "message.noellesroles.hakukofox.nine_lives_saved": "§6狐有九命！你逃過一劫！",
```

- [ ] **Step 3: Update zh_cn.json**

Same entries as zh_tw.json (replace at line 3795-3800):

```json
  "announcement.star.role.hakukofox": "白狐",
  "announcement.star.role.noellesroles.hakukofox": "白狐",
  "announcement.star.goals.hakukofox": "利用兽化形态和狐狸分身，淘汰所有乘客！",
  "skill.noellesroles.hakukofox.transform": "兽化形态",
  "skill.noellesroles.hakukofox.transform_on": "§a变身为兽化形态！获得速度 II 和跳跃 II！",
  "skill.noellesroles.hakukofox.transform_off": "§c回到人类形态。",
  "skill.noellesroles.hakukofox.clone": "狐狸分身",
  "skill.noellesroles.hakukofox.clone_spawned": "§a消耗50金币，生产了一只狐狸分身！",
  "skill.noellesroles.hakukofox.clone_pov": "§a进入狐狸分身视角。",
  "skill.noellesroles.hakukofox.clone_possess": "§a接管了狐狸分身！",
  "skill.noellesroles.hakukofox.clone_revert": "§c退回原身体。",
  "message.noellesroles.hakukofox.not_enough_money": "§c金币不足！需要 %d 金币。",
  "message.noellesroles.hakukofox.nine_lives_saved": "§6狐有九命！你逃过一劫！",
```

- [ ] **Step 4: Update role_modifier_intro en_us.json**

Replace lines 233-234 with:

```json
  "info.screen.roleid.hakukofox": "Killer Faction\nHong Kong indie Vtuber 🦊\nOn a journey to return to her own time and become a VTuber.\nShe gathers support from viewers and converts their cheers into magic power to return to the Ice Castle.\nSkill 1 (G): Transform into beast form — Snow Fox, gaining Speed II and Jump II. Infinite duration. Cannot attack in beast form. Press G again to revert. Cooldown 180s.\nPassive — Nine Lives: Immune to one fatal blow while in beast form.\nSkill 2 (Shift+G): Fox Clone. Costs 50 coins. Spawn an AI fox clone. Press again to view through the clone's eyes (your body stays). Press again to possess the clone (body disappears). ESC to return. Cooldown 90s.",
  "info.screen.roleid.hakukofox.simple": "Killer Faction\nBeast form: speed + jump, no attack\nFox clone: POV / possess / return"
```

- [ ] **Step 5: Update role_modifier_intro zh_tw.json**

Replace lines 423-424 with:

```json
  "info.screen.roleid.hakukofox": "殺手陣營\n香港個人勢Vtuber 🦊\n為了回到自己所屬的時空，踏上成為VTuber的旅途。\n希望藉此吸引觀眾的支持，將他們的應援轉化為魔力，幫助自己回到冰霜之城。\n技能1（G）：變身獸化型態—雪狐，獲得速度 II, 跳躍 II，無限持續時間。獸化時無法攻擊。再按 G 回到人型，冷卻 180 秒。\n被動—狐有九命：獸化型態下免疫一次致命傷害。\n技能2（Shift+G）：狐狸分身。消耗50金幣。生產一隻AI狐狸分身。再按進入分身視角（本體留在原地）。再按接管分身（本體消失）。ESC退回。冷卻90秒。",
  "info.screen.roleid.hakukofox.simple": "殺手陣營\n獸化型態：強化速度+跳躍，無法攻擊\n狐狸分身：視角切換 / 接管 / 退回"
```

- [ ] **Step 6: Update role_modifier_intro zh_cn.json**

Replace lines 423-424 with:

```json
  "info.screen.roleid.hakukofox": "杀手阵营\n香港個人勢Vtuber 🦊\n为了回到自己所屬的時空，踏上成為VTuber的旅途。\n希望藉此吸引觀眾的支持，將他們的應援轉化為魔力，幫助自己回到冰霜之城。\n技能1（G）：变身兽化形态—雪狐，获得速度 II, 跳跃 II，无限持续时间。兽化时无法攻击。再按 G 回到人型，冷却 180 秒。\n被动—狐有九命：兽化形态下免疫一次致命伤害。\n技能2（Shift+G）：狐狸分身。消耗50金币。生产一只AI狐狸分身。再按进入分身视角（本体留在原地）。再按接管分身（本体消失）。ESC退回。冷却90秒。",
  "info.screen.roleid.hakukofox.simple": "杀手阵营\n兽化形态：强化速度+跳跃，无法攻击\n狐狸分身：视角切换 / 接管 / 退回"
```

- [ ] **Step 7: Verify compilation**

Expected: Build succeeds

---

### Task 8: Verify ESC Handling for Clone POV Exit

**Problem:** The ESC key is not a normal skill keybind. We need a way for the player to exit the clone POV with ESC.

**Approach:** The camera detachment via `setCamera()` is naturally released when the player presses the sneak key or interacts. But for explicit ESC handling, we should hook into the `ServerPlayer#releaseCamera()` or use a client-side keybind.

**Alternative approach:** Since the mod already uses the key G and Shift+G for skills, we can handle the ESC case by checking if the player is in POV mode. When the player presses any movement key or ESC naturally releases the camera in Minecraft, we detect it in `serverTick()`:

```java
// In serverTick:
if (cloneState == CloneState.POV && sp.getCamera() == sp) {
    // Camera was released (ESC pressed, or moved)
    cloneState = CloneState.EXISTS;
    sync();
}
```

This should be added to the `serverTick()` method in Task 1. Let me add that logic now.

- [ ] **Step 1: Add camera release detection to serverTick**

In Task 1's `serverTick()`, add after the clone cleanup block:

```java
// Detect ESC/forced camera release while in POV mode
if (cloneState == CloneState.POV && sp.getCamera() == sp) {
    cloneState = CloneState.EXISTS;
    sync();
}
```

This must be added to the method in Task 1.

---

### Task 9: Final Integration Verification

- [ ] **Step 1: Build the whole project**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Manual test checklist**
  1. Equip HakukoFox role
  2. Press G → Should transform to white fox (Speed II, Jump II)
  3. Try attacking → Should be blocked
  4. Take damage while in beast form → Should survive once (nine lives)
  5. Press G again → Revert to human, 180s cooldown starts
  6. Press Shift+G → Spawn fox clone, -50 coins
  7. Press Shift+G again → Enter clone POV
  8. Press Shift+G again → Possess clone, teleport to clone position
  9. Press Shift+G again → Revert to original position
  10. Press Shift+G → Spawn clone, enter POV, press ESC → Return to original, clone stays
