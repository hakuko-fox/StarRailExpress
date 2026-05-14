package org.agmas.noellesroles.game.modes.repair;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.packet.OpenRepairRoleSelectionS2CPacket;
import org.agmas.noellesroles.role.ModRoles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RepairEscapeGameMode extends GameMode {
    private long selectionEndTick;
    private boolean rolesFinalized;

    public RepairEscapeGameMode(ResourceLocation identifier) {
        super(identifier, 12, 2);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return true;
    }

    @Override
    public boolean hasMood() {
        return false;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        RepairModeState.reset(serverWorld);
        rolesFinalized = false;
        selectionEndTick = serverWorld.getGameTime() + 40 * 20L;
        ArrayList<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        int hunterCount = Math.max(1, shuffled.size() / 5);
        int neutralCount = Math.max(1, shuffled.size() / 6);
        List<String> playerNames = shuffled.stream().map(player -> player.getGameProfile().getName()).toList();
        for (int i = 0; i < shuffled.size(); i++) {
            ServerPlayer player = shuffled.get(i);
            RepairRoleDatabase.loadInto(player);
            RepairRoleDefinition.Faction faction = i < hunterCount ? RepairRoleDefinition.Faction.HUNTER
                    : i < hunterCount + neutralCount ? RepairRoleDefinition.Faction.NEUTRAL
                            : RepairRoleDefinition.Faction.SURVIVOR;
            var component = ModComponents.REPAIR_ROLES.get(player);
            component.init();
            component.selectionEndTick = selectionEndTick;
            component.sync();
            gameWorldComponent.addRole(player, switch (faction) {
                case HUNTER -> ModRoles.REPAIR_HUNTER;
                case NEUTRAL -> ModRoles.REPAIR_NEUTRAL;
                case SURVIVOR -> ModRoles.REPAIR_SURVIVOR;
            }, false);
            giveModeItems(player, faction, serverWorld.random);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40 * 20, 10, false, false, true));
            ServerPlayNetworking.send(player, new AnnounceWelcomePayload(gameWorldComponent.getRole(player).getIdentifier().toString(),
                    hunterCount, shuffled.size() - hunterCount));
            ServerPlayNetworking.send(player, new OpenRepairRoleSelectionS2CPacket(faction.id(), selectionEndTick, playerNames));
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.select_role", 40)
                    .withStyle(ChatFormatting.GOLD), false);
        }
        gameWorldComponent.syncRoles();
    }

    private static void giveModeItems(ServerPlayer player, RepairRoleDefinition.Faction faction, RandomSource random) {
        switch (faction) {
            case HUNTER -> {
                player.addItem(new ItemStack(ModItems.HUNTER_CHAIN));
                player.addItem(new ItemStack(ModItems.ROPE));
                player.addItem(new ItemStack(ModBlocks.HUNTER_SNARE.asItem(), 2));
            }
            case NEUTRAL -> {
                player.addItem(new ItemStack(ModItems.SPARE_PARTS, 2));
                player.addItem(new ItemStack(ModItems.RESCUE_FLARE));
                player.addItem(new ItemStack(ModItems.SMOKE_PELLET));
            }
            case SURVIVOR -> {
                player.addItem(new ItemStack(ModItems.REPAIR_TOOLBOX));
                ItemStack parts = new ItemStack(ModItems.SPARE_PARTS);
                parts.setCount(3 + random.nextInt(3));
                player.addItem(parts);
                player.addItem(new ItemStack(ModItems.RESCUE_FLARE));
                player.addItem(new ItemStack(ModItems.SMOKE_PELLET));
                if (random.nextBoolean()) {
                    player.addItem(new ItemStack(ModItems.DECOY_BEACON));
                } else {
                    player.addItem(new ItemStack(ModItems.ESCAPE_GRAPPLE));
                }
            }
        }
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (!rolesFinalized && serverWorld.getGameTime() >= selectionEndTick) {
            finalizeSelectedRoles(serverWorld, gameWorldComponent);
        }
        applyRoleTickEffects(serverWorld, gameWorldComponent);
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;
        int activeSurvivors = 0;
        int escapedSurvivors = 0;
        int livingHunters = 0;

        for (ServerPlayer player : serverWorld.players()) {
            var component = ModComponents.REPAIR_ROLES.get(player);
            if (player.getTags().contains(RepairModeState.NEUTRAL_WIN_TAG)) {
                var roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
                roundEnd.CustomWinnerID = component.activeRole;
                roundEnd.CustomWinnerPlayers.add(player.getUUID());
                winStatus = GameUtils.WinStatus.CUSTOM;
                break;
            }
            if (RepairRoleDefinition.byId(component.activeRole).map(role -> role.faction == RepairRoleDefinition.Faction.HUNTER)
                    .orElse(gameWorldComponent.isRole(player, ModRoles.REPAIR_HUNTER))) {
                if (!GameUtils.isPlayerEliminated(player)) {
                    livingHunters++;
                }
                continue;
            }
            boolean survivor = RepairRoleDefinition.byId(component.activeRole)
                    .map(role -> role.faction == RepairRoleDefinition.Faction.SURVIVOR)
                    .orElse(gameWorldComponent.isRole(player, ModRoles.REPAIR_SURVIVOR));
            if (!survivor) {
                continue;
            }
            if (player.getTags().contains(RepairModeState.ESCAPED_TAG)) {
                escapedSurvivors++;
            } else if (!GameUtils.isPlayerEliminated(player)) {
                activeSurvivors++;
            }
        }

        if (winStatus == GameUtils.WinStatus.NONE) {
            if (escapedSurvivors > 0 && activeSurvivors == 0) {
                winStatus = GameUtils.WinStatus.PASSENGERS;
            } else if (activeSurvivors == 0) {
                winStatus = GameUtils.WinStatus.KILLERS;
            } else if (livingHunters == 0) {
                winStatus = GameUtils.WinStatus.PASSENGERS;
            } else if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime()) {
                winStatus = GameUtils.WinStatus.KILLERS;
            }
        }

        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }

    private void finalizeSelectedRoles(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        rolesFinalized = true;
        for (ServerPlayer player : serverWorld.players()) {
            var component = ModComponents.REPAIR_ROLES.get(player);
            RepairRoleDefinition.Faction faction = gameWorldComponent.isRole(player, ModRoles.REPAIR_HUNTER)
                    ? RepairRoleDefinition.Faction.HUNTER
                    : gameWorldComponent.isRole(player, ModRoles.REPAIR_NEUTRAL)
                            ? RepairRoleDefinition.Faction.NEUTRAL
                            : RepairRoleDefinition.Faction.SURVIVOR;
            RepairRoleDefinition role = component.selectedRole(faction);
            component.activeRole = role.id;
            component.neutralTaskProgress = 0;
            component.neutralTaskCompleted = false;
            component.sync();
            gameWorldComponent.addRole(player, role.sreRole(), false);
            giveRoleSkillItem(player, role);
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.role_locked", role.displayName())
                    .withStyle(ChatFormatting.GREEN), false);
        }
        gameWorldComponent.syncRoles();
    }

    private static void giveRoleSkillItem(ServerPlayer player, RepairRoleDefinition role) {
        switch (role.id) {
            case "warden" -> {
                player.addItem(new ItemStack(ModItems.HUNTER_JAMMER));
                player.addItem(new ItemStack(ModBlocks.HUNTER_SNARE.asItem(), 2));
            }
            case "brute" -> player.addItem(new ItemStack(ModItems.HUNTER_BLINK));
            case "tracker" -> player.addItem(new ItemStack(ModItems.HUNTER_PULSE));
            case "mechanic" -> player.addItem(new ItemStack(ModItems.REPAIR_TOOLBOX));
            case "medic" -> player.addItem(new ItemStack(ModItems.RESCUE_FLARE));
            case "runner" -> player.addItem(new ItemStack(ModItems.ESCAPE_GRAPPLE));
            default -> {
            }
        }
    }

    private void applyRoleTickEffects(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (!rolesFinalized) {
            return;
        }
        for (ServerPlayer player : serverWorld.players()) {
            String active = ModComponents.REPAIR_ROLES.get(player).activeRole;
            if ("runner".equals(active)) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false, true));
                if (serverWorld.getGameTime() % 8 == 0) {
                    serverWorld.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1D, player.getZ(), 3, 0.15D, 0.05D, 0.15D, 0.01D);
                }
            } else if ("brute".equals(active)) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, false, true));
                if (serverWorld.getGameTime() % 12 == 0) {
                    serverWorld.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, player.getX(), player.getY() + 1.0D, player.getZ(), 4, 0.25D, 0.35D, 0.25D, 0.02D);
                }
            } else if ("tracker".equals(active) && serverWorld.getGameTime() % 100 == 0) {
                for (ServerPlayer target : serverWorld.players()) {
                    if (target != player && !GameUtils.isPlayerEliminated(target)
                            && !ModComponents.REPAIR_ROLES.get(target).activeRole.isEmpty()
                            && !gameWorldComponent.getRole(target).canUseKiller()) {
                        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false, true));
                        serverWorld.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + 1.0D, target.getZ(), 8, 0.35D, 0.45D, 0.35D, 0.02D);
                    }
                }
            }
        }
    }

    @Override
    public void stopGame(ServerLevel world) {
        RepairModeState.reset(world);
        rolesFinalized = false;
    }
}
