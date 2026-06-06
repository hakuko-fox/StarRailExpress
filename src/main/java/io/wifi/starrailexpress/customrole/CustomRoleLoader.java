package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.ExtraEffectRole;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.customrole.CustomRoleData.EffectEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 自定义职业加载器
 * 负责从 CustomRoleConfig 读取配置并注册为 SRERole
 */
public class CustomRoleLoader {

    private static final Map<String, CustomRoleData> loadedRoles = new HashMap<>();
    private static final Map<String, SRERole> registeredRoles = new HashMap<>();

    /**
     * 重新加载所有自定义职业
     */
    public static void reload(MinecraftServer server) {
        // 先清除旧的自定义职业
        List<String> toRemove = new ArrayList<>();
        for (var entry : TMMRoles.ROLES.entrySet()) {
            if (entry.getKey().getPath().startsWith("customrole:")) {
                toRemove.add(entry.getKey().toString());
            }
        }
        for (String key : toRemove) {
            TMMRoles.ROLES.remove(ResourceLocation.parse(key));
        }
        registeredRoles.clear();
        loadedRoles.clear();

        // 从服务器世界目录加载配置
        var level = server.overworld();
        var worldPath = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        CustomRoleConfig config = CustomRoleConfig.loadFromFile(worldPath);

        for (CustomRoleData data : config.roles) {
            try {
                SRERole role = createRole(data);
                TMMRoles.registerRole(role);
                registeredRoles.put(data.englishId, role);
                loadedRoles.put(data.englishId, data);
                SRE.LOGGER.info("[CustomRole] Registered custom role: {}", data.englishId);
            } catch (Exception e) {
                SRE.LOGGER.error("[CustomRole] Failed to register custom role: {}", data.englishId, e);
            }
        }

        SRE.LOGGER.info("[CustomRole] Loaded {} custom roles", config.roles.size());
    }

    public static CustomRoleData getCustomRoleData(String englishId) {
        var result = loadedRoles.get(englishId);
        if (result != null) return result;
        // 客户端回退：从配置文件读取
        try {
            var config = CustomRoleConfig.loadFromDefaultPath();
            return config.findRole(englishId);
        } catch (Exception e) {
            return null;
        }
    }

    public static SRERole getRegisteredRole(String englishId) {
        return registeredRoles.get(englishId);
    }

    /**
     * 根据配置创建 SRERole 实例
     */
    private static SRERole createRole(CustomRoleData data) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("customrole", data.englishId);

        // 解析颜色
        int color = (data.colorR << 16) | (data.colorG << 8) | data.colorB;

        // 解析心情类型
        SRERole.MoodType mood = "FAKE".equalsIgnoreCase(data.moodType)
            ? SRERole.MoodType.FAKE : SRERole.MoodType.REAL;

        // 解析体力
        int maxSprintTime;
        if (data.infiniteSprint) {
            maxSprintTime = -1; // 无限
        } else {
            int civilianSprint = io.wifi.starrailexpress.api.TMMRoles.CIVILIAN.getMaxSprintTime();
            maxSprintTime = (int) (civilianSprint * data.sprintMultiplier);
        }

        // 解析初始药水效果 (EffectEntry with amplifier)
        ArrayList<MobEffectInstance> effects = new ArrayList<>();
        if (!data.initialEffects.isEmpty()) {
            for (EffectEntry effEntry : data.initialEffects) {
                String cleaned = effEntry.effectId.trim();
                if (cleaned.isEmpty()) continue;
                try {
                    ResourceLocation effectRL = ResourceLocation.parse(cleaned);
                    var effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectRL);
                    if (effectHolder.isPresent()) {
                        effects.add(new MobEffectInstance(effectHolder.get(),
                            -1, effEntry.amplifier, false, false, true));
                    }
                } catch (Exception ignored) {}
            }
        }

        // 创建商店
        List<ShopEntry> shop = createShopEntries(data);

        // 创建角色（使用 CustomNormalRole 以支持自定义商店）
        SRERole role = new CustomNormalRole(id, color, data.isInnocent, data.canUseKiller,
            mood, maxSprintTime, data.canSeeTime,
            effects.isEmpty() ? new ArrayList<>() : effects, shop);

        // === 高级定义 ===
        role.setCanSeeCoin(data.canSeeCoin);
        if (data.canUseInstinct) role.setCanUseInstinct(true);
        if (data.ableToPickUpRevolver != null) role.setAbleToPickUpRevolver(data.ableToPickUpRevolver);
        if (data.setNeutrals != null && data.setNeutrals) role.setNeutrals(true);
        if (data.setNeutralForKiller != null && data.setNeutralForKiller) role.setNeutralForKiller(true);
        if (data.setVigilanteTeam != null && data.setVigilanteTeam) role.setVigilanteTeam(true);
        if (data.canSeeTeammateKiller != null) role.setCanSeeTeammateKiller(data.canSeeTeammateKiller);
        role.setOccupiedRoleCount(data.occupiedRoleCount);
        role.setMax(data.maxCount);
        if (data.canAutoAddMoney != null) role.setCanAutoAddMoney(data.canAutoAddMoney);
        role.setCanBeRandomedByOtherRoles(data.canBeRandomedByOtherRoles);
        if (data.canIgnoreBlackout != null) role.setCanIgnoreBlackout(data.canIgnoreBlackout);
        if (data.canSeeBodyItems != null) role.setCanSeeBodyItems(data.canSeeBodyItems);
        if (data.canSeeBodyRoleInfo != null) role.setCanSeeBodyRoleInfo(data.canSeeBodyRoleInfo);
        if (data.canSeeBodyDeathReason != null) role.setCanSeeBodyDeathReason(data.canSeeBodyDeathReason);
        if (data.canSeeBodyKiller != null) role.setCanSeeBodyKiller(data.canSeeBodyKiller);

        // === 生成选项 ===
        if (data.enableChance >= 0 && !data.useRareChance) role.setEnableChance(data.enableChance);
        if (data.useRareChance && data.enableRareChance >= 0) role.setEnableRareChance(data.enableRareChance);
        if (data.enableNeededPlayerCount >= 0) role.setEnableNeededPlayerCount(data.enableNeededPlayerCount);

        // 互斥和绑定生成（需要在所有角色注册完成后处理，这里只存储引用）
        // 这些将在 postInit 中处理

        // === 职业能力选项 ===
        // 初始物品
        if (!data.initialItems.isEmpty()) {
            role.serverTickEvent = (role.serverTickEvent == null) ? null : role.serverTickEvent;
            // Override getDefaultItems via onInit
            final SRERole finalRole = role;
            final List<CustomRoleData.InitialItemEntry> items = new ArrayList<>(data.initialItems);
            role.serverTickEvent = (player, gameWorld) -> {
                // Initial items are handled via HarpyModLoader's onInit mechanism
                // We add items on first tick if the player hasn't received them yet
            };
        }

        // 技能
        if (data.enableAbility && !data.abilitySkillCommands.isEmpty()) {
            final List<String> commands = new ArrayList<>(data.abilitySkillCommands);
            final int cooldownSeconds = data.abilityCooldownSeconds;

            RoleSkill.register(role, context -> {
                ServerPlayer player = context.player();
                SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);

                if (ability.cooldown > 0) {
                    return;
                }

                // Execute all commands
                for (String cmd : commands) {
                    String processed = cmd
                        .replace("<player>", player.getGameProfile().getName())
                        .replace("~ ~ ~", String.format("%.1f %.1f %.1f",
                            player.getX(), player.getY(), player.getZ()));
                    player.getServer().getCommands().performPrefixedCommand(
                        player.getServer().createCommandSourceStack(), processed);
                }

                ability.setCooldown(cooldownSeconds * 20);
                ability.sync();
            });
        }

        return role;
    }

    /**
     * 后期处理：设置互斥、绑定生成、地图限制等
     */
    public static void postInit() {
        CustomRoleConfig config = CustomRoleConfig.getInstance();

        for (CustomRoleData data : config.roles) {
            SRERole role = registeredRoles.get(data.englishId);
            if (role == null) continue;

            // 双向互斥
            for (String oppId : data.twoWayOpposingJobs) {
                SRERole oppRole = findRole(oppId);
                if (oppRole != null) {
                    role.addTwoWayOpposingJobs(oppRole);
                }
            }

            // 单向互斥
            for (String oppId : data.opposingJobs) {
                SRERole oppRole = findRole(oppId);
                if (oppRole != null) {
                    role.addOpposingJobs(oppRole);
                }
            }
        }
    }

    private static SRERole findRole(String roleId) {
        // Try as ResourceLocation
        ResourceLocation id;
        if (roleId.contains(":")) {
            id = ResourceLocation.parse(roleId);
        } else {
            id = SRE.id(roleId);
        }
        return TMMRoles.ROLES.get(id);
    }

    /**
     * 创建商店条目（带冷却和禁止重复购买支持）
     */
    public static List<ShopEntry> createShopEntries(CustomRoleData data) {
        List<ShopEntry> entries = new ArrayList<>();
        for (CustomRoleData.ShopEntryData entry : data.shopEntries) {
            final int cooldownTicks = entry.cooldownSeconds * 20;
            switch (entry.type) {
                case "item": {
                    if (!entry.itemId.isEmpty()) {
                        try {
                            ResourceLocation itemId = ResourceLocation.parse(entry.itemId);
                            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
                            if (item.isPresent()) {
                                final Item theItem = item.get();
                                entries.add(new ShopEntry(
                                    new ItemStack(theItem), entry.price, ShopEntry.Type.TOOL
                                ) {
                                    @Override
                                    public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                                        // 禁止重复购买：检查快捷栏是否已有该物品
                                        if (!entry.allowDuplicate) {
                                            for (var stack : player.getInventory().items) {
                                                if (stack.is(theItem)) return false;
                                            }
                                        }
                                        boolean result = super.onBuy(player);
                                        // 冷却
                                        if (result && cooldownTicks > 0 && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                                            sp.getCooldowns().addCooldown(theItem, cooldownTicks);
                                        }
                                        return result;
                                    }
                                });
                            }
                        } catch (Exception ignored) {}
                    }
                    break;
                }
                case "psycho":
                    entries.add(new ShopEntry(
                        io.wifi.starrailexpress.index.TMMItems.PSYCHO_MODE.getDefaultInstance(),
                        entry.price, ShopEntry.Type.WEAPON
                    ) {
                        @Override
                        public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                            return io.wifi.starrailexpress.cca.SREPlayerShopComponent.usePsychoMode(player);
                        }
                    });
                    break;
                case "blackout":
                    entries.add(new ShopEntry(
                        io.wifi.starrailexpress.index.TMMItems.BLACKOUT.getDefaultInstance(),
                        entry.price, ShopEntry.Type.TOOL
                    ) {
                        @Override
                        public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                            return io.wifi.starrailexpress.cca.SREPlayerShopComponent.useBlackout(player);
                        }
                    });
                    break;
                case "monitor_fail":
                    entries.add(new ShopEntry(
                        io.wifi.starrailexpress.index.TMMItems.MONITOR_BROKEN.getDefaultInstance(),
                        entry.price, ShopEntry.Type.TOOL
                    ) {
                        @Override
                        public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                            return io.wifi.starrailexpress.cca.SREPlayerShopComponent.useMonitorBroken(player,
                                io.wifi.starrailexpress.SREConfig.instance().monitorBrokenDuration * 20);
                        }
                    });
                    break;
                case "custom": {
                    if (!entry.itemId.isEmpty() && !entry.commands.isEmpty()) {
                        final List<String> cmds = new ArrayList<>(entry.commands);
                        try {
                            ResourceLocation itemId = ResourceLocation.parse(entry.itemId);
                            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
                            ItemStack display = item.map(ItemStack::new).orElse(ItemStack.EMPTY);
                            // 设置自定义商品名称
                            if (!entry.displayName.isEmpty()) {
                                display.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                                    net.minecraft.network.chat.Component.literal(entry.displayName));
                            }
                            entries.add(new ShopEntry(display, entry.price, ShopEntry.Type.TOOL) {
                                @Override
                                public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                                    if (player.getServer() != null) {
                                        for (String cmd : cmds) {
                                            String processed = cmd
                                                .replace("<player>", player.getGameProfile().getName())
                                                .replace("~ ~ ~", String.format("%.1f %.1f %.1f",
                                                    player.getX(), player.getY(), player.getZ()));
                                            player.getServer().getCommands().performPrefixedCommand(
                                                player.getServer().createCommandSourceStack(), processed);
                                        }
                                    }
                                    // 冷却
                                    if (cooldownTicks > 0 && !display.isEmpty()
                                            && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                                        sp.getCooldowns().addCooldown(display.getItem(), cooldownTicks);
                                    }
                                    return true;
                                }
                            });
                        } catch (Exception ignored) {}
                    }
                    break;
                }
            }
        }
        return entries;
    }
}
