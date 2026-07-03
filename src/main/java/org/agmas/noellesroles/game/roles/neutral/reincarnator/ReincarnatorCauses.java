package org.agmas.noellesroles.game.roles.neutral.reincarnator;

import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 轮回者可收集的"死因"清单（仅用于界面展示的标准格子）。
 * <p>
 * 实际进度只要求"被其他玩家以不同方式杀死"——任何 {@code deathReason} 都会被计入，
 * 不限于此清单；此清单只是 HUD / 背包面板里用来画"标准死因格子"的参考集合。
 */
public final class ReincarnatorCauses {

    /** 标准可收集死因（用于界面格子展示，按常见到稀有排序）。 */
    public static final List<ResourceLocation> DISPLAY_CAUSES = List.of(
            GameConstants.DeathReasons.KNIFE,
            GameConstants.DeathReasons.REVOLVER,
            GameConstants.DeathReasons.DERRINGER,
            GameConstants.DeathReasons.BAT,
            GameConstants.DeathReasons.NUNCHUCK,
            GameConstants.DeathReasons.SNIPER_RIFLE,
            GameConstants.DeathReasons.ZERO_ONE_FIVE,
            GameConstants.DeathReasons.GRENADE,
            GameConstants.DeathReasons.POISON,
            GameConstants.DeathReasons.ARROW,
            GameConstants.DeathReasons.TRIDENT);

    private ReincarnatorCauses() {
    }
}
