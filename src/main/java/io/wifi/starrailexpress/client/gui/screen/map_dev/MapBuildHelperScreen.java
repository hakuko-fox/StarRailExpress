package io.wifi.starrailexpress.client.gui.screen.map_dev;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.scenery.SceneGeometry;
import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

public class MapBuildHelperScreen extends Screen {

    // ══════════════════════════════════════════════════════════════════
    // 偏移量（静态 + 文件持久化）
    // ══════════════════════════════════════════════════════════════════

    private static double offsetX = 0.5;
    private static double offsetY = 1;
    private static double offsetZ = 0.5;

    // ══════════════════════════════════════════════════════════════════
    // 实例字段
    // ══════════════════════════════════════════════════════════════════

    private final BlockPos position;
    private int activeTab = 0;

    private EditBox dxBox, dyBox, dzBox;
    private EditBox sceneIdBox;
    private EditBox mapNameBox, mapImportBox;

    private final List<AbstractWidget> tabWidgets0 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets1 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets2 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets3 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets4 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets5 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets6 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets7 = new ArrayList<>();

    // 面板尺寸
    private int panelLeftX;
    private int panelTopY;
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 454;

    // 滚动相关
    private int scrollOffset = 0;
    private int contentHeight = 0;
    private boolean isDraggingScroll = false;
    private int dragStartY = 0;
    private int dragStartScroll = 0;
    private final Map<AbstractWidget, Integer> widgetRelativeY = new HashMap<>();

    // 配置条目根列表（未排序，保持原始顺序）
    private List<SettingsEntry> allSettingsEntries = new ArrayList<>();

    private static final Gson GSON = new Gson();

    // ══════════════════════════════════════════════════════════════════
    // 内部类：配置条目
    // ══════════════════════════════════════════════════════════════════

    private class SettingsEntry {
        String path;
        Field field;
        Object parentObject;
        int depth;
        boolean expanded = false;
        List<SettingsEntry> children = new ArrayList<>();
        List<AbstractWidget> widgets = new ArrayList<>();
        String displayName;
        String categoryId;
        Object currentValue;
        SettingsEntry(String path, Field field, Object parent, int depth) {
            this.path = path;
            this.field = field;
            this.parentObject = parent;
            this.depth = depth;
            this.displayName = getFieldDisplayName(field);
            this.categoryId = getCategoryId(field);
            updateValue();
        }

        void updateValue() {
            try {
                field.setAccessible(true);
                this.currentValue = field.get(parentObject);
            } catch (IllegalAccessException e) {
                this.currentValue = null;
            }
        }

        boolean isLeaf() {
            return children.isEmpty();
        }
    }

    // 分类标题条目（非真实字段，仅用于界面展示）
    private class CategoryHeaderEntry {
        String displayName;
        CategoryHeaderEntry(String displayName, String categoryId) {
            this.displayName = displayName;
        }
    }

    // 自定义标签控件：显示分类标题
    private class CategoryLabel extends AbstractWidget {
        private final String text;

        public CategoryLabel(int x, int y, int width, int height, String text) {
            super(x, y, width, height, Component.literal(text));
            this.text = text;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            // 左侧小方块（颜色与界面顶部边框一致）
            g.fill(getX(), getY() + 4, getX() + 4, getY() + getHeight() - 4, 0xFF5577CC);
            // 标题文字（加粗，金色）
            g.drawString(font,
                    Component.literal(text).withStyle(Style.EMPTY.withColor(0xFFAA00).withBold(true)),
                    getX() + 8, getY() + 4, 0xFFFFFF, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 构造
    // ══════════════════════════════════════════════════════════════════

    public MapBuildHelperScreen(BlockPos position) {
        this(position, 0);
    }

    public MapBuildHelperScreen(BlockPos position, int initialTab) {
        super(Component.translatable("sre.map_helper.title"));
        this.position = position;
        this.activeTab = Math.max(0, Math.min(7, initialTab));
    }

    // ══════════════════════════════════════════════════════════════════
    // 坐标计算
    // ══════════════════════════════════════════════════════════════════

    private double ax() {
        return position.getX() + offsetX;
    }

    private double ay() {
        return position.getY() + offsetY;
    }

    private double az() {
        return position.getZ() + offsetZ;
    }

    private float playerYaw() {
        var p = Minecraft.getInstance().player;
        return p != null ? p.getYRot() : 0f;
    }

    private float playerPitch() {
        var p = Minecraft.getInstance().player;
        return p != null ? p.getXRot() : 0f;
    }

    // ══════════════════════════════════════════════════════════════════
    // 命令发送
    // ══════════════════════════════════════════════════════════════════

    private void sendAndClose(String cmd) {
        var p = Minecraft.getInstance().player;
        if (p != null)
            p.connection.sendCommand(cmd);
        onClose();
    }

    private void sendOnly(String cmd) {
        var p = Minecraft.getInstance().player;
        if (p != null)
            p.connection.sendCommand(cmd);
    }

    // ══════════════════════════════════════════════════════════════════
    // init – 全部 UI 构建
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        tabWidgets0.clear();
        tabWidgets1.clear();
        tabWidgets2.clear();
        tabWidgets3.clear();
        tabWidgets4.clear();
        tabWidgets5.clear();
        tabWidgets6.clear();
        tabWidgets7.clear();
        widgetRelativeY.clear();

        final int bw = 158;
        final int gap = 12;
        final int bh = 22;

        panelLeftX = (width - PANEL_WIDTH) / 2;
        panelTopY = (height - PANEL_HEIGHT) / 2;

        buildOffsetRow();
        buildTabBar();

        final int cy = panelTopY + 114;

        // ---------- Tab 0: Positions ----------
        addTabWidget(tabWidgets0, ModernButton.builder(
                Component.translatable("sre.map_helper.set_spawn"),
                b -> sendAndClose(String.format("sre:area_manager set spawnPos %f %f %f %.1f %.1f",
                        ax(), ay(), az(), playerYaw(), playerPitch())))
                .bounds(panelLeftX + 6, cy, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build());

        addTabWidget(tabWidgets0, ModernButton.builder(
                Component.translatable("sre.map_helper.set_spectator_spawn"),
                b -> sendAndClose(String.format("sre:area_manager set spectatorSpawnPos %f %f %f %.1f %.1f",
                        ax(), ay(), az(), playerYaw(), playerPitch())))
                .bounds(panelLeftX + 6 + bw + gap, cy, bw, bh)
                .accentBar(AccentSide.RIGHT)
                .build());

        // ---------- Tab 1: Areas ----------
        String[] areaKeys = { "readyArea", "playArea", "sceneArea", "resetTemplateArea", "resetPasteArea" };
        for (int i = 0; i < areaKeys.length; i++) {
            final String cmd = areaKeys[i];
            final Component areaName = Component.translatable("sre.area." + cmd);
            final int rowY = cy + i * (bh + gap);

            addTabWidget(tabWidgets1, ModernButton.builder(
                    Component.translatable("sre.map_helper.area.set_min", areaName),
                    b -> sendAndClose(
                            String.format("sre:area_manager set %s min %.0f %.0f %.0f", cmd, Math.floor(ax()),
                                    Math.floor(ay()), Math.floor(az()))))
                    .bounds(panelLeftX + 6, rowY, bw, bh)
                    .accentBar(AccentSide.LEFT)
                    .build());

            addTabWidget(tabWidgets1, ModernButton.builder(
                    Component.translatable("sre.map_helper.area.set_max", areaName),
                    b -> sendAndClose(
                            String.format("sre:area_manager set %s max %.0f %.0f %.0f", cmd, Math.floor(ax()),
                                    Math.floor(ay()), Math.floor(az()))))
                    .bounds(panelLeftX + 6 + bw + gap, rowY, bw, bh)
                    .accentBar(AccentSide.RIGHT)
                    .build());
        }

        // ---------- Tab 2: Settings（移除了 canJump, canSwim,
        // enableOxygenDrowning）----------
        String[] boolFields = { "noReset", "haveOutsideSound", "sceneOffsetEnabled", "mustCopy",
                "minigameQuestEnabled" };
        String[] boolFieldKeys = {
                "sre.field.noReset",
                "sre.field.haveOutsideSound",
                "sre.field.sceneOffsetEnabled",
                "sre.field.mustCopy",
                "sre.field.minigameQuestEnabled"
        };
        for (int i = 0; i < boolFields.length; i++) {
            final String field = boolFields[i];
            final int rowY = cy + i * (bh + gap);

            addTabWidget(tabWidgets2, ModernButton.builder(
                    Component.translatable("sre.map_helper.set_true", Component.translatable(boolFieldKeys[i])),
                    b -> sendOnly("sre:area_manager set " + field + " true"))
                    .bounds(panelLeftX + 6, rowY, bw, bh)
                    .accentBar(AccentSide.LEFT)
                    .build());
            addTabWidget(tabWidgets2, ModernButton.builder(
                    Component.translatable("sre.map_helper.set_false", Component.translatable(boolFieldKeys[i])),
                    b -> sendOnly("sre:area_manager set " + field + " false"))
                    .bounds(panelLeftX + 6 + bw + gap, rowY, bw, bh)
                    .accentBar(AccentSide.RIGHT)
                    .build());
        }

        // ---------- Tab 3: Rooms ----------
        buildRoomsTab(cy, bw, gap, bh);

        // ---------- Tab 4: Environment（仅保留 effect）----------
        buildEnvironmentTab(cy, bw, gap, bh);

        // ---------- Tab 5: Scene ----------
        buildSceneTab(cy, bw, gap, bh);

        // ---------- Tab 6: Map ----------
        buildMapTab(cy, bw, gap, bh);

        // ---------- Tab 7: All Settings（新增，带分类标题）----------
        buildAllSettingsTab();

        // 将所有控件添加为可渲染
        tabWidgets0.forEach(this::addRenderableWidget);
        tabWidgets1.forEach(this::addRenderableWidget);
        tabWidgets2.forEach(this::addRenderableWidget);
        tabWidgets3.forEach(this::addRenderableWidget);
        tabWidgets4.forEach(this::addRenderableWidget);
        tabWidgets5.forEach(this::addRenderableWidget);
        tabWidgets6.forEach(this::addRenderableWidget);
        tabWidgets7.forEach(this::addRenderableWidget);

        syncTabVisibility();
    }

    // ── 原有各标签页构建方法（部分已精简）────────────────────────────

    private void buildMapTab(int startY, int bw, int gap, int bh) {
        int left = panelLeftX + 6;
        int right = left + bw + gap;
        int fullWidth = PANEL_WIDTH - 12;

        AreasWorldComponent areas = SREClient.areaComponent;
        String currentName = areas == null || areas.mapName == null ? "" : areas.mapName;

        mapNameBox = new EditBox(font, left, startY, 230, bh, Component.translatable("sre.map_helper.map_name"));
        mapNameBox.setMaxLength(128);
        mapNameBox.setValue(currentName);
        addTabWidget(tabWidgets6, mapNameBox);

        int row1 = startY + bh + gap;
        addTabWidget(tabWidgets6, ModernButton.builder(Component.translatable("sre.map_helper.save_as_new"), b -> {
            String name = mapNameBox.getValue().trim();
            if (!name.isEmpty()) {
                sendOnly("sre:area_manager map save " + quoteCommandArgument(name));
            }
        }).bounds(left, row1, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets6, ModernButton.builder(Component.translatable("sre.map_helper.save_overwrite"), b -> {
            String name = mapNameBox.getValue().trim();
            if (!name.isEmpty()) {
                sendOnly("sre:area_manager map save " + quoteCommandArgument(name) + " force");
            }
        }).bounds(right, row1, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row2 = row1 + bh + gap;
        addTabWidget(tabWidgets6, ModernButton.builder(Component.translatable("sre.map_helper.load_map_config"), b -> {
            String name = mapNameBox.getValue().trim();
            if (!name.isEmpty()) {
                sendOnly("sre:area_manager map load " + quoteCommandArgument(name));
            }
        }).bounds(left, row2, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets6, ModernButton.builder(Component.translatable("sre.map_helper.list_maps"),
                b -> sendOnly("sre:area_manager map list"))
                .bounds(right, row2, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row3 = row2 + bh + gap;
        mapImportBox = new EditBox(font, left, row3, fullWidth, bh,
                Component.translatable("sre.map_helper.import_filename_hint"));
        mapImportBox.setMaxLength(128);
        addTabWidget(tabWidgets6, mapImportBox);

        int row4 = row3 + bh + gap;
        addTabWidget(tabWidgets6, ModernButton.builder(Component.translatable("sre.map_helper.import_as_map"),
                b -> importMapConfig(false))
                .bounds(left, row4, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets6, ModernButton.builder(Component.translatable("sre.map_helper.import_overwrite"),
                b -> importMapConfig(true))
                .bounds(right, row4, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row5 = row4 + bh + gap;
        addTabWidget(tabWidgets6, ModernButton.builder(Component.translatable("sre.map_helper.create_blank_map"), b -> {
            sendOnly("sre:area_manager create_new");
            String name = mapNameBox.getValue().trim();
            if (!name.isEmpty()) {
                sendOnly("sre:area_manager map name " + quoteCommandArgument(name));
            }
        }).bounds(left, row5, fullWidth, bh).accentBar(AccentSide.BOTTOM).build());

        // 地图初始物品配置
        int row6 = row5 + bh + gap;
        AreasWorldComponent areasForII = SREClient.areaComponent;
        String currentInitialItems = "";
        if (areasForII != null && !areasForII.initialItems.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String item : areasForII.initialItems) {
                String[] parts = item.split(";");
                if (parts.length >= 2) {
                    if (sb.length() > 0)
                        sb.append(",");
                    sb.append(parts[0]).append(",").append(parts[1]);
                }
            }
            currentInitialItems = sb.toString();
        }
        EditBox initialItemsBox = new EditBox(font, left, row6, fullWidth, bh,
                Component.translatable("sre.map_helper.initial_items_hint"));
        initialItemsBox.setMaxLength(512);
        initialItemsBox.setValue(currentInitialItems);
        addTabWidget(tabWidgets6, initialItemsBox);

        int row7 = row6 + bh + gap;
        addTabWidget(tabWidgets6,
                ModernButton.builder(Component.translatable("sre.map_helper.set_initial_items"), b -> {
                    String value = initialItemsBox.getValue().trim();
                    if (!value.isEmpty()) {
                        sendOnly("sre:area_manager set initialItems " + quoteCommandArgument(value));
                    }
                }).bounds(left, row7, fullWidth, bh).accentBar(AccentSide.BOTTOM).build());
    }

    private void importMapConfig(boolean force) {
        String filename = mapImportBox.getValue().trim();
        String mapName = mapNameBox.getValue().trim();
        if (filename.isEmpty() || mapName.isEmpty()) {
            return;
        }
        sendOnly("sre:area_manager map import " + quoteCommandArgument(filename)
                + " as " + quoteCommandArgument(mapName)
                + (force ? " force" : ""));
    }

    private void buildSceneTab(int startY, int bw, int gap, int bh) {
        int left = panelLeftX + 6;
        int right = left + bw + gap;
        AreasWorldComponent areas = SREClient.areaComponent;

        sceneIdBox = new EditBox(font, left, startY, 190, bh, Component.translatable("sre.map_helper.scene_id"));
        sceneIdBox.setMaxLength(128);
        sceneIdBox.setValue(areas == null ? "" : areas.getSceneId());
        addTabWidget(tabWidgets5, sceneIdBox);
        addTabWidget(tabWidgets5, ModernButton.builder(Component.translatable("sre.map_helper.assign_scene"), b -> {
            String id = sceneIdBox.getValue().trim();
            if (!id.isEmpty()) {
                sendOnly("sre:scene library assign " + quoteCommandArgument(id));
            }
        }).bounds(left + 196, startY, 64, bh).accentBar(AccentSide.BOTTOM).build());
        addTabWidget(tabWidgets5, ModernButton.builder(Component.translatable("sre.map_helper.scene_editor"),
                b -> sendOnly("sre:scene manager"))
                .bounds(left + 264, startY, 64, bh).accentBar(AccentSide.RIGHT).build());

        int row1 = startY + bh + gap;
        addTabWidget(tabWidgets5, ModernButton.builder(Component.translatable("sre.map_helper.detach_scene"),
                b -> sendOnly("sre:scene library detach"))
                .bounds(left, row1, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets5, ModernButton.builder(Component.translatable("sre.map_helper.list_scene_library"),
                b -> sendOnly("sre:scene library list"))
                .bounds(right, row1, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row2 = row1 + bh + gap;
        addTabWidget(tabWidgets5,
                ModernButton
                        .builder(Component.translatable("sre.map_helper.toggle_preview"),
                                b -> SceneAssetClient.setPreviewEnabled(!SceneAssetClient.isPreviewEnabled()))
                        .bounds(left, row2, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets5,
                ModernButton
                        .builder(Component.translatable("sre.map_helper.toggle_scroll"),
                                b -> SceneAssetClient.setPreviewPaused(!SceneAssetClient.isPreviewPaused()))
                        .bounds(right, row2, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row3 = row2 + bh + gap;
        addTabWidget(tabWidgets5,
                ModernButton
                        .builder(Component.translatable("sre.map_helper.preview_alpha_down"),
                                b -> SceneAssetClient.setPreviewAlpha(SceneAssetClient.getPreviewAlpha() - 0.05F))
                        .bounds(left, row3, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets5,
                ModernButton
                        .builder(Component.translatable("sre.map_helper.preview_alpha_up"),
                                b -> SceneAssetClient.setPreviewAlpha(SceneAssetClient.getPreviewAlpha() + 0.05F))
                        .bounds(right, row3, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row4 = row3 + bh + gap;
        addTabWidget(tabWidgets5,
                ModernButton
                        .builder(Component.translatable("sre.map_helper.preview_speed_down"),
                                b -> SceneAssetClient.setPreviewSpeed(SceneAssetClient.getPreviewSpeed() - 0.25F))
                        .bounds(left, row4, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets5,
                ModernButton
                        .builder(Component.translatable("sre.map_helper.preview_speed_up"),
                                b -> SceneAssetClient.setPreviewSpeed(SceneAssetClient.getPreviewSpeed() + 0.25F))
                        .bounds(right, row4, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row5 = row4 + bh + gap;
        addTabWidget(tabWidgets5, ModernButton.builder(Component.translatable("sre.map_helper.refresh_preview"),
                b -> SceneAssetClient.refreshPreview())
                .bounds(left, row5, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets5, ModernButton.builder(
                Component.translatable("sre.map_helper.toggle_client_scene",
                        SceneAssetClient.isMovingSceneEnabled() ? Component.translatable("sre.map_helper.on")
                                : Component.translatable("sre.map_helper.off")),
                b -> {
                    SceneAssetClient.setMovingSceneEnabled(!SceneAssetClient.isMovingSceneEnabled());
                    init(minecraft, width, height);
                })
                .bounds(right, row5, bw, bh).accentBar(AccentSide.RIGHT).build());
    }

    private void buildRoomsTab(int startY, int bw, int gap, int bh) {
        final int row1 = startY;
        final int fieldWidth = 60;
        EditBox roomCountBox = makeField(panelLeftX + 6, row1, fieldWidth, bh, "0",
                v -> {
                });
        addTabWidget(tabWidgets3, roomCountBox);

        ModernButton setCountBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.set_room_count"),
                b -> {
                    String count = roomCountBox.getValue().trim();
                    if (!count.isEmpty()) {
                        sendOnly("sre:area_manager set roomCount " + count);
                    }
                })
                .bounds(panelLeftX + 6 + fieldWidth + gap, row1, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build();
        addTabWidget(tabWidgets3, setCountBtn);

        final int row2 = startY + (bh + gap);
        EditBox roomIdBox = makeField(panelLeftX + 6, row2, fieldWidth, bh, "0",
                v -> {
                });
        addTabWidget(tabWidgets3, roomIdBox);

        ModernButton addRoomBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.add_to_room"),
                b -> {
                    String idStr = roomIdBox.getValue().trim();
                    if (!idStr.isEmpty()) {
                        try {
                            int id = Integer.parseInt(idStr);
                            long x = (long) Math.floor(ax());
                            long y = (long) Math.floor(ay());
                            long z = (long) Math.floor(az());
                            sendOnly(String.format("sre:area_manager set roomPositions add %d %d %d %d", id, x, y, z));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                })
                .bounds(panelLeftX + 6 + fieldWidth + gap, row2, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build();
        addTabWidget(tabWidgets3, addRoomBtn);

        final int row3 = startY + 2 * (bh + gap);
        ModernButton removeRoomBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.remove_room"),
                b -> {
                    String idStr = roomIdBox.getValue().trim();
                    if (!idStr.isEmpty()) {
                        try {
                            int id = Integer.parseInt(idStr);
                            sendOnly("sre:area_manager set roomPositions remove " + id);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                })
                .bounds(panelLeftX + 6 + fieldWidth + gap, row3, bw, bh)
                .accentBar(AccentSide.RIGHT)
                .build();
        addTabWidget(tabWidgets3, removeRoomBtn);
    }

    private void buildEnvironmentTab(int startY, int bw, int gap, int bh) {
        final int smallH = 18;
        final int fieldW = 120;
        final int rowGap = bh + gap;
        int rowIndex = 0;

        final int row0 = startY + rowGap * rowIndex++;
        EditBox effectBox = makeField(panelLeftX + 6, row0, fieldW, smallH, "",
                v -> {
                });
        addTabWidget(tabWidgets4, effectBox);

        ModernButton effectBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.set_effect"),
                b -> {
                    String val = effectBox.getValue().trim();
                    sendOnly("sre:area_manager set effect " + (val.isEmpty() ? "\"\"" : val));
                })
                .bounds(panelLeftX + 6 + fieldW + gap, row0, bw - fieldW - gap + gap, bh)
                .accentBar(AccentSide.RIGHT)
                .build();
        addTabWidget(tabWidgets4, effectBtn);
    }

    private void buildOffsetRow() {
        final int oy = panelTopY + 52;
        final int fh = 18;
        final int labelW = 14;
        final int fieldW = 64;
        final int smallGap = 6;
        final int bigGap = 12;
        final int groupW = labelW + smallGap + fieldW;
        final int resetW = 48;
        final int totalW = groupW * 3 + bigGap * 2 + resetW;
        final int startX = panelLeftX + (PANEL_WIDTH - totalW) / 2;

        dxBox = makeField(startX + labelW + smallGap, oy, fieldW, fh, "0",
                v -> {
                    try {
                        offsetX = Double.parseDouble(v);
                    } catch (Exception ignored) {
                    }
                });
        dxBox.setValue(fmtDouble(offsetX));
        addRenderableWidget(dxBox);

        int yStart = startX + groupW + bigGap;
        dyBox = makeField(yStart + labelW + smallGap, oy, fieldW, fh, "1",
                v -> {
                    try {
                        offsetY = Double.parseDouble(v);
                    } catch (Exception ignored) {
                    }
                });
        dyBox.setValue(fmtDouble(offsetY));
        addRenderableWidget(dyBox);

        int zStart = yStart + groupW + bigGap;
        dzBox = makeField(zStart + labelW + smallGap, oy, fieldW, fh, "0",
                v -> {
                    try {
                        offsetZ = Double.parseDouble(v);
                    } catch (Exception ignored) {
                    }
                });
        dzBox.setValue(fmtDouble(offsetZ));
        addRenderableWidget(dzBox);

        int resetX = zStart + groupW + bigGap;
        addRenderableWidget(ModernButton.builder(Component.translatable("sre.map_helper.reset"), b -> {
            offsetX = offsetZ = 0.5;
            offsetY = 1;
            dxBox.setValue("0.5");
            dyBox.setValue("1");
            dzBox.setValue("0.5");
        }).bounds(resetX, oy, resetW, fh)
                .accentBar(AccentSide.BOTTOM)
                .build());
    }

    private void buildTabBar() {
        final int tabY = panelTopY + 74;
        final int tabH = 22;
        final int tabW = 42;
        final int tabGap = 5;
        final int totalTabW = tabW * 8 + tabGap * 7;
        final int startX = panelLeftX + (PANEL_WIDTH - totalTabW) / 2;

        String[] tabKeys = { "positions", "areas", "settings", "rooms", "environment", "scene", "map", "all" };
        for (int i = 0; i < 8; i++) {
            final int idx = i;
            var builder = ModernButton.builder(Component.translatable("sre.map_helper.tab." + tabKeys[i]), b -> {
                activeTab = idx;
                init(minecraft, width, height);
            }).bounds(startX + i * (tabW + tabGap), tabY, tabW, tabH);

            if (activeTab == i)
                builder.accentBar(AccentSide.BOTTOM);
            else
                builder.accentBar();
            addRenderableWidget(builder.build());
        }
    }

    // ── 辅助方法 ────────────────────────────────────────────────────
    private void addTabWidget(List<AbstractWidget> list, AbstractWidget widget) {
        list.add(widget);
    }

    private void syncTabVisibility() {
        tabWidgets0.forEach(w -> w.visible = (activeTab == 0));
        tabWidgets1.forEach(w -> w.visible = (activeTab == 1));
        tabWidgets2.forEach(w -> w.visible = (activeTab == 2));
        tabWidgets3.forEach(w -> w.visible = (activeTab == 3));
        tabWidgets4.forEach(w -> w.visible = (activeTab == 4));
        tabWidgets5.forEach(w -> w.visible = (activeTab == 5));
        tabWidgets6.forEach(w -> w.visible = (activeTab == 6));
        tabWidgets7.forEach(w -> w.visible = (activeTab == 7));
    }

    private EditBox makeField(int x, int y, int w, int h, String defaultVal, Consumer<String> responder) {
        var box = new EditBox(font, x, y, w, h, Component.empty());
        box.setValue(defaultVal);
        box.setMaxLength(20);
        box.setResponder(responder);
        return box;
    }

    private static String fmtDouble(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e9)
            return String.valueOf((long) v);
        String s = String.format("%.4f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private static String quoteCommandArgument(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ══════════════════════════════════════════════════════════════════
    // 全部配置标签页（Tab 7）—— 动态反射 + 分类标题（不排序）
    // ══════════════════════════════════════════════════════════════════

    // ---- 字段过滤 ----
    private boolean shouldShowField(Field field) {
        if (field.isAnnotationPresent(Expose.class)) {
            Expose expose = field.getAnnotation(Expose.class);
            if (!expose.serialize() || !expose.deserialize()) {
                return false;
            }
        }
        return true;
    }

    // ---- 获取字段显示名 ----
    private String getFieldDisplayName(Field field) {
        String key = "sre.map_helper.settings.entry." + field.getName();
        return Component.translatableWithFallback(key, field.getName()).getString();
    }

    // ---- 获取分类 ID（使用 @Category 注解） ----
    private String getCategoryId(Field field) {
        try {
            Class<io.wifi.ConfigCompact.annotation.Category> categoryClass = io.wifi.ConfigCompact.annotation.Category.class;
            if (field.isAnnotationPresent((Class<? extends java.lang.annotation.Annotation>) categoryClass)) {
                java.lang.annotation.Annotation ann = field.getAnnotation(categoryClass);
                try {
                    java.lang.reflect.Method m = categoryClass.getMethod("value");
                    return (String) m.invoke(ann);
                } catch (Exception e) {
                    return null;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    // ---- 获取分类显示名 ----
    private String getCategoryDisplayName(String categoryId) {
        if (categoryId == null)
            categoryId = "default";
        String key = "sre.map_helper.settings.category." + categoryId;
        return Component.translatableWithFallback(key, categoryId).getString();
    }

    // ---- 判断是否应展开对象 ----
    private boolean shouldExpandObject(Object obj) {
        if (obj == null)
            return false;
        Class<?> clazz = obj.getClass();
        if (clazz.isPrimitive() || clazz.isEnum() || clazz == String.class)
            return false;
        if (Collection.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz))
            return false;
        if (Number.class.isAssignableFrom(clazz) || Boolean.class.isAssignableFrom(clazz))
            return false;
        return true;
    }

    private void expandObject(SettingsEntry parent) {
        Object obj = parent.currentValue;
        if (obj == null)
            return;
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (!shouldShowField(field))
                continue;
            String childPath = parent.path + "." + field.getName();
            SettingsEntry child = new SettingsEntry(childPath, field, obj, parent.depth + 1);
            if (shouldExpandObject(child.currentValue)) {
                expandObject(child);
            }
            parent.children.add(child);
        }
    }

    // ---- 构建配置条目（带分类标题） ----
    private void buildAllSettingsTab() {
        tabWidgets7.clear();
        allSettingsEntries.clear();

        AreasWorldComponent comp = SREClient.areaComponent;
        if (comp == null)
            return;
        Object settings = comp.areasSettings;
        if (settings == null)
            return;

        // 1. 收集所有根字段
        Class<?> clazz = settings.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (!shouldShowField(field))
                continue;
            SettingsEntry root = new SettingsEntry(field.getName(), field, settings, 0);
            if (shouldExpandObject(root.currentValue)) {
                expandObject(root);
            }
            allSettingsEntries.add(root);
        }

        // 2. 构建混合列表（保留原始顺序，插入分类标题）
        List<Object> flatList = new ArrayList<>();
        String lastCategory = null;
        for (SettingsEntry entry : allSettingsEntries) {
            String cat = entry.categoryId;
            if (!Objects.equals(cat, lastCategory)) {
                String displayCat = getCategoryDisplayName(cat);
                if (displayCat == null)
                    displayCat = cat != null ? cat : "default";
                flatList.add(new CategoryHeaderEntry(displayCat, cat));
                lastCategory = cat;
            }
            flatList.add(entry);
        }

        // 3. 递归创建控件（顶层使用混合列表，子项仍为 SettingsEntry）
        int totalHeight = createWidgetsForMixedEntries(flatList, 0);
        contentHeight = totalHeight;
        scrollOffset = 0;
    }

    // 为混合列表创建控件（顶层）
    private int createWidgetsForMixedEntries(List<Object> list, int yOffset) {
        int currentY = yOffset;
        for (Object obj : list) {
            if (obj instanceof CategoryHeaderEntry) {
                CategoryHeaderEntry header = (CategoryHeaderEntry) obj;
                int height = createWidgetsForCategoryHeader(header, currentY);
                currentY += height;
            } else if (obj instanceof SettingsEntry) {
                SettingsEntry entry = (SettingsEntry) obj;
                int height = createWidgetsForEntry(entry, currentY);
                currentY += height;
                if (entry.expanded && !entry.children.isEmpty()) {
                    // 子项递归（不插入分类标题）
                    currentY = createWidgetsForEntries(entry.children, currentY);
                }
            }
        }
        return currentY;
    }

    // 为 SettingsEntry 列表递归创建控件（子项，不插入标题）
    private int createWidgetsForEntries(List<SettingsEntry> entries, int yOffset) {
        int currentY = yOffset;
        for (SettingsEntry entry : entries) {
            int height = createWidgetsForEntry(entry, currentY);
            currentY += height;
            if (entry.expanded && !entry.children.isEmpty()) {
                currentY = createWidgetsForEntries(entry.children, currentY);
            }
        }
        return currentY;
    }

    // 创建分类标题控件
    private int createWidgetsForCategoryHeader(CategoryHeaderEntry header, int y) {
        int leftX = panelLeftX + 6;
        int width = PANEL_WIDTH - 12;
        int height = 24;
        CategoryLabel label = new CategoryLabel(leftX, y, width, height, header.displayName);
        addWidgetWithRelativeY(label, y);
        tabWidgets7.add(label);
        return height;
    }

    // 工具：添加控件并记录相对Y
    private <T extends AbstractWidget> T addWidgetWithRelativeY(T widget, int relY) {
        widget.setY(panelTopY + 170 + relY);
        widgetRelativeY.put(widget, relY);
        return widget;
    }

    // 为单个 SettingsEntry 创建控件（叶子或内部节点）
    private int createWidgetsForEntry(SettingsEntry entry, int y) {
        int leftX = panelLeftX + 6 + entry.depth * 12;
        int labelWidth = 80;
        Class<?> type = entry.field.getType();
        Object value = entry.currentValue;
        int usedHeight = 30;

        if (entry.isLeaf()) {
            if (type == boolean.class || type == Boolean.class) {
                ModernButton enableBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.set_true", Component.literal("启用")),
                        b -> sendOnly("sre:area_manager set " + entry.path + " true"))
                        .bounds(leftX + labelWidth, y, 50, 20)
                        .accentBar(AccentSide.LEFT)
                        .build();
                addWidgetWithRelativeY(enableBtn, y);
                ModernButton disableBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.set_false", Component.literal("禁用")),
                        b -> sendOnly("sre:area_manager set " + entry.path + " false"))
                        .bounds(leftX + labelWidth + 54, y, 50, 20)
                        .accentBar(AccentSide.RIGHT)
                        .build();
                addWidgetWithRelativeY(disableBtn, y);
                ModernButton viewBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.view"),
                        b -> sendOnly("sre:area_manager get " + entry.path))
                        .bounds(leftX + labelWidth + 108, y, 30, 20)
                        .accentBar(AccentSide.BOTTOM)
                        .build();
                addWidgetWithRelativeY(viewBtn, y);
                entry.widgets.add(enableBtn);
                entry.widgets.add(disableBtn);
                entry.widgets.add(viewBtn);
                tabWidgets7.add(enableBtn);
                tabWidgets7.add(disableBtn);
                tabWidgets7.add(viewBtn);

            } else if (type == String.class || Number.class.isAssignableFrom(type)) {
                EditBox input = new EditBox(font, leftX + labelWidth, y, 90, 20, Component.empty());
                input.setValue(value != null ? value.toString() : "");
                input.setMaxLength(50);
                addWidgetWithRelativeY(input, y);
                ModernButton modifyBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.modify"),
                        b -> {
                            String val = input.getValue().trim();
                            if (!val.isEmpty()) {
                                sendOnly("sre:area_manager set " + entry.path + " " + quoteCommandArgument(val));
                            }
                        })
                        .bounds(leftX + labelWidth + 94, y, 40, 20)
                        .accentBar(AccentSide.BOTTOM)
                        .build();
                addWidgetWithRelativeY(modifyBtn, y);
                ModernButton viewBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.view"),
                        b -> sendOnly("sre:area_manager get " + entry.path))
                        .bounds(leftX + labelWidth + 138, y, 30, 20)
                        .accentBar(AccentSide.BOTTOM)
                        .build();
                addWidgetWithRelativeY(viewBtn, y);
                entry.widgets.add(input);
                entry.widgets.add(modifyBtn);
                entry.widgets.add(viewBtn);
                tabWidgets7.add(input);
                tabWidgets7.add(modifyBtn);
                tabWidgets7.add(viewBtn);

            } else if (type.isEnum()) {
                Object[] constants = type.getEnumConstants();
                int btnStartX = leftX + labelWidth;
                int btnY = y;
                int currentX = btnStartX;
                int maxWidth = PANEL_WIDTH - 12 - labelWidth - entry.depth * 12 - 6;
                int rowHeight = 20;
                int gap = 4;
                int rows = 1;
                for (Object constObj : constants) {
                    String constName = ((Enum<?>) constObj).name();
                    int btnWidth = Math.max(40, font.width(constName) + 12);
                    if (currentX + btnWidth > btnStartX + maxWidth - 34) {
                        currentX = btnStartX;
                        btnY += rowHeight + gap;
                        rows++;
                    }
                    ModernButton btn = ModernButton.builder(
                            Component.literal(constName),
                            b -> sendOnly("sre:area_manager set " + entry.path + " " + constName))
                            .bounds(currentX, btnY, btnWidth, rowHeight)
                            .accentBar(AccentSide.BOTTOM)
                            .build();
                    addWidgetWithRelativeY(btn, y + (btnY - y));
                    entry.widgets.add(btn);
                    tabWidgets7.add(btn);
                    currentX += btnWidth + gap;
                }
                int viewX = btnStartX + maxWidth - 34;
                int viewY = btnY;
                if (viewX - currentX < 30) {
                    viewX = btnStartX;
                    viewY += rowHeight + gap;
                    rows++;
                }
                ModernButton viewBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.view"),
                        b -> sendOnly("sre:area_manager get " + entry.path))
                        .bounds(viewX, viewY, 30, rowHeight)
                        .accentBar(AccentSide.BOTTOM)
                        .build();
                addWidgetWithRelativeY(viewBtn, y + (viewY - y));
                entry.widgets.add(viewBtn);
                tabWidgets7.add(viewBtn);
                usedHeight = rowHeight + (rows - 1) * (rowHeight + gap) + 4;

            } else if (Collection.class.isAssignableFrom(type)) {
                int x = leftX + labelWidth;
                EditBox addInput = new EditBox(font, x, y, 70, 20, Component.translatable("sre.map_helper.value"));
                addWidgetWithRelativeY(addInput, y);
                ModernButton addBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.add"),
                        b -> {
                            String val = addInput.getValue().trim();
                            if (!val.isEmpty()) {
                                sendOnly("sre:area_manager set " + entry.path + " add " + quoteCommandArgument(val));
                            }
                        })
                        .bounds(x + 74, y, 35, 20)
                        .accentBar(AccentSide.LEFT)
                        .build();
                addWidgetWithRelativeY(addBtn, y);
                int x2 = x + 74 + 35 + 4;
                EditBox removeInput = new EditBox(font, x2, y, 55, 20, Component.translatable("sre.map_helper.value"));
                addWidgetWithRelativeY(removeInput, y);
                ModernButton removeBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.remove"),
                        b -> {
                            String val = removeInput.getValue().trim();
                            if (!val.isEmpty()) {
                                sendOnly("sre:area_manager set " + entry.path + " remove " + quoteCommandArgument(val));
                            }
                        })
                        .bounds(x2 + 59, y, 35, 20)
                        .accentBar(AccentSide.RIGHT)
                        .build();
                addWidgetWithRelativeY(removeBtn, y);
                int x3 = x2 + 59 + 35 + 4;
                ModernButton clearBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.clear"),
                        b -> sendOnly("sre:area_manager set " + entry.path + " clear"))
                        .bounds(x3, y, 35, 20)
                        .accentBar(AccentSide.BOTTOM)
                        .build();
                addWidgetWithRelativeY(clearBtn, y);
                int x4 = x3 + 35 + 4;
                ModernButton viewBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.view"),
                        b -> sendOnly("sre:area_manager get " + entry.path))
                        .bounds(x4, y, 30, 20)
                        .accentBar(AccentSide.BOTTOM)
                        .build();
                addWidgetWithRelativeY(viewBtn, y);
                entry.widgets.add(addInput);
                entry.widgets.add(addBtn);
                entry.widgets.add(removeInput);
                entry.widgets.add(removeBtn);
                entry.widgets.add(clearBtn);
                entry.widgets.add(viewBtn);
                tabWidgets7.add(addInput);
                tabWidgets7.add(addBtn);
                tabWidgets7.add(removeInput);
                tabWidgets7.add(removeBtn);
                tabWidgets7.add(clearBtn);
                tabWidgets7.add(viewBtn);

            } else if (Map.class.isAssignableFrom(type)) {
                EditBox mapInput = new EditBox(font, leftX + labelWidth, y, 120, 20,
                        Component.translatable("sre.map_helper.json"));
                mapInput.setValue(value != null ? GSON.toJson(value) : "{}");
                addWidgetWithRelativeY(mapInput, y);
                ModernButton modifyBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.modify"),
                        b -> {
                            String json = mapInput.getValue().trim();
                            if (!json.isEmpty()) {
                                sendOnly("sre:area_manager set " + entry.path + " " + quoteCommandArgument(json));
                            }
                        })
                        .bounds(leftX + labelWidth + 124, y, 40, 20)
                        .accentBar(AccentSide.BOTTOM)
                        .build();
                addWidgetWithRelativeY(modifyBtn, y);
                ModernButton viewBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.view"),
                        b -> sendOnly("sre:area_manager get " + entry.path))
                        .bounds(leftX + labelWidth + 168, y, 30, 20)
                        .accentBar(AccentSide.BOTTOM)
                        .build();
                addWidgetWithRelativeY(viewBtn, y);
                entry.widgets.add(mapInput);
                entry.widgets.add(modifyBtn);
                entry.widgets.add(viewBtn);
                tabWidgets7.add(mapInput);
                tabWidgets7.add(modifyBtn);
                tabWidgets7.add(viewBtn);

            } else {
                // 其他类型：仅查看
                ModernButton viewBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.view"),
                        b -> sendOnly("sre:area_manager get " + entry.path))
                        .bounds(leftX + labelWidth, y, 30, 20)
                        .accentBar(AccentSide.BOTTOM)
                        .build();
                addWidgetWithRelativeY(viewBtn, y);
                entry.widgets.add(viewBtn);
                tabWidgets7.add(viewBtn);
            }
        } else {
            // 内部节点：展开/折叠按钮 + 名称
            ModernButton toggleBtn = ModernButton.builder(
                    Component.literal(entry.expanded ? "▾" : "▸"),
                    b -> {
                        entry.expanded = !entry.expanded;
                        init(minecraft, width, height);
                    })
                    .bounds(leftX, y, 20, 20)
                    .accentBar(AccentSide.BOTTOM)
                    .build();
            addWidgetWithRelativeY(toggleBtn, y);
            ModernButton nameBtn = ModernButton.builder(
                    Component.literal(entry.displayName),
                    b -> {
                    })
                    .bounds(leftX + 22, y, labelWidth - 22, 20)
                    .accentBar()
                    .build();
            addWidgetWithRelativeY(nameBtn, y);
            entry.widgets.add(toggleBtn);
            entry.widgets.add(nameBtn);
            tabWidgets7.add(toggleBtn);
            tabWidgets7.add(nameBtn);
        }

        return usedHeight;
    }

    // ---- 滚动 ----
    private int getVisibleHeight() {
        return PANEL_HEIGHT - 170;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll) {
        if (activeTab == 7) {
            int visibleH = getVisibleHeight();
            int contentH = contentHeight;
            if (contentH > visibleH) {
                scrollOffset -= verticalScroll * 20;
                scrollOffset = Math.max(0, Math.min(scrollOffset, contentH - visibleH));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (isDraggingScroll && activeTab == 7) {
            int visibleH = getVisibleHeight();
            int contentH = contentHeight;
            if (contentH > visibleH) {
                int dragDelta = (int) (mouseY - dragStartY);
                int scrollDelta = (int) ((float) dragDelta / visibleH * (contentH - visibleH));
                scrollOffset = Math.max(0, Math.min(contentH - visibleH, dragStartScroll + scrollDelta));
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeTab == 7) {
            int visibleH = getVisibleHeight();
            int contentH = contentHeight;
            if (contentH > visibleH) {
                int scrollBarX = panelLeftX + PANEL_WIDTH - 8;
                int scrollBarY = panelTopY + 170;
                int scrollBarH = visibleH;
                if (mouseX >= scrollBarX && mouseX <= scrollBarX + 4 &&
                        mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarH) {
                    isDraggingScroll = true;
                    dragStartY = (int) mouseY;
                    dragStartScroll = scrollOffset;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + PANEL_WIDTH + 6, panelTopY + PANEL_HEIGHT + 3, 0xCC080C18);
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + PANEL_WIDTH + 6, panelTopY - 2, 0xFF5577CC);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (activeTab == 7) {
            for (AbstractWidget widget : tabWidgets7) {
                Integer relY = widgetRelativeY.get(widget);
                if (relY != null) {
                    int newY = panelTopY + 170 + relY - scrollOffset;
                    widget.setY(newY);
                }
            }
        }

        super.render(g, mouseX, mouseY, partialTick);

        final int cx = panelLeftX + PANEL_WIDTH / 2;

        g.drawCenteredString(font,
                Component.translatable("sre.map_helper.title").withStyle(s -> s.withColor(0x55BBFF).withBold(true)),
                cx, panelTopY + 10, 0xFFFFFF);

        g.drawCenteredString(font,
                Component.translatable("sre.map_helper.source_pos", position.getX(), position.getY(), position.getZ())
                        .withStyle(s -> s.withColor(0x778899)),
                cx, panelTopY + 22, 0xFFFFFF);

        boolean hasOffset = offsetX != 0 || offsetY != 0 || offsetZ != 0;
        g.drawCenteredString(font,
                Component.translatable("sre.map_helper.applied_pos", ax(), ay(), az())
                        .withStyle(s -> s.withColor(hasOffset ? 0x55DD88 : 0x445566)),
                cx, panelTopY + 32, 0xFFFFFF);

        final int oy = panelTopY + 52;
        final int labelW = 14;
        final int fieldW = 64;
        final int smallGap = 6;
        final int bigGap = 12;
        int groupW = labelW + smallGap + fieldW;
        int resetW = 48;
        int totalW = groupW * 3 + bigGap * 2 + resetW;
        int startX = panelLeftX + (PANEL_WIDTH - totalW) / 2;

        g.drawString(font, Component.translatable("sre.map_helper.dx"), startX, oy + 4, 0xAABBCC, false);
        int yStart = startX + groupW + bigGap;
        g.drawString(font, Component.translatable("sre.map_helper.dy"), yStart, oy + 4, 0xAABBCC, false);
        int zStart = yStart + groupW + bigGap;
        g.drawString(font, Component.translatable("sre.map_helper.dz"), zStart, oy + 4, 0xAABBCC, false);

        g.fill(panelLeftX, panelTopY + 70, panelLeftX + PANEL_WIDTH, panelTopY + 71, 0x33AABBCC);
        g.fill(panelLeftX, panelTopY + 94, panelLeftX + PANEL_WIDTH, panelTopY + 95, 0x33AABBCC);

        String[] tabTitlesKeys = {
                "spawn_offset", "aabb_areas", "boolean_settings", "rooms_config", "environment", "scene", "map", "all"
        };
        g.drawString(font,
                Component.translatable("sre.map_helper.tab_title." + tabTitlesKeys[activeTab])
                        .withStyle(Style.EMPTY.withColor(0x5577CC).withBold(true)),
                panelLeftX + 6, panelTopY + 100, 0xFFFFFF, false);

        if (activeTab == 1) {
            g.drawString(font,
                    Component.translatable("sre.map_helper.areas.hint", ax(), ay(), az())
                            .withStyle(s -> s.withColor(0x445566)),
                    panelLeftX + 6, panelTopY + PANEL_HEIGHT - 12, 0xFFFFFF, false);
        } else if (activeTab == 3) {
            g.drawString(font,
                    Component.translatable("sre.map_helper.room_hint")
                            .withStyle(s -> s.withColor(0x445566)),
                    panelLeftX + 6, panelTopY + PANEL_HEIGHT - 12, 0xFFFFFF, false);
        } else if (activeTab == 5) {
            renderSceneSummary(g);
        } else if (activeTab == 6) {
            String mapName = SREClient.areaComponent == null || SREClient.areaComponent.mapName == null
                    ? "-"
                    : SREClient.areaComponent.mapName;
            g.drawString(font, Component.translatable("sre.map_helper.current_map", mapName),
                    panelLeftX + 6, panelTopY + PANEL_HEIGHT - 24, 0x88DDFF, false);
            g.drawString(font, Component.translatable("sre.map_helper.import_dir_hint"),
                    panelLeftX + 6, panelTopY + PANEL_HEIGHT - 12, 0x88CC99, false);
        } else if (activeTab == 7) {
            int visibleH = getVisibleHeight();
            int contentH = contentHeight;
            if (contentH > visibleH) {
                int scrollBarX = panelLeftX + PANEL_WIDTH - 8;
                int scrollBarY = panelTopY + 170;
                int scrollBarH = visibleH;
                int thumbH = Math.max(20, (int) ((float) visibleH / contentH * scrollBarH));
                int thumbY = scrollBarY + (int) ((float) scrollOffset / (contentH - visibleH) * (scrollBarH - thumbH));
                g.fill(scrollBarX, scrollBarY, scrollBarX + 4, scrollBarY + scrollBarH, 0x44FFFFFF);
                g.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbH, 0xAAFFFFFF);
            }

            int clipX = panelLeftX;
            int clipY = panelTopY + 170;
            int clipW = PANEL_WIDTH;
            int clipH = visibleH;
            g.pose().pushPose();
            g.enableScissor(clipX, clipY, clipX + clipW, clipY + clipH);
            g.pose().popPose();
            g.disableScissor();
        }
    }

    private void renderSceneSummary(GuiGraphics g) {
        AreasWorldComponent areas = SREClient.areaComponent;
        if (areas == null)
            return;

        SceneGeometry.SectionBounds bounds = SceneGeometry.sectionBounds(areas.getSceneArea());
        var status = SceneAssetClient.cacheStatus();
        String hash = SceneAssetClient.currentHash();

        Component sceneIdText = areas.getSceneId().isBlank()
                ? Component.translatable("sre.map_helper.scene_status.not_specified")
                : Component.literal(areas.getSceneId());
        Component areaStatus = areas.isSceneAreaConfigured()
                ? Component.translatable("sre.map_helper.scene_status.done")
                : Component.translatable("sre.map_helper.scene_status.not_done");
        Component assetStatus = areas.getSceneAssetHash().isBlank()
                ? Component.translatable("sre.map_helper.scene_status.not_done")
                : Component.translatable("sre.map_helper.scene_status.done");
        String scrollAxis = areas.getSceneScroll().name();
        int sectionCount = bounds.sectionCount();
        float alpha = SceneAssetClient.getPreviewAlpha() * 100.0F;
        float speed = SceneAssetClient.getPreviewSpeed();
        Component pauseStatus = SceneAssetClient.isPreviewPaused()
                ? Component.translatable("sre.map_helper.scene_status.paused")
                : Component.empty();

        Component sceneSummary = Component.translatable("sre.map_helper.scene_summary",
                sceneIdText, areaStatus, assetStatus, scrollAxis, sectionCount,
                String.format("%.0f", alpha), String.format("%.2f", speed), pauseStatus);

        int entries = status.entries();
        double usedMB = status.bytes() / 1048576.0D;
        double limitMB = status.limitBytes() / 1048576.0D;
        String hashDisplay = hash.isBlank()
                ? Component.translatable("sre.map_helper.scene_status.not_specified").getString()
                : hash.substring(0, Math.min(12, hash.length()));
        Component clientState = SceneAssetClient.isMovingSceneEnabled()
                ? Component.translatable("sre.map_helper.on")
                : Component.translatable("sre.map_helper.off");
        Component remoteState = areas.getSceneAssetRemoteUrl().isBlank()
                ? Component.translatable("sre.map_helper.off")
                : Component.translatable("sre.map_helper.on");
        Component downloadState = SceneAssetClient.isRemoteDownloading()
                ? Component.translatable("sre.map_helper.scene_status.downloading")
                : Component.empty();

        Component cacheSummary = Component.translatable("sre.map_helper.cache_summary",
                entries, String.format("%.1f", usedMB), String.format("%.1f", limitMB),
                hashDisplay, clientState, remoteState, downloadState);

        g.drawString(font, sceneSummary,
                panelLeftX + 6, panelTopY + PANEL_HEIGHT - 24, 0x88DDFF, false);
        g.drawString(font, cacheSummary,
                panelLeftX + 6, panelTopY + PANEL_HEIGHT - 12, 0x88CC99, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        SceneAssetClient.closeEditor();
        super.onClose();
    }
}