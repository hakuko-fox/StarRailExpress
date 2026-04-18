package org.agmas.noellesroles.init;

import io.github.mortuusars.exposure_polaroid.ExposurePolaroid;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.*;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.game.roles.Innocent.singer.SingerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ShootingFrenzyPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.MaChenXuPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.watcher.WatcherPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.water_ghost.WaterGhostPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.mercenary.MercenaryPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.RedHouseRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RoleShopHandler {
  private static final String OLDMAN_EASTER_EGG_TAG = "sre_oldman_easter_egg";
  private static final String OLDMAN_EASTER_EGG_USED_TAG = "sre_oldman_easter_egg_used";
  public static final String OLDMAN_EASTER_EGG_PIG_NO_STEP_TAG = "sre_oldman_easter_egg_pig_no_step";
  private static boolean oldmanEasterEggTriggeredInRound = false;

  public static void resetOldmanEasterEggState() {
    oldmanEasterEggTriggeredInRound = false;
  }

  public static @NotNull ItemStack createOldmanEasterEggRod() {
    ItemStack rod = Items.CARROT_ON_A_STICK.getDefaultInstance();
    rod.set(DataComponents.UNBREAKABLE, new Unbreakable(true));

    CompoundTag tag = new CompoundTag();
    tag.putBoolean(OLDMAN_EASTER_EGG_TAG, true);
    tag.putBoolean(OLDMAN_EASTER_EGG_USED_TAG, false);
    rod.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    return rod;
  }

  public static boolean isOldmanEasterEggRod(@NotNull ItemStack stack) {
    if (!stack.is(Items.CARROT_ON_A_STICK))
      return false;
    var customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
    return customData.copyTag().getBoolean(OLDMAN_EASTER_EGG_TAG);
  }

  public static boolean hasUsedOldmanEasterEggRod(@NotNull ItemStack stack) {
    if (!isOldmanEasterEggRod(stack))
      return false;
    var customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
    return customData.copyTag().getBoolean(OLDMAN_EASTER_EGG_USED_TAG);
  }

  public static void markOldmanEasterEggRodUsed(@NotNull ItemStack stack) {
    if (!isOldmanEasterEggRod(stack))
      return;
    var customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
    CompoundTag tag = customData.copyTag();
    tag.putBoolean(OLDMAN_EASTER_EGG_USED_TAG, true);
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
  }

  // ==================== 商店项目列表 ====================
  public static ArrayList<ShopEntry> FRAMING_ROLES_SHOP = new ArrayList<>();
  // ==================== 阴谋家商店 ====================
  public static ArrayList<ShopEntry> CONSPIRATOR_SHOP = new ArrayList<>();
  // ==================== 柜子区商店 ====================
  public static ArrayList<ShopEntry> 柜子区的商店 = new ArrayList<>();
  // ==================== 滑头鬼商店 ====================
  public static ArrayList<ShopEntry> SLIPPERY_GHOST_SHOP = new ArrayList<>();
  // ==================== 工程师商店 ====================
  public static ArrayList<ShopEntry> ENGINEER_SHOP = new ArrayList<>();
  // ==================== 拳击手商店 ====================
  public static ArrayList<ShopEntry> BOXER_SHOP = new ArrayList<>();
  // ==================== 邮差商店 ====================
  public static ArrayList<ShopEntry> POSTMAN_SHOP = new ArrayList<>();
  // ==================== 心理学家商店 ====================
  public static ArrayList<ShopEntry> PSYCHOLOGIST_SHOP = new ArrayList<>();
  // ==================== 炸弹客商店 ====================
  public static ArrayList<ShopEntry> BOMBER_SHOP = new ArrayList<>();
  // ==================== 医生商店 ====================
  public static ArrayList<ShopEntry> DOCTOR_SHOP = new ArrayList<>();
  // ==================== 歌手商店 ====================
  public static ArrayList<ShopEntry> SINGER_SHOP = new ArrayList<>();
  // ==================== 乘务员商店 ====================
  public static ArrayList<ShopEntry> ATTENDANT_SHOP = new ArrayList<>();
  // ==================== 退伍军人商店 ====================
  public static ArrayList<ShopEntry> VETERAN_SHOP = new ArrayList<>();
  // ==================== 巡警商店 ====================
  public static ArrayList<ShopEntry> PATROLLER_SHOP = new ArrayList<>();
  // ==================== 年兽商店 ====================
  public static ArrayList<ShopEntry> NIAN_SHOU_SHOP = new ArrayList<>();
  // ==================== 魔术师商店 ====================
  public static ArrayList<ShopEntry> MAGICIAN_SHOP = new ArrayList<>();
  // ==================== 强盗商店 ====================
  public static ArrayList<ShopEntry> BANDIT_SHOP = new ArrayList<>();
  // ==================== 仇杀客商店 ====================
  public static ArrayList<ShopEntry> BLOOD_FEUDIST_SHOP = new ArrayList<>();
  // ==================== 小偷商店 ====================
  public static ArrayList<ShopEntry> THIEF_SHOP = new ArrayList<>();
  // ==================== 钟表匠商店 ====================
  public static ArrayList<ShopEntry> CLOCKMAKER_SHOP = new ArrayList<>();
  // ==================== 作家商店 ====================
  public static ArrayList<ShopEntry> WRITER_SHOP = new ArrayList<>();
  // ==================== 搜救员商店 ====================
  public static ArrayList<ShopEntry> RESCUER_SHOP = new ArrayList<>();
  // ==================== 消防员商店 ====================
  public static ArrayList<ShopEntry> FIREFIGHTER_SHOP = new ArrayList<>();
  // ==================== 会计商店 ====================
  public static ArrayList<ShopEntry> ACCOUNTANT_SHOP = new ArrayList<>();
  // ==================== 特警商店 ====================
  public static ArrayList<ShopEntry> SWAST_SHOP = new ArrayList<>();
  // ==================== 武术教官商店 ====================
  public static ArrayList<ShopEntry> MARTIAL_ARTS_INSTRUCTOR_SHOP = new ArrayList<>();
  // ==================== 海王商店 ====================
  public static ArrayList<ShopEntry> SEA_KING_SHOP = new ArrayList<>();
  // ==================== 水鬼商店 ====================
  public static ArrayList<ShopEntry> WATER_GHOST_SHOP = new ArrayList<>();
  // ==================== 秉烛人商店 ====================
  public static ArrayList<ShopEntry> CANDLE_BEARER_SHOP = new ArrayList<>();
  // ==================== 雇佣兵商店 ====================
  public static ArrayList<ShopEntry> MERCENARY_SHOP = new ArrayList<>();
  // ==================== 超级亡命徒商店 ====================
  public static ArrayList<ShopEntry> SUPER_LOOSE_END_SHOP = new ArrayList<>();

  /**
   * 初始化框架角色商店
   */
  public static void initializeFramingShop() {
    FRAMING_ROLES_SHOP
        .add(new ShopEntry(ModItems.MASTER_KEY_P.getDefaultInstance(), 50,
            ShopEntry.Type.TOOL));
    FRAMING_ROLES_SHOP
        .add(new ShopEntry(ModItems.DELUSION_VIAL.getDefaultInstance(), 30,
            ShopEntry.Type.POISON));
    FRAMING_ROLES_SHOP.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(), 5,
        ShopEntry.Type.TOOL));
    FRAMING_ROLES_SHOP
        .add(new ShopEntry(TMMItems.NOTE.getDefaultInstance(), 5, ShopEntry.Type.TOOL));
  }

  /**
   * 初始化水鬼商店
   * - 开锁器：100金币
   * - 下雨：150金币
   */
  public static void initializeWaterGhostShop() {
    // 开锁器 - 100金币
    WATER_GHOST_SHOP.add(new ShopEntry(
        TMMItems.LOCKPICK.getDefaultInstance(),
        100,
        ShopEntry.Type.TOOL));

    // 下雨 - 150金币（参考ma_chen_xu的狂热下雨实现，但只保留下雨能力）
    ItemStack rainItem = Items.WATER_BUCKET.getDefaultInstance();
    rainItem.set(DataComponents.ITEM_NAME,
        Component.translatable("item.noellesroles.water_ghost.rain")
            .withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD));
    var rainLore = new ArrayList<Component>();
    rainLore.add(Component.translatable("item.noellesroles.water_ghost.rain.lore1")
        .setStyle(Style.EMPTY.withItalic(false))
        .withStyle(ChatFormatting.GRAY));
    rainLore.add(Component.translatable("item.noellesroles.water_ghost.rain.lore2")
        .setStyle(Style.EMPTY.withItalic(false))
        .withStyle(ChatFormatting.GRAY));
    rainLore.add(Component.translatable("item.noellesroles.water_ghost.rain.lore3")
        .setStyle(Style.EMPTY.withItalic(false))
        .withStyle(ChatFormatting.GRAY));
    rainItem.set(DataComponents.LORE, new ItemLore(rainLore));

    WATER_GHOST_SHOP.add(new ShopEntry(rainItem, 150, ShopEntry.Type.TOOL) {
      @Override
      public boolean onBuy(@NotNull Player player) {
        var component = WaterGhostPlayerComponent.KEY.get(player);
        if (component != null) {
          return component.buyRain();
        }
        return false;
      }
    });
  }

  /**
   * 初始化仇杀客商店
   * - 撬棍：35金币
   * - 开锁器：80金币
   * - 疯狂模式：275金币（冷却30秒）
   */
  public static void initializeBloodFeudistShop() {
    // 撬棍 - 35金币
    BLOOD_FEUDIST_SHOP.add(new ShopEntry(
        TMMItems.CROWBAR.getDefaultInstance(),
        35,
        ShopEntry.Type.TOOL));

    // 开锁器 - 80金币
    BLOOD_FEUDIST_SHOP.add(new ShopEntry(
        TMMItems.LOCKPICK.getDefaultInstance(),
        80,
        ShopEntry.Type.TOOL));

    // 疯狂模式 - 275金币
    BLOOD_FEUDIST_SHOP.add(new ShopEntry(
        TMMItems.PSYCHO_MODE.getDefaultInstance(),
        275,
        ShopEntry.Type.WEAPON) {
      @Override
      public boolean onBuy(Player player) {
        var psycc = SREPlayerPsychoComponent.KEY.get(player);
        boolean success = psycc.startPsycho();
        if (success) {
          player.getCooldowns().addCooldown(TMMItems.PSYCHO_MODE, 20 * 60);
        }
        return success;
      }
    });
  }

  public static void shopRegister() {
    ShopContent.customEntries.clear();
    ShopContent.defaultKnifeEntries.clear();
    // 初始化其他角色商店
    initShops();
    // 初始化框架角色商店
    initializeFramingShop();

    // 初始化仇杀客商店
    initializeBloodFeudistShop();

    ShopContent.register();
    {
      // 布袋鬼商店（诡舍·缚灵）
      // 设计要求：无法购买刀、枪、狂暴模式，只能购买强化领域的道具
      var MA_CHEN_XU_SHOP = new ArrayList<ShopEntry>();

      // 诡舍·浊雨 - 100金币
      // 效果：30秒小雨，恐惧范围外好人每5秒掉3SAN，可与大招叠加
      ItemStack turbidRainItem = Items.BARRIER.getDefaultInstance();
      turbidRainItem.set(DataComponents.ITEM_NAME,
          Component.translatable("item.noellesroles.ma_chen_xu.turbid_rain")
              .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
      var turbidRainLore = new ArrayList<Component>();
      turbidRainLore.add(Component.translatable("item.noellesroles.ma_chen_xu.turbid_rain.lore1")
          .setStyle(Style.EMPTY.withItalic(false))
          .withStyle(ChatFormatting.GRAY));
      turbidRainLore.add(Component.translatable("item.noellesroles.ma_chen_xu.turbid_rain.lore2")
          .setStyle(Style.EMPTY.withItalic(false))
          .withStyle(ChatFormatting.GRAY));
      turbidRainItem.set(DataComponents.LORE, new ItemLore(turbidRainLore));

      MA_CHEN_XU_SHOP.add(new ShopEntry(turbidRainItem, 180, ShopEntry.Type.TOOL) {
        @Override
        public boolean onBuy(@NotNull Player player) {
          var component = org.agmas.noellesroles.component.ModComponents.MA_CHEN_XU.get(player);
          if (component != null) {
            return component.useTurbidRain();
          }
          return false;
        }
      });

      // 诡舍·镇魂铃 - 150金币
      // 效果：20格AoE，好人获得10秒耳鸣效果
      ItemStack soulBellItem = Items.BELL.getDefaultInstance();
      soulBellItem.set(DataComponents.ITEM_NAME,
          Component.translatable("item.noellesroles.ma_chen_xu.soul_bell")
              .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
      var soulBellLore = new ArrayList<Component>();
      soulBellLore.add(Component.translatable("item.noellesroles.ma_chen_xu.soul_bell.lore1")
          .setStyle(Style.EMPTY.withItalic(false))
          .withStyle(ChatFormatting.GRAY));
      soulBellLore.add(Component.translatable("item.noellesroles.ma_chen_xu.soul_bell.lore2")
          .setStyle(Style.EMPTY.withItalic(false))
          .withStyle(ChatFormatting.GRAY));
      soulBellItem.set(DataComponents.LORE, new ItemLore(soulBellLore));

      MA_CHEN_XU_SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 75, ShopEntry.Type.TOOL));
      MA_CHEN_XU_SHOP.add(new ShopEntry(soulBellItem, 105, ShopEntry.Type.TOOL) {
        @Override
        public boolean onBuy(@NotNull Player player) {
          var component = org.agmas.noellesroles.component.ModComponents.MA_CHEN_XU.get(player);
          if (component != null) {
            return component.useSoulBell();
          }
          return false;
        }
      });

      ShopContent.customEntries.put(ModRoles.MA_CHEN_XU.getIdentifier(), MA_CHEN_XU_SHOP);
    }

    {
      // FURANDORU的商店
      var SHOP = new ArrayList<ShopEntry>();
      SHOP.add(new ShopEntry(
          TMMItems.KNIFE.getDefaultInstance(),
          130,
          ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(ModItems.FAKE_REVOLVER.getDefaultInstance(), 50,
          ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(TMMItems.DERRINGER.getDefaultInstance(), 400,
          ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(), SREConfig.instance().bodyBagPrice,
          ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
          ShopEntry.Type.TOOL) {
        public boolean onBuy(@NotNull Player player) {
          player.getCooldowns().addCooldown(TMMItems.BLACKOUT,
              60 * 20);
          boolean triggered = ((SREWorldBlackoutComponent) SREWorldBlackoutComponent.KEY
              .get(player.level()))
              .triggerBlackout();
          if (triggered) {
            SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(),
                BuiltInRegistries.ITEM.getKey(TMMItems.BLACKOUT));
          }
          return triggered;
        }
      });

      SHOP.add(new ShopEntry(Items.WIND_CHARGE.getDefaultInstance(), 50,
          ShopEntry.Type.TOOL));
      ShopContent.customEntries.put(RedHouseRoles.FURANDORU.getIdentifier(), SHOP);
    }
    {
      // BAKA的商店
      var SHOP = new ArrayList<ShopEntry>();
      SHOP.add(new ShopEntry(ModItems.SIGNATURE_PAPER.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
      ShopContent.customEntries.put(ModRoles.SUPERSTAR.getIdentifier(), SHOP);
    }

    {
      // INITIATE的商店
      var SHOP = new ArrayList<ShopEntry>();
      SHOP.add(new ShopEntry(TMMItems.KNIFE.getDefaultInstance(), 200, ShopEntry.Type.WEAPON));
      ShopContent.customEntries.put(SERoles.INITIATE.getIdentifier(), SHOP);
    }
    {
      // BAKA的商店
      var SHOP = new ArrayList<ShopEntry>();
      SHOP.add(new ShopEntry(FunnyItems.PROBLEM_SET.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
      ShopContent.customEntries.put(RedHouseRoles.BAKA.getIdentifier(), SHOP);
    }
    {
      // EXAMPLER的商店
      var SHOP = new ArrayList<ShopEntry>();
      SHOP.add(new ShopEntry(
          io.wifi.starrailexpress.index.TMMItems.KNIFE.getDefaultInstance(),
          120,
          ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(
          io.wifi.starrailexpress.index.TMMItems.REVOLVER.getDefaultInstance(),
          400,
          ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(
          io.wifi.starrailexpress.index.TMMItems.LOCKPICK.getDefaultInstance(),
          50,
          ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 200,
          ShopEntry.Type.TOOL) {
        public boolean onBuy(@NotNull Player player) {
          player.getCooldowns().addCooldown(TMMItems.BLACKOUT,
              Math.min((Integer) GameConstants.ITEM_COOLDOWNS
                  .getOrDefault(TMMItems.BLACKOUT, 0), 60));
          boolean triggered = ((SREWorldBlackoutComponent) SREWorldBlackoutComponent.KEY
              .get(player.level()))
              .triggerBlackout();
          if (triggered) {
            SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(),
                BuiltInRegistries.ITEM.getKey(TMMItems.BLACKOUT));
          }
          return triggered;
        }
      });
      var psychoItem = TMMItems.PSYCHO_MODE.getDefaultInstance();
      var examplerPsychoLore = new ItemLore(
          List.of(Component.translatable(
              "itemstack.exampler.shop.psychoitem.item_lore.1")));
      psychoItem.set(DataComponents.LORE, examplerPsychoLore);

      SHOP.add(new ShopEntry(psychoItem, 0, ShopEntry.Type.WEAPON) {
        @Override
        public boolean onBuy(@NotNull Player player) {
          // 启动疯狂模式
          if (SREItemUtils.hasItem(player, TMMItems.PSYCHO_MODE) > 0) {
            if (SREPlayerPsychoComponent.KEY.get(player).startPsycho()) {
              SREItemUtils.clearItem(player, TMMItems.PSYCHO_MODE, 1);
              return true;
            }
          }
          return false;
        }
      });
      ShopContent.customEntries.put(ModRoles.EXAMPLER.getIdentifier(), SHOP);
    }
    {
      // 老人的商店
      var SHOP = new ArrayList<ShopEntry>();

      SHOP.add(new ShopEntry(ModItems.WHEELCHAIR.getDefaultInstance(), 150, ShopEntry.Type.TOOL) {
        @Override
        public boolean onBuy(@NotNull Player player) {
          if (!oldmanEasterEggTriggeredInRound && player.getRandom().nextFloat() < 0.2f) {
            var easterEggRod = createOldmanEasterEggRod();
            boolean inserted = RoleUtils.insertStackInFreeSlot(player, easterEggRod);
            if (inserted) {
              oldmanEasterEggTriggeredInRound = true;
              return true;
            }
            return false;
          }
          return super.onBuy(player);
        }
      });
      ShopContent.customEntries.put(ModRoles.OLDMAN.getIdentifier(), SHOP);
    }

    {
      // 监察员的商店
      var SHOP = new ArrayList<ShopEntry>();
      var displayer = Items.BARRIER.getDefaultInstance();
      displayer.set(DataComponents.ITEM_NAME,
          Component.translatable("gui.noellesroles.monitor.cooldown_item")
              .withStyle(ChatFormatting.RED));
      SHOP.add(new ShopEntry(displayer, 0, ShopEntry.Type.TOOL) {
        @Override
        public boolean onBuy(Player player) {
          return false;
        }
      });
      ShopContent.customEntries.put(ModRoles.MONITOR.getIdentifier(), SHOP);
    }
    {
      // 死灵法师的商店
      var NECROMANCER_SHOP = new ArrayList<ShopEntry>();

      NECROMANCER_SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100,
          ShopEntry.Type.TOOL));
      ShopContent.customEntries.put(SERoles.NECROMANCER.getIdentifier(), NECROMANCER_SHOP);
      ShopContent.customEntries.put(SERoles.ARSONIST.getIdentifier(), NECROMANCER_SHOP);
      ShopContent.customEntries.put(ModRoles.CAT_NECROMANCER.getIdentifier(), NECROMANCER_SHOP);

      {
        var CAT_KILLER_SHOP = new ArrayList<>(NECROMANCER_SHOP);
        // 只是给某些特定情况下启用，比如赌徒。
        CAT_KILLER_SHOP.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(), 0, ShopEntry.Type.WEAPON) {
          @Override
          public boolean onBuy(@NotNull Player player) {
            return SREPlayerShopComponent.usePsychoMode(player);
          }
        });
        ShopContent.customEntries.put(ModRoles.CAT_KILLER.getIdentifier(), CAT_KILLER_SHOP);
      }
    }
    {
      // 忍者商店
      var NINJA_SHOP = new ArrayList<ShopEntry>();

      // 苦无 - 130金币
      NINJA_SHOP.add(new ShopEntry(ModItems.NINJA_KNIFE.getDefaultInstance(), 130, ShopEntry.Type.WEAPON));

      // 手里剑 - 275金币
      NINJA_SHOP.add(new ShopEntry(ModItems.NINJA_SHURIKEN.getDefaultInstance(), 275, ShopEntry.Type.WEAPON));

      // 关灯 - 50金币
      NINJA_SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 50, ShopEntry.Type.TOOL) {
        public boolean onBuy(@NotNull Player player) {
          if (SREPlayerShopComponent.useBlackoutWithMultiplier(player, 0.4)) {
            player.getCooldowns().addCooldown(TMMItems.BLACKOUT,
                Math.max(GameConstants.getBlackoutCooldownGlobal(),
                    GameConstants.ITEM_COOLDOWNS.get(TMMItems.BLACKOUT) / 5));
            return true;
          }
          return false;
        }
      });

      // 撬锁器 - 75金币
      NINJA_SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 75, ShopEntry.Type.TOOL));

      ShopContent.customEntries.put(ModRoles.NINJA_ID, NINJA_SHOP);
    }
    {
      // 厨师的商店
      var shop = new ArrayList<ShopEntry>();
      shop.add(new ShopEntry(ModItems.A_BOTTLE_OF_WATER.getDefaultInstance(), 50,
          ShopEntry.Type.TOOL));
      shop.add(new ShopEntry(ModItems.LINGSHI.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
      shop.add(new ShopEntry(ModItems.PAN.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
      ShopContent.customEntries.put(ModRoles.CHEF_ID, shop);
    }
    {
      // 指挥官的商店
      var _SHOP = new ArrayList<ShopEntry>();
      _SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
      ShopContent.customEntries.put(ModRoles.COMMANDER_ID, _SHOP);
    }
    {
      // 雇佣兵商店
      var shop = new ArrayList<ShopEntry>();

      // 未签订契约 - 75金币
      shop.add(new ShopEntry(ModItems.MERCENARY_CONTRACT.getDefaultInstance(), 75, ShopEntry.Type.TOOL));

      // 护盾层 - 150金币
      ItemStack shieldItem = Items.SHIELD.getDefaultInstance();
      shieldItem.set(DataComponents.ITEM_NAME,
          Component.translatable("item.noellesroles.mercenary_guard").withStyle(ChatFormatting.AQUA));
      shop.add(new ShopEntry(shieldItem, 150, ShopEntry.Type.TOOL) {
        @Override
        public boolean onBuy(@NotNull Player player) {
          var gameWorld = SREGameWorldComponent.KEY.get(player.level());
          if (!gameWorld.isRole(player, ModRoles.MERCENARY)) {
            return false;
          }
          var mercenary = MercenaryPlayerComponent.KEY.get(player);
          return mercenary.onBoughtShieldLayer();
        }
      });

      // 撬锁器 - 100金币
      shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100, ShopEntry.Type.TOOL));

      // 德林加 - 300金币
      shop.add(new ShopEntry(TMMItems.DERRINGER.getDefaultInstance(), 300, ShopEntry.Type.WEAPON));

      // 刀 - 130金币
      shop.add(new ShopEntry(TMMItems.KNIFE.getDefaultInstance(), 130, ShopEntry.Type.WEAPON));

      ShopContent.customEntries.put(ModRoles.MERCENARY_ID, shop);
    }
    {
      // 游侠商店
      var shopEntries = new ArrayList<ShopEntry>();
      shopEntries.add(new ShopEntry(Items.CROSSBOW.getDefaultInstance(), 300, ShopEntry.Type.WEAPON) {
        @Override
        public boolean onBuy(@NotNull Player player) {
          int itemCount = SREItemUtils.hasItem(player, Items.CROSSBOW);
          if (itemCount > 0)
            return false;
          ItemStack item = Items.CROSSBOW.getDefaultInstance();
          item.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
          return RoleUtils.insertStackInFreeSlot(player, item);
        }
      });

      final var PoisonArrow = Items.TIPPED_ARROW.getDefaultInstance();
      PoisonArrow.set(DataComponents.ITEM_NAME, Component.translatable("item.poison_arrow.name"));
      PoisonArrow.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
      PoisonArrow.set(DataComponents.MAX_STACK_SIZE, 1);
      shopEntries.add(new ShopEntry(PoisonArrow, 75, ShopEntry.Type.WEAPON) {
        @Override
        public boolean onBuy(@NotNull Player player) {
          int itemCount = SREItemUtils.hasItem(player, Items.TIPPED_ARROW);
          if (itemCount >= 2)
            return false;
          return RoleUtils.insertStackInFreeSlot(player, PoisonArrow.copy());
        }
      });

      final var SpectralArrow = Items.SPECTRAL_ARROW.getDefaultInstance();
      SpectralArrow.set(DataComponents.MAX_STACK_SIZE, 1);

      shopEntries.add(new ShopEntry(SpectralArrow, 50, ShopEntry.Type.WEAPON) {
        @Override
        public boolean onBuy(@NotNull Player player) {
          int itemCount = SREItemUtils.hasItem(player, Items.SPECTRAL_ARROW);
          if (itemCount >= 2)
            return false;
          return RoleUtils.insertStackInFreeSlot(player, SpectralArrow.copy());
        }
      });
      ShopContent.customEntries.put(
          ModRoles.ELF_ID, shopEntries);
    }
    {
      ShopContent.customEntries.put(
          ModRoles.MANIPULATOR_ID, ShopContent.defaultKnifeEntries);
    }
    {
      var SPEED_SPLASH_POITION = Items.SPLASH_POTION.getDefaultInstance();
      var speedPotionList = List.of(new MobEffectInstance(
          MobEffects.MOVEMENT_SPEED,
          60 * 20, // 持续时间（tick）
          2, // 等级（0 = 速度 I）
          false, // ambient（环境效果，如信标）
          true, // showParticles（显示粒子）
          true // showIcon（显示图标）
      ));
      var speedPotionContent = new PotionContents(Optional.empty(), Optional.of(53503),
          speedPotionList);
      SPEED_SPLASH_POITION.set(DataComponents.POTION_CONTENTS, speedPotionContent);
      var shopEntries = new ArrayList<ShopEntry>();
      shopEntries.add(new ShopEntry(SPEED_SPLASH_POITION, 275, ShopEntry.Type.WEAPON));
      ShopContent.customEntries.put(
          ModRoles.ATHLETE_ID, shopEntries);
    }
    {
      ShopContent.customEntries.put(
          ModRoles.EXECUTIONER_ID, 柜子区的商店);
    }
    {
      List<ShopEntry> entries = new ArrayList<>(ShopContent.defaultKnifeEntries);
      entries.add(new ShopEntry(
          ModItems.HALLUCINATION_BOTTLE.getDefaultInstance(),
          120,
          ShopEntry.Type.TOOL));

      ShopContent.customEntries.put(
          ModRoles.MORPHLING_ID, entries);
    }
    ShopContent.customEntries.put(
        ModRoles.POISONER_ID, ModItems.POISONER_SHOP_ENTRIES);

    ShopContent.customEntries.put(
        ModRoles.SWAPPER_ID, ShopContent.defaultKnifeEntries);

    // 仇杀客商店
    ShopContent.customEntries.put(
        ModRoles.BLOOD_FEUDIST_ID, BLOOD_FEUDIST_SHOP);

    // ShopContent.customEntries.put(
    // POISONER_ID, ShopContent.defaultEntries
    // );
    // ShopContent.customEntries.put(
    // ModRoles.BANDIT_ID, HSRConstants.BANDIT_SHOP_ENTRIES);
    ShopContent.customEntries.put(
        ModRoles.JESTER_ID, FRAMING_ROLES_SHOP);
    {
      // DIO
      List<ShopEntry> entries = new ArrayList<>();
      entries.add(new ShopEntry(ModItems.THROWING_KNIFE.getDefaultInstance(), 120,
          ShopEntry.Type.TOOL));
      entries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 95,
          ShopEntry.Type.TOOL));
      entries.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 175,
          ShopEntry.Type.TOOL) {
        public boolean onBuy(@NotNull Player player) {
          player.getCooldowns().addCooldown(TMMItems.BLACKOUT,
              Math.min((Integer) GameConstants.ITEM_COOLDOWNS
                  .getOrDefault(TMMItems.BLACKOUT, 0), 60));
          boolean triggered = ((SREWorldBlackoutComponent) SREWorldBlackoutComponent.KEY
              .get(player.level()))
              .triggerBlackout();
          if (triggered) {
            SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(),
                BuiltInRegistries.ITEM.getKey(TMMItems.BLACKOUT));
          }
          return triggered;
        }
      });
      ShopContent.customEntries.put(
          ModRoles.DIO_ID, entries);
    }
    {
      // 女仆咲夜
      List<ShopEntry> entries = new ArrayList<>();
      var SAKUYA_KNIFE = ModItems.THROWING_KNIFE.getDefaultInstance();
      SAKUYA_KNIFE.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
      entries.add(new ShopEntry(SAKUYA_KNIFE, 250,
          ShopEntry.Type.TOOL));
      entries.add(new ShopEntry(FunnyItems.SHISIYE.getDefaultInstance(), 440,
          ShopEntry.Type.TOOL));
      ShopContent.customEntries.put(
          RedHouseRoles.MAID_SAKUYA_ID, entries);
    }
    {
      List<ShopEntry> entries = new ArrayList<>();
      entries.add(new ShopEntry(TMMItems.DEFENSE_VIAL.getDefaultInstance(), 200,
          ShopEntry.Type.POISON));

      ShopContent.customEntries.put(
          ModRoles.BARTENDER_ID, entries);
    }
    {
      // 大嗓门商店已删除
    }

    // {
    // List<ShopEntry> entries = new ArrayList<>();
    // entries.add(new ShopEntry(ModItems.SHERIFF_GUN_MAINTENANCE.getDefaultStack(),
    // 150, ShopEntry.Type.TOOL));
    //
    // ShopContent.customEntries.put(
    // SHERIFF_ID, entries
    // );
    // }
    {
      List<ShopEntry> entries = new ArrayList<>();
      // 拍立得相机 - 75金币
      {
        var item = ExposurePolaroid.Items.INSTANT_CAMERA.get();
        if (item != null) {
          final var defaultInstance = item.getDefaultInstance();
          entries.add(new ShopEntry(defaultInstance, 75, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
              player.addItem(defaultInstance.copy());
              return true;
            }
          });
        }
      }
      // 拍立得相纸 - 75金币
      {
        var item = ExposurePolaroid.Items.INSTANT_COLOR_SLIDE.get();
        if (item != null) {
          final var defaultInstance = item.getDefaultInstance();
          entries.add(new ShopEntry(defaultInstance, 75, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
              player.addItem(defaultInstance.copy());
              return true;
            }
          });
        }
      }

      ShopContent.customEntries.put(
          ModRoles.PHOTOGRAPHER_ID, entries);
    }
    {
      ShopContent.customEntries.put(
          ModRoles.AWESOME_BINGLUS_ID,
          List.of(
              new ShopEntry(TMMItems.NOTE.getDefaultInstance(), 10, ShopEntry.Type.TOOL),
              new ShopEntry(ModItems.GIANT_NOTE.getDefaultInstance(), 75, ShopEntry.Type.TOOL)));
    }
    {
      ShopContent.customEntries.put(
          ModRoles.CONSPIRATOR_ID, CONSPIRATOR_SHOP);
    }
    {
      ShopContent.customEntries.put(
          ModRoles.SLIPPERY_GHOST_ID, SLIPPERY_GHOST_SHOP);
    }
    {
      // PACHURI 商店
      var displayStack = Items.WRITTEN_BOOK.getDefaultInstance();
      String title = "\u00a7d\u00a7lPachuri Knowledge Book";
      displayStack.set(DataComponents.WRITTEN_BOOK_CONTENT,
          new WrittenBookContent(new Filterable<String>(title, Optional.of(title)), "Pachuri Knowledge", 1, List.of(),
              true));
      var SHOP = new ArrayList<ShopEntry>();
      SHOP.add(new ShopEntry(
          displayStack,
          125,
          ShopEntry.Type.TOOL) {
        @Override
        public boolean onBuy(Player player) {
          var itemStack = Items.WRITTEN_BOOK.getDefaultInstance();
          var players = new ArrayList<>(player.level().players());
          var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
          players.removeIf((p) -> {
            return gameWorldComponent.getRole(p) == null || p.isSpectator();
          });
          Collections.shuffle(players);
          int count = 1;
          var contents = new ArrayList<Filterable<Component>>();
          {
            var fstct = Component.translatable("%s\n%s", Component.translatable("item.written_book.role_title")
                .withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD),
                Component.translatable("item.written_book.role_intro")
                    .withStyle(ChatFormatting.GRAY));
            var fstcontent = new Filterable<Component>(fstct, Optional.of(fstct));
            contents.add(fstcontent);
          }
          for (int i = 0; i < count; i++) {
            var p = players.get(i);
            var ct = Component.translatable("%s\n%s", Component.translatable("item.written_book.per_role_title", i + 1)
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD),
                Component
                    .translatable("item.written_book.per_role_content",
                        p.getName().copy().withStyle(ChatFormatting.DARK_GRAY),
                        RoleUtils.getRoleOrModifierNameWithColor(gameWorldComponent.getRole(p)))
                    .withStyle(ChatFormatting.DARK_AQUA));
            var content = new Filterable<Component>(ct, Optional.of(ct));
            contents.add(content);
            if (p instanceof ServerPlayer sp)
              BroadcastCommand.BroadcastMessage(sp,
                  Component.translatable("message.pachuri.be_known_role").withStyle(ChatFormatting.RED));
          }
          String title = "\u00a7d\u00a7lPachuri Knowledge Book";

          itemStack.set(DataComponents.WRITTEN_BOOK_CONTENT,
              new WrittenBookContent(new Filterable<String>(title, Optional.of(title)), "System", 1, contents, true));
          return RoleUtils.insertStackInFreeSlot(player, itemStack);
        }
      });
      ShopContent.customEntries.put(
          RedHouseRoles.PACHURI_ID, SHOP);
    }
    {
      // 锁匠商店
      var SHOP = new ArrayList<ShopEntry>();
      SHOP.add(new ShopEntry(
          ModItems.SCREWDRIVER.getDefaultInstance(),
          100,
          ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(
          ModItems.NOELL_KEY_BLANK.getDefaultInstance(),
          150,
          ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(
          ModItems.NOELL_PAPERCLIP.getDefaultInstance(),
          75,
          ShopEntry.Type.TOOL));
      ShopContent.customEntries.put(
          ModRoles.LOCKSMITH_ID, SHOP);
    }
    {
      // 船长商店
      var SHOP = new ArrayList<ShopEntry>();
      SHOP.add(new ShopEntry(
          ModItems.SCREWDRIVER.getDefaultInstance(),
          100,
          ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(
          ModItems.MASTER_KEY.getDefaultInstance(),
          400,
          ShopEntry.Type.TOOL));
      ShopContent.customEntries.put(
          ModRoles.CONDUCTOR_ID, SHOP);
    }
    {
      ShopContent.customEntries.put(
          ModRoles.ENGINEER_ID, ENGINEER_SHOP);
    }
    {
      ShopContent.customEntries.put(
          ModRoles.BOXER_ID, BOXER_SHOP);
    }
    {
      var shopEntries = new ArrayList<ShopEntry>();
      shopEntries.add(new ShopEntry(TMMItems.KNIFE.getDefaultInstance(), 220, ShopEntry.Type.TOOL));
      shopEntries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100,
          ShopEntry.Type.TOOL));
      shopEntries.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(), 500, ShopEntry.Type.TOOL));
      shopEntries.add(new ShopEntry(TMMItems.NOTE.getDefaultInstance(), 15, ShopEntry.Type.TOOL));
      shopEntries.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(), 15,
          ShopEntry.Type.TOOL));
      ShopContent.customEntries.put(
          ModRoles.CLEANER_ID,
          shopEntries);
    }
    {
      ShopContent.customEntries.put(
          ModRoles.ADMIRER_ID,
          List.of(new ShopEntry(ModItems.MASTER_KEY_P.getDefaultInstance(), 150,
              ShopEntry.Type.TOOL)));
    }
    {
      ShopContent.customEntries.put(
          ModRoles.POSTMAN_ID, POSTMAN_SHOP);
    }

    ShopContent.customEntries.put(
        ModRoles.STALKER_ID,
        List.of(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 75,
            ShopEntry.Type.TOOL),
            new ShopEntry(ModItems.STALKER_KNIFE_OFFHAND.getDefaultInstance(), 325, ShopEntry.Type.WEAPON) {
              @Override
              public boolean canBuy(@NotNull Player player) {
                return !(player.getOffhandItem().getItem() instanceof KnifeItem);
              }

              @Override
              public boolean canDisplay(@NotNull Player player) {
                return StalkerPlayerComponent.KEY.get(player).phase >= 2;
              }

              @Override
              public boolean onBuy(@NotNull Player player) {

                boolean b = player.getOffhandItem().getItem() instanceof KnifeItem;
                if (!b) {
                  player.setItemInHand(InteractionHand.OFF_HAND, ModItems.STALKER_KNIFE_OFFHAND.getDefaultInstance());
                }
                return b;
              }
            }));

    // 心理学家商店
    {
      ShopContent.customEntries.put(
          ModRoles.PSYCHOLOGIST_ID, PSYCHOLOGIST_SHOP);
    }

    // 乘务员商店
    {
      // 乘务员专属商店 - 添加原版灯笼，价格 75 金币
      ItemStack attendantLantern = Items.LANTERN.getDefaultInstance();
      ATTENDANT_SHOP.add(new ShopEntry(attendantLantern, 75, ShopEntry.Type.TOOL) {
        @Override
        public boolean onBuy(@NotNull Player player) {
          return RoleUtils.insertStackInFreeSlot(player, attendantLantern.copy());
        }
      });

      ShopContent.customEntries.put(
          ModRoles.ATTENDANT_ID, ATTENDANT_SHOP);
    }

    // 操纵师商店

    {
      ShopContent.customEntries.put(
          ModRoles.BOMBER_ID, BOMBER_SHOP);
    }
    {
      ShopContent.customEntries.put(
          ModRoles.DOCTOR_ID, DOCTOR_SHOP);
    }
    {
      ShopContent.customEntries.put(
          ModRoles.SINGER_ID, SINGER_SHOP);
    }

    // 退伍军人商店
    {
      VETERAN_SHOP.add(new ShopEntry(
          TMMItems.KNIFE.getDefaultInstance(),
          250,
          ShopEntry.Type.WEAPON));
      ShopContent.customEntries.put(
          ModRoles.VETERAN_ID, VETERAN_SHOP);
    }

    // 年兽商店
    {
      ShopContent.customEntries.put(
          ModRoles.NIAN_SHOU_ID, NIAN_SHOU_SHOP);
    }
    // 魔术师商店
    {
      ShopContent.customEntries.put(
          ModRoles.MAGICIAN_ID, MAGICIAN_SHOP);
    }
    // 强盗商店
    {
      ShopContent.customEntries.put(
          ModRoles.BANDIT_ID, BANDIT_SHOP);
    }
    // 小偷商店
    {
      ShopContent.customEntries.put(
          ModRoles.THIEF_ID, THIEF_SHOP);
    }
    // 钟表匠商店
    {
      ShopContent.customEntries.put(
          ModRoles.CLOCKMAKER_ID, CLOCKMAKER_SHOP);
    }
    // 作家商店
    {
      ShopContent.customEntries.put(
          ModRoles.WRITER_ID, WRITER_SHOP);
    }
    // 搜救员商店
    {
      ShopContent.customEntries.put(
          ModRoles.RESCUER_ID, RESCUER_SHOP);
    }
    // 消防员商店
    {
      ShopContent.customEntries.put(
          ModRoles.FIREFIGHTER_ID, FIREFIGHTER_SHOP);
    }
    // 会计商店
    {
      ShopContent.customEntries.put(
          ModRoles.ACCOUNTANT_ID, ACCOUNTANT_SHOP);
    }
    // 风精灵
    {
      List<ShopEntry> entries = new ArrayList<>();
      entries.add(new ShopEntry(Items.WIND_CHARGE.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
      ShopContent.customEntries.put(
          ModRoles.WIND_YAOSE_ID, entries);
    }
    // 警长商店
    {
      List<ShopEntry> entries = new ArrayList<>();
      entries.add(new ShopEntry(ModItems.HANDCUFFS.getDefaultInstance(), 150, ShopEntry.Type.TOOL));
      ShopContent.customEntries.put(
          TMMRoles.VIGILANTE.identifier(), entries);
    }
    // 巡警商店
    {
      ShopContent.customEntries.put(
          ModRoles.PATROLLER_ID, PATROLLER_SHOP);
    }
    // 特警商店
    {
      ShopContent.customEntries.put(
          ModRoles.SWAST_ID, SWAST_SHOP);
    }
    // 武术教官商店
    {
      ShopContent.customEntries.put(
          ModRoles.MARTIAL_ARTS_INSTRUCTOR_ID, MARTIAL_ARTS_INSTRUCTOR_SHOP);
    }
    // 海王商店
    {
      ShopContent.customEntries.put(
          ModRoles.SEA_KING_ID, SEA_KING_SHOP);
    }

    // 水鬼商店
    {
      ShopContent.customEntries.put(
          ModRoles.WATER_GHOST_ID, WATER_GHOST_SHOP);
    }

    // 秉烛人商店
    {
      ShopContent.customEntries.put(
          ModRoles.CANDLE_BEARER_ID, CANDLE_BEARER_SHOP);
    }

    // 超级亡命徒商店
    {
      ShopContent.customEntries.put(
              ModRoles.SUPER_LOOSE_END_ID, SUPER_LOOSE_END_SHOP);
    }

    // 故障机器人商店
    {
      List<ShopEntry> glitchRobotShop = new ArrayList<>();
      // 夜视仪 - 100金币
      glitchRobotShop
          .add(new ShopEntry(ModItems.NIGHT_VISION_GLASSES.getDefaultInstance(), 100,
              ShopEntry.Type.TOOL));
      // 萤石粉 - 25金币（修复夜视仪）
      glitchRobotShop.add(new ShopEntry(Items.GLOWSTONE_DUST.getDefaultInstance(), 25,
          ShopEntry.Type.TOOL) {
        @Override
        public boolean onBuy(@NotNull Player player) {
          var head = player.getSlot(103).get();
          if (head.is(ModItems.NIGHT_VISION_GLASSES)) {
            int damage = head.getDamageValue();
            if (damage >= 25) {
              head.setDamageValue(damage - 25);
            } else {
              return false;
            }
          } else {
            return false;
          }
          return true;
        }
      });
      ShopContent.customEntries.put(ModRoles.GLITCH_ROBOT_ID, glitchRobotShop);
    }

    // 潜水员商店
    {
      List<ShopEntry> diverShop = new ArrayList<>();
      // 潜水头盔 - 125金币
      diverShop.add(new ShopEntry(ModItems.DIVING_HELMET.getDefaultInstance(), 125,
          ShopEntry.Type.TOOL));
      // 潜水靴 - 225金币
      diverShop.add(new ShopEntry(ModItems.DIVING_BOOTS.getDefaultInstance(), 225,
          ShopEntry.Type.TOOL));
      ShopContent.customEntries.put(ModRoles.DIVER_ID, diverShop);
    }
    {
      // 诡客的商店
      List<ShopEntry> shop = new ArrayList<>();
      // 净雨符
      {
        ItemStack it = Items.POTION.getDefaultInstance();
        it.set(DataComponents.ITEM_NAME, Component.translatable("item.noellesroles.guest_ghost.stop_raining"));
        var rainLore = new ArrayList<Component>();
        rainLore.add(Component.translatable("item.noellesroles.guest_ghost.stop_raining.tooltip1")
            .setStyle(Style.EMPTY.withItalic(false))
            .withStyle(ChatFormatting.GRAY));
        it.set(DataComponents.LORE, new ItemLore(rainLore));
        shop.add(new ShopEntry(it, 200,
            ShopEntry.Type.TOOL) {
          @Override
          public boolean onBuy(Player player) {
            if (player.getCooldowns().isOnCooldown(Items.BARRIER)) {
              return false;
            }
            player.getCooldowns().addCooldown(Items.BARRIER, 60 * 20);
            if (player instanceof ServerPlayer sp) {
              final SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(sp.level());
              List<ServerPlayer> players = sp.serverLevel().players();
              for (var p : players) {
                if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)) {
                  p.removeEffect(ModEffects.INFINITE_STAMINA);
                  if (gameWorldComponent.isRole(p, ModRoles.MA_CHEN_XU)) {
                    var mapc = MaChenXuPlayerComponent.KEY.get(p);
                    if (mapc.otherworldActive) {
                      mapc.otherworldDuration = 1;
                    }
                  } else {
                    SRERole role = gameWorldComponent.getRole(p);
                    if (role != null) {
                      if (role.getMoodType().equals(SRERole.MoodType.REAL)) {
                        SREPlayerMoodComponent.KEY.get(p).addMood(0.2f);
                      }
                    }
                  }
                }
              }
            }
            return true;
          }
        });
      }
      // 桃木钉
      {
        ItemStack it = Items.WOODEN_SWORD.getDefaultInstance();
        it.set(DataComponents.ITEM_NAME, Component.translatable("item.noellesroles.guest_ghost.taomuding"));
        var rainLore = new ArrayList<Component>();
        rainLore.add(Component.translatable("item.noellesroles.guest_ghost.taomuding.tooltip1")
            .setStyle(Style.EMPTY.withItalic(false))
            .withStyle(ChatFormatting.GRAY));
        it.set(DataComponents.LORE, new ItemLore(rainLore));
        shop.add(new ShopEntry(it, 150,
            ShopEntry.Type.TOOL) {
          @Override
          public boolean onBuy(Player player) {
            if (player.getCooldowns().isOnCooldown(Items.WOODEN_SWORD)) {
              return false;
            }
            player.getCooldowns().addCooldown(Items.WOODEN_SWORD, 60 * 20);
            if (player instanceof ServerPlayer sp) {
              final SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(sp.level());
              List<ServerPlayer> players = sp.serverLevel().players();
              for (var p : players) {
                if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)) {
                  if (gameWorldComponent.isRole(p, ModRoles.MA_CHEN_XU)) {
                    var mapc = MaChenXuPlayerComponent.KEY.get(p);
                    // 增加所有鬼术冷却30秒
                    mapc.ghostWallCooldown += 20 * 30;
                    mapc.echoCooldown += 20 * 30;
                    mapc.trapCooldown += 20 * 30;
                    mapc.parasiteCooldown += 20 * 30;
                    mapc.ultimateCooldown += 20 * 30;
                    // 移除护盾
                    mapc.permanentShield = false;
                    p.displayClientMessage(
                        Component.translatable("message.noellesroles.ma_chen_xu.into_cooldown_by_guest")
                            .withStyle(ChatFormatting.RED),
                        true);
                    p.playNotifySound(TMMSounds.ITEM_PSYCHO_ARMOUR, SoundSource.MASTER, 1f, 1f);
                  }
                }
              }
            }
            return true;
          }
        });
      }

      ShopContent.customEntries.put(ModRoles.GUEST_GHOST_ID, shop);
    }
    {
      // watcher
      var shop = new ArrayList<ShopEntry>();
      shop.add(
          new ShopEntry(TMMItems.KNIFE.getDefaultInstance(), SREConfig.instance().knifePrice, ShopEntry.Type.WEAPON) {
            @Override
            public boolean canDisplay(Player player) {
              return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
            }
          });
      // 没有枪
      shop.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(), SREConfig.instance().grenadePrice,
          ShopEntry.Type.WEAPON) {
        @Override
        public boolean canDisplay(Player player) {
          return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
        }
      });
      shop.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(),
          SREConfig.instance().psychoModePrice, ShopEntry.Type.WEAPON) {
        @Override
        public boolean canDisplay(Player player) {
          return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
        }

        @Override
        public boolean onBuy(@NotNull Player player) {
          return SREPlayerShopComponent.usePsychoMode(player);
        }
      });
      shop.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(),
          SREConfig.instance().firecrackerPrice, ShopEntry.Type.TOOL) {
        @Override
        public boolean canDisplay(Player player) {
          return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
        }
      });
      shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), SREConfig.instance().lockpickPrice,
          ShopEntry.Type.TOOL) {
        @Override
        public boolean canDisplay(Player player) {
          return true;
        }
      });
      shop.add(
          new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(), SREConfig.instance().crowbarPrice, ShopEntry.Type.TOOL) {
            @Override
            public boolean canDisplay(Player player) {
              return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
            }
          });
      shop.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(), SREConfig.instance().bodyBagPrice,
          ShopEntry.Type.TOOL) {
        @Override
        public boolean canDisplay(Player player) {
          return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
        }
      });
      shop.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
          ShopEntry.Type.TOOL) {
        @Override
        public boolean canDisplay(Player player) {
          return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
        }

        @Override
        public boolean onBuy(@NotNull Player player) {
          return SREPlayerShopComponent.useBlackout(player);
        }
      });
      shop
          .add(new ShopEntry(new ItemStack(TMMItems.NOTE, 4), SREConfig.instance().notePrice, ShopEntry.Type.TOOL) {
            @Override
            public boolean canDisplay(Player player) {
              return true;
            }
          });
      ShopContent.customEntries.put(ModRoles.WATCHER_ID, shop);
    }
  }

  /**
   * 初始化商店
   */
  public static void initShops() {
    FRAMING_ROLES_SHOP.clear();
    CONSPIRATOR_SHOP.clear();
    柜子区的商店.clear();
    SLIPPERY_GHOST_SHOP.clear();
    ENGINEER_SHOP.clear();
    BOXER_SHOP.clear();
    POSTMAN_SHOP.clear();
    PSYCHOLOGIST_SHOP.clear();
    BOMBER_SHOP.clear();
    DOCTOR_SHOP.clear();
    SINGER_SHOP.clear();
    ATTENDANT_SHOP.clear();
    VETERAN_SHOP.clear();
    PATROLLER_SHOP.clear();
    NIAN_SHOU_SHOP.clear();
    MAGICIAN_SHOP.clear();
    BANDIT_SHOP.clear();
    BLOOD_FEUDIST_SHOP.clear();
    THIEF_SHOP.clear();
    CLOCKMAKER_SHOP.clear();
    WRITER_SHOP.clear();
    RESCUER_SHOP.clear();
    FIREFIGHTER_SHOP.clear();
    ACCOUNTANT_SHOP.clear();
    SWAST_SHOP.clear();
    MARTIAL_ARTS_INSTRUCTOR_SHOP.clear();
    SEA_KING_SHOP.clear();
    WATER_GHOST_SHOP.clear();
    CANDLE_BEARER_SHOP.clear();

    柜子区的商店.add(new ShopEntry(
        ModItems.BANDIT_REVOLVER.getDefaultInstance(),
        130,
        ShopEntry.Type.TOOL));
    柜子区的商店.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(), SREConfig.instance().firecrackerPrice,
        ShopEntry.Type.TOOL));
    柜子区的商店.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
    柜子区的商店.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(), SREConfig.instance().bodyBagPrice,
        ShopEntry.Type.TOOL));
    柜子区的商店.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
        ShopEntry.Type.TOOL) {
      public boolean onBuy(@NotNull Player player) {

        boolean triggered = SREPlayerShopComponent.useBlackout(player);
        if (triggered) {
          player.getCooldowns().addCooldown(TMMItems.BLACKOUT,
              Math.max(60 * 20, GameConstants.getBlackoutCooldownGlobal()));
          return true;
        }
        return triggered;
      }
    });
    {
      // 射击狂热 - 275金币（魔改psycho，狂暴模式）
      var 柜子区疯魔 = TMMItems.PSYCHO_MODE.getDefaultInstance();
      柜子区疯魔.set(DataComponents.ITEM_NAME, Component.translatable("itemstack.executioner.psychoitem.item_name"));
      var lore = new ItemLore(List.of(
          Component.translatable("itemstack.executioner.psychoitem.item_lore.1")
              .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)),
          Component.translatable("itemstack.executioner.psychoitem.item_lore.2")
              .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY))));
      柜子区疯魔.set(DataComponents.LORE, lore);
      柜子区的商店.add(new ShopEntry(
          柜子区疯魔,
          325,
          ShopEntry.Type.WEAPON) {
        @Override
        public boolean onBuy(@NotNull Player player) {
          ShootingFrenzyPlayerComponent frenzyComponent = ShootingFrenzyPlayerComponent.KEY.get(player);
          boolean success = frenzyComponent.startFrenzy();
          if (success) {
            player.getCooldowns().addCooldown(TMMItems.PSYCHO_MODE, 20 * 60);
          }
          return success;
        }
      });
    }

    // 阴谋家商店
    CONSPIRATOR_SHOP.add(new ShopEntry(
        ModItems.CONSPIRACY_PAGE.getDefaultInstance(),
        100,
        ShopEntry.Type.TOOL));

    CONSPIRATOR_SHOP.add(new ShopEntry(
        io.wifi.starrailexpress.index.TMMItems.KNIFE.getDefaultInstance(),
        120,
        ShopEntry.Type.TOOL));

    CONSPIRATOR_SHOP.add(new ShopEntry(
        io.wifi.starrailexpress.index.TMMItems.REVOLVER.getDefaultInstance(),
        200,
        ShopEntry.Type.WEAPON));

    CONSPIRATOR_SHOP.add(new ShopEntry(
        io.wifi.starrailexpress.index.TMMItems.LOCKPICK.getDefaultInstance(),
        50,
        ShopEntry.Type.TOOL));

    // 滑头鬼商店
    // 空包弹 - 150金币
    SLIPPERY_GHOST_SHOP.add(new ShopEntry(
        ModItems.BLANK_CARTRIDGE.getDefaultInstance(),
        150,
        ShopEntry.Type.TOOL));

    // 烟雾弹 - 150金币
    SLIPPERY_GHOST_SHOP.add(new ShopEntry(
        ModItems.SMOKE_GRENADE.getDefaultInstance(),
        150,
        ShopEntry.Type.TOOL));

    // 撬锁器 - 50金币 (原版杀手商店物品)
    SLIPPERY_GHOST_SHOP.add(new ShopEntry(
        io.wifi.starrailexpress.index.TMMItems.LOCKPICK.getDefaultInstance(),
        50,
        ShopEntry.Type.TOOL));

    // 关灯 - 300金币 (原版杀手商店物品)
    SLIPPERY_GHOST_SHOP.add(
        new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
            ShopEntry.Type.TOOL) {
          public boolean onBuy(@NotNull Player player) {
            return SREPlayerShopComponent.useBlackout(player);
          }
        });

    // 工程师商店
    // 加固门 - 30金币
    ENGINEER_SHOP.add(new ShopEntry(
        ModItems.REINFORCEMENT.getDefaultInstance(),
        30,
        ShopEntry.Type.TOOL));

    // 警报陷阱 - 15金币
    ENGINEER_SHOP.add(new ShopEntry(
        ModItems.ALARM_TRAP.getDefaultInstance(),
        15,
        ShopEntry.Type.TOOL));

    ENGINEER_SHOP.add(new ShopEntry(
        ModItems.MASTER_KEY_P.getDefaultInstance(),
        90,
        ShopEntry.Type.TOOL));

    ENGINEER_SHOP.add(new ShopEntry(
        ModItems.LOCK_ITEM.getDefaultInstance(),
        100,
        ShopEntry.Type.TOOL));

    // 年兽商店
    // 关灯 - 200金币
    NIAN_SHOU_SHOP.add(
        new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 200, ShopEntry.Type.TOOL) {
          public boolean onBuy(@NotNull Player player) {
            return SREPlayerShopComponent.useBlackout(player);
          }
        });

    // 拳击手商店
    BOXER_SHOP.add(new ShopEntry(
        ModItems.BOXING_GLOVE.getDefaultInstance(),
        150,
        ShopEntry.Type.WEAPON));

    // 邮差商店
    // 传递盒 - 100金币
    POSTMAN_SHOP.add(new ShopEntry(
        ModItems.DELIVERY_BOX.getDefaultInstance(),
        100,
        ShopEntry.Type.TOOL));

    // 心理学家商店
    // 薄荷糖 - 75金币
    PSYCHOLOGIST_SHOP.add(new ShopEntry(
        ModItems.MINT_CANDIES.getDefaultInstance(),
        75,
        ShopEntry.Type.TOOL));
    // 维生素 - 125金币
    PSYCHOLOGIST_SHOP.add(new ShopEntry(
        ModItems.ALCHEMIST_BUFF_POTION.getDefaultInstance(),
        125,
        ShopEntry.Type.TOOL));
    // 炸弹客商店
    BOMBER_SHOP.add(new ShopEntry(
        TMMItems.GRENADE.getDefaultInstance(),
        275,
        ShopEntry.Type.WEAPON));
    BOMBER_SHOP.add(new ShopEntry(
        TMMItems.FIRECRACKER.getDefaultInstance(),
        25,
        ShopEntry.Type.TOOL));

    // 巡警商店
    // 左轮手枪 - 325金币
    PATROLLER_SHOP.add(new ShopEntry(
        TMMItems.REVOLVER.getDefaultInstance(),
        325,
        ShopEntry.Type.WEAPON));
    BOMBER_SHOP.add(new ShopEntry(
        TMMItems.LOCKPICK.getDefaultInstance(),
        80,
        ShopEntry.Type.TOOL));
    // 歌手商店
    for (int i = 1; i <= 5; i++) {
      ItemStack singer_shop_item = ModItems.SINGER_MUSIC_DISC.getDefaultInstance();
      singer_shop_item.set(DataComponents.ITEM_NAME,
          Component.translatable("item.noellesroles.shop.singer.display_name.root",
              Component.translatable(
                  "item.noellesroles.shop.singer.display_name."
                      + i)
                  .withStyle(ChatFormatting.GOLD))
              .withStyle(ChatFormatting.AQUA));
      var lores = new ArrayList<Component>();
      lores.add(Component.translatable("item.noellesroles.shop.singer.lore",
          Component.translatable("item.noellesroles.shop.singer.effect." + i)
              .withStyle(ChatFormatting.YELLOW))
          .withStyle(ChatFormatting.GRAY));
      singer_shop_item.set(DataComponents.LORE, new ItemLore(lores));
      final int idx = i;
      // 第5张唱片(Lupinus)价格为500金币，其他为100金币
      int price = (i == 5) ? 500 : 100;
      SINGER_SHOP.add(new ShopEntry(singer_shop_item, price, ShopEntry.Type.TOOL) {
        public boolean onBuy(@NotNull Player player) {
          return SingerPlayerComponent.buyDisc(player, idx);
        }
      });
    }

    // 医生商店
    DOCTOR_SHOP.add(new ShopEntry(
        ModItems.ANTIDOTE_REAGENT.getDefaultInstance(),
        50,
        ShopEntry.Type.TOOL));
    // 针管 - 75金币
    DOCTOR_SHOP.add(new ShopEntry(
        ModItems.ANTIDOTE.getDefaultInstance(),
        75,
        ShopEntry.Type.TOOL));
    // 药丸 - 75金币
    DOCTOR_SHOP.add(new ShopEntry(
        ModItems.createPillStack(false),
        75,
        ShopEntry.Type.TOOL));
    // 净化弹 - 300金币
    DOCTOR_SHOP.add(new ShopEntry(
        ModItems.PURIFY_BOMB.getDefaultInstance(),
        300,
        ShopEntry.Type.TOOL));

    // 乘务员商店
    // 乘务员钥匙 - 50金币
    ATTENDANT_SHOP.add(new ShopEntry(ModItems.MASTER_KEY_P.getDefaultInstance(), 50, ShopEntry.Type.TOOL));

    // 铁门钥匙 - 75金币
    ATTENDANT_SHOP
        .add(new ShopEntry(
            io.wifi.starrailexpress.index.TMMItems.IRON_DOOR_KEY
                .getDefaultInstance(),
            75, ShopEntry.Type.TOOL));
    // 手电筒（moonlight_lamp） - 150金币
    if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("handheldmoon:moonlight_lamp"))) {
      final var moonlightLampItem = BuiltInRegistries.ITEM
          .get(ResourceLocation.parse("handheldmoon:moonlight_lamp"));
      if (moonlightLampItem != null) {
        final var defaultInstance = moonlightLampItem.getDefaultInstance();
        ATTENDANT_SHOP.add(new ShopEntry(defaultInstance, 150, ShopEntry.Type.TOOL));
      }
    }

    // 魔术师商店
    // 假刀 - 50金币
    MAGICIAN_SHOP.add(new ShopEntry(
        ModItems.FAKE_KNIFE.getDefaultInstance(),
        50,
        ShopEntry.Type.WEAPON));

    // 假撬棍 - 35金币
    MAGICIAN_SHOP.add(new ShopEntry(
        ModItems.FAKE_CROWBAR.getDefaultInstance(),
        35,
        ShopEntry.Type.WEAPON));

    // 假开锁器 - 80金币
    MAGICIAN_SHOP.add(new ShopEntry(
        ModItems.FAKE_LOCKPICK.getDefaultInstance(),
        80,
        ShopEntry.Type.WEAPON));

    // 鞭炮 - 30金币
    MAGICIAN_SHOP.add(new ShopEntry(
        TMMItems.FIRECRACKER.getDefaultInstance(),
        30,
        ShopEntry.Type.WEAPON));

    // 假裹尸袋 - 100金币
    MAGICIAN_SHOP.add(new ShopEntry(
        ModItems.FAKE_BODY_BAG.getDefaultInstance(),
        100,
        ShopEntry.Type.WEAPON));

    // 便签 - 100金币
    MAGICIAN_SHOP.add(new ShopEntry(
        TMMItems.NOTE.getDefaultInstance(),
        100,
        ShopEntry.Type.WEAPON));

    // 假枪 - 175金币
    MAGICIAN_SHOP.add(new ShopEntry(
        ModItems.FAKE_REVOLVER.getDefaultInstance(),
        175,
        ShopEntry.Type.WEAPON));

    // 假手雷 - 200金币
    MAGICIAN_SHOP.add(new ShopEntry(
        ModItems.FAKE_GRENADE.getDefaultInstance(),
        200,
        ShopEntry.Type.WEAPON));

    // 假疯狂模式 - 325金币
    MAGICIAN_SHOP.add(new ShopEntry(
        ModItems.FAKE_PSYCHO_MODE.getDefaultInstance(),
        325,
        ShopEntry.Type.WEAPON) {
      @Override
      public boolean onBuy(@NotNull Player player) {
        // 获得假球棒并启动假疯狂模式
        var magicianComponent = org.agmas.noellesroles.component.ModComponents.MAGICIAN
            .get(player);
        if (magicianComponent != null) {
          if (!magicianComponent.startFakePsycho()) {
            return false;
          }
        }
        return true;
      }
    });

    // 强盗商店
    // 刀 - 200金币
    BANDIT_SHOP.add(new ShopEntry(
        TMMItems.KNIFE.getDefaultInstance(),
        200,
        ShopEntry.Type.WEAPON));

    // 匪徒手枪 - 175金币
    BANDIT_SHOP.add(new ShopEntry(
        ModItems.BANDIT_REVOLVER.getDefaultInstance(),
        175,
        ShopEntry.Type.WEAPON));

    // 手榴弹 - 600金币
    BANDIT_SHOP.add(new ShopEntry(
        TMMItems.GRENADE.getDefaultInstance(),
        600,
        ShopEntry.Type.WEAPON));

    // 关灯 - 150金币
    BANDIT_SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 150, ShopEntry.Type.TOOL) {
      public boolean onBuy(@NotNull Player player) {
        return SREPlayerShopComponent.useBlackout(player);
      }
    });

    // 闪光弹 - 100金币
    BANDIT_SHOP.add(new ShopEntry(ModItems.FLASH_GRENADE.getDefaultInstance(), 30, ShopEntry.Type.TOOL) {
      @Override
      public boolean canBuy(@NotNull Player player) {
        return !(MCItemsUtils.hasItem(player, ModItems.FLASH_GRENADE) > 0);
      }
    });

    // 诱饵弹 - 50金币
    BANDIT_SHOP.add(new ShopEntry(ModItems.DECOY_GRENADE.getDefaultInstance(), 50, ShopEntry.Type.TOOL));

    // 小偷商店
    // 小偷的荣誉（金锭） - 根据人数动态计算价格
    // ShopEntry THIEF_SHOP_ENTRY = new
    // ShopEntry(Items.GOLD_INGOT.getDefaultInstance(),
    // 0, // 价格在onBuy中动态计算
    // ShopEntry.Type.TOOL) {
    // @Override
    // public int price() {
    // return 0;
    // }

    // @Override
    // public boolean onBuy(@NotNull Player player) {
    // var tpc = ThiefPlayerComponent.KEY.get(player);
    // int cost = tpc.honorCost;
    // // 扣除金币并给予金锭
    // shop.addToBalance(-cost);

    // player.addItem(Items.GOLD_INGOT.getDefaultInstance().copy());
    // player.displayClientMessage(
    // Component.translatable("message.noellesroles.thief.honor_purchased", cost)
    // .withStyle(ChatFormatting.GOLD),
    // true);
    // return true;
    // }
    // };
    // THIEF_SHOP.add(THIEF_SHOP_ENTRY);

    // 钟表匠商店
    // 时钟（原版） - 100金币
    CLOCKMAKER_SHOP.add(new ShopEntry(
        Items.CLOCK.getDefaultInstance(),
        100,
        ShopEntry.Type.TOOL));

    // 怀表 - 150金币
    CLOCKMAKER_SHOP.add(new ShopEntry(
        ModItems.POCKET_WATCH.getDefaultInstance(),
        150,
        ShopEntry.Type.TOOL));

    // 作家商店
    // 书与笔（原版） - 100金币
    WRITER_SHOP.add(new ShopEntry(
        Items.WRITABLE_BOOK.getDefaultInstance(),
        100,
        ShopEntry.Type.TOOL));

    // 搜救员商店
    // 绳索 - 150金币
    RESCUER_SHOP.add(new ShopEntry(
        ModItems.ROPE.getDefaultInstance(),
        150,
        ShopEntry.Type.TOOL));

    // 裹尸袋 - 75金币
    RESCUER_SHOP.add(new ShopEntry(
        TMMItems.BODY_BAG.getDefaultInstance(),
        75,
        ShopEntry.Type.TOOL));

    // 消防员商店
    // 消防斧 - 150金币
    FIREFIGHTER_SHOP.add(new ShopEntry(
        ModItems.FIRE_AXE.getDefaultInstance(),
        150,
        ShopEntry.Type.TOOL) {
      @Override
      public boolean onBuy(Player player) {
        if (SREItemUtils.hasItem(player, Items.CHAIN_COMMAND_BLOCK) > 0) {
          return false;
        }
        if (RoleUtils.insertStackInFreeSlot(player, this.stack().copy())) {
          player.getInventory().setItem(14, Items.CHAIN_COMMAND_BLOCK.getDefaultInstance());
          return true;
        }
        return false;
      }
    });

    // 灭火器 - 150金币
    FIREFIGHTER_SHOP.add(new ShopEntry(
        ModItems.EXTINGUISHER.getDefaultInstance(),
        150,
        ShopEntry.Type.TOOL) {
      @Override
      public boolean onBuy(Player player) {
        if (SREItemUtils.hasItem(player, Items.REPEATING_COMMAND_BLOCK) > 0) {
          return false;
        }
        if (RoleUtils.insertStackInFreeSlot(player, this.stack().copy())) {
          player.getInventory().setItem(15, Items.REPEATING_COMMAND_BLOCK.getDefaultInstance());
          return true;
        }
        return false;
      }
    });

    // 会计商店
    // 存折 - 100金币（只能购买1次）
    ACCOUNTANT_SHOP.add(new ShopEntry(
        ModItems.PASSBOOK.getDefaultInstance(),
        100,
        ShopEntry.Type.TOOL) {
      @Override
      public boolean onBuy(Player player) {
        if (SREItemUtils.hasItem(player, Items.COMMAND_BLOCK) > 0) {
          return false;
        }
        if (RoleUtils.insertStackInFreeSlot(player, this.stack().copy())) {
          player.getInventory().setItem(16, Items.COMMAND_BLOCK.getDefaultInstance());
          return true;
        }
        return false;
      }
    });

    // 特警商店
    // 马格南子弹 - 125金币
    SWAST_SHOP.add(new ShopEntry(
        TMMItems.MAGNUM_BULLET.getDefaultInstance(),
        125,
        ShopEntry.Type.WEAPON));

    // 瞄准镜 - 100金币
    SWAST_SHOP.add(new ShopEntry(
        TMMItems.SCOPE.getDefaultInstance(),
        100,
        ShopEntry.Type.WEAPON));

    // 铁门钥匙 - 75金币
    SWAST_SHOP.add(new ShopEntry(
        TMMItems.IRON_DOOR_KEY.getDefaultInstance(),
        75,
        ShopEntry.Type.TOOL));

    // 海王商店
    // 普通三叉戟 - 150金币
    var trident = Items.TRIDENT.getDefaultInstance();
    trident.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
    SEA_KING_SHOP.add(new ShopEntry(
        trident,
        150,
        ShopEntry.Type.WEAPON));

    // 水鬼商店
    initializeWaterGhostShop();

    // 秉烛人商店
    // 撬锁器 - 75金币

    CANDLE_BEARER_SHOP.add(new ShopEntry(
        TMMItems.LOCKPICK.getDefaultInstance(),
        75,
        ShopEntry.Type.TOOL));

    // 超级亡命徒商店
    SUPER_LOOSE_END_SHOP.add(new ShopEntry(
        ModItems.THROWING_KNIFE.getDefaultInstance(),
        100,
        ShopEntry.Type.TOOL));
  }
}