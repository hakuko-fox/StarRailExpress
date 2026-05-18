package org.agmas.noellesroles.game.roles.neutral.infected;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 疫使胜利检测器
 * 使用纵火犯的逻辑来防止游戏结束
 */
public class InfectedWinChecker {
    
    private static boolean wasAccelerated = false;  // 记录上一个tick的加速状态
    
    /**
     * 注册疫使胜利检测事件
     */
    public static void registerEvent() {
        // 胜利检测事件
        AllowGameEnd.EVENT.register((serverWorld, winStatus, isLooseEndsMode) -> {
            if (isLooseEndsMode) {
                return WinStatus.NOT_MODIFY;
            }
            
            var gameWorldComponent = SREGameWorldComponent.KEY.get(serverWorld);
            var players = serverWorld.getPlayers(GameUtils::isPlayerAliveAndSurvival);
            
            boolean infectedAlive = false;
            int infectedCount = 0;
            int totalAlive = players.size();
            int infectedInfectedCount = 0; // 被感染的非疫使玩家数量
            
            for (ServerPlayer player : players) {
                if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                    infectedAlive = true;
                    infectedCount++;
                }
                
                // 检查玩家是否被感染
                InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(player);
                if (infectedComponent != null && infectedComponent.infectedTicks > 0) {
                    if (!gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                        infectedInfectedCount++;
                    }
                }
            }
            
            // 只有疫使存活
            if (infectedAlive && totalAlive == infectedCount) {
                // 疫使胜利 - 算作杀手胜利
                RoleUtils.customWinnerWin(serverWorld, WinStatus.KILLERS,
                    org.agmas.noellesroles.role.ModRoles.INFECTED.identifier().getPath(),
                    java.util.OptionalInt.of(org.agmas.noellesroles.role.ModRoles.INFECTED.color()));
                return WinStatus.KILLERS;
            }
            
            // 疫使存活且其他所有玩家都被感染
            if (infectedAlive && infectedInfectedCount == totalAlive - infectedCount) {
                // 疫使胜利 - 算作杀手胜利
                RoleUtils.customWinnerWin(serverWorld, WinStatus.KILLERS,
                    org.agmas.noellesroles.role.ModRoles.INFECTED.identifier().getPath(),
                    java.util.OptionalInt.of(org.agmas.noellesroles.role.ModRoles.INFECTED.color()));
                return WinStatus.KILLERS;
            }
            
            // 防止游戏结束 - 疫使存活时阻止游戏结束判定
            if (infectedAlive && (winStatus == WinStatus.KILLERS || winStatus == WinStatus.PASSENGERS)) {
                return WinStatus.NONE;
            }
            
            return WinStatus.NOT_MODIFY;
        });

        // 服务器tick事件 - 检查疫使加速条件
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerLevel level = server.overworld();
            var gameWorldComponent = SREGameWorldComponent.KEY.maybeGet(level).orElse(null);
            if (gameWorldComponent == null || !gameWorldComponent.isRunning()) {
                return;
            }

            // 检查场上是否无其他杀手，只剩下疫使
            boolean hasNonInfectedKiller = false;
            boolean hasInfected = false;
            var players = level.getPlayers(GameUtils::isPlayerAliveAndSurvival);

            for (ServerPlayer player : players) {
                var role = gameWorldComponent.getRole(player);
                if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                    hasInfected = true;
                } else if (role != null && (role.isKillerTeam() || role.isKiller())) {
                    // 有其他杀手存在
                    hasNonInfectedKiller = true;
                }
            }

            // 如果疫使存活且无其他杀手，或者所有其他玩家都被感染，触发加速效果
            if (hasInfected && (!hasNonInfectedKiller || checkAllNonInfectedAreInfected(level, gameWorldComponent))) {
                // 设置加速传播（病毒传染时间缩短至10秒）
                if (!wasAccelerated) {
                    InfectedPlayerComponent.setSpreadAcceleratedForAll(level, true);
                    wasAccelerated = true;
                }
                InfectedAbilityHandler.checkAndTriggerLastInfected(level);
            } else {
                // 取消加速传播
                if (wasAccelerated) {
                    InfectedPlayerComponent.setSpreadAcceleratedForAll(level, false);
                    wasAccelerated = false;
                }
            }
        });
    }

    /**
     * 检查除了疫使以外的所有玩家是否都被感染
     */
    private static boolean checkAllNonInfectedAreInfected(ServerLevel level, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
            if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                continue;
            }
            InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(player);
            if (infectedComponent == null || infectedComponent.infectedTicks <= 0) {
                return false;
            }
        }
        return true;
    }
}
