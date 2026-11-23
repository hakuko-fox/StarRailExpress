package org.agmas.noellesroles.client.screen;

import com.nododiiiii.ponderer.application.api.RuntimeAccess;
import com.nododiiiii.ponderer.scene.DslScene;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.ponder.RolePonderIsolation;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 打开 Ponderer 的职业思索场景。
 * Ponderer 与 PondererLib 已经是本模组的前置，因此直接调用它们的公开 API。
 */
public final class RolePonderTutorial {
    private static final Logger LOGGER = LoggerFactory.getLogger(RolePonderTutorial.class);

    private static final String SCENE_PREFIX = "starrailexpress:role_tutorial.";

    private RolePonderTutorial() {
    }

    public enum OpenResult {
        OPENED,
        WORLD_REQUIRED,
        SCENE_MISSING,
        OPEN_FAILED
    }

    public static OpenResult open(SRERole role) {
        if (Minecraft.getInstance().level == null) {
            return OpenResult.WORLD_REQUIRED;
        }
        if (role == null) {
            return OpenResult.SCENE_MISSING;
        }
        String wantedId = SCENE_PREFIX + role.identifier().getPath();
        try {
            List<DslScene> scenes = RuntimeAccess.scenes();
            DslScene scene = scenes.stream()
                    .filter(candidate -> wantedId.equals(candidate.id))
                    .findFirst()
                    .orElse(null);
            if (scene == null) {
                LOGGER.warn("未找到职业思索场景 {}，Ponderer 当前已加载 {} 个场景", wantedId, scenes.size());
                return OpenResult.SCENE_MISSING;
            }
            if (!RolePonderIsolation.open(scene, 0)) {
                LOGGER.warn("Ponderer 未能打开职业思索场景 {}", wantedId);
                return OpenResult.OPEN_FAILED;
            }
            return OpenResult.OPENED;
        } catch (Throwable throwable) {
            LOGGER.warn("打开职业 {} 的思索场景失败：{}", role.identifier(), throwable.toString());
            return OpenResult.OPEN_FAILED;
        }
    }
}
