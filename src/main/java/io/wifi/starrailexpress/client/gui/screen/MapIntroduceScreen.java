package io.wifi.starrailexpress.client.gui.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.network.MapIntroSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import io.wifi.starrailexpress.index.SREBlocks;
import io.wifi.starrailexpress.index.TMMBlocks;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModSceneBlocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MapIntroduceScreen extends Screen {
    private static final int MAX_WIDTH = 720;
    private static final int LEFT_RATIO_NUM = 31;
    private static final int PAD = 7;
    private static final int ROW_H = 34;
    private static final int GAP = 5;
    private static final int TOP_H = 20;
    private static final int TAB_H = 18;
    private static final int BOTTOM_H = 27;
    private static final int PANEL = 0xD0141822;
    private static final int PANEL_ALT = 0xE01C2230;
    private static final int LINE = 0x805C6C86;
    private static final int TEXT = 0xFFE8EEF8;
    private static final int MUTED = 0xFF9AA9BE;

    private final List<MapEntry> maps = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<FormattedCharSequence> detailLines = new ArrayList<>();
    private final Set<String> bagMaps = new HashSet<>();
    private final Set<String> policeMaps = new HashSet<>();
    private final Set<String> underwaterMaps = new HashSet<>();
    private final Set<String> airMaps = new HashSet<>();
    private final List<TabInfo> tabs = List.of(
            new TabInfo(Tab.MAP_PROPERTIES, "map_intro.tab.map_properties", 0xFF5EB7D8),
            new TabInfo(Tab.SCENE_BLOCKS, "map_intro.tab.scene_blocks", 0xFF72C17B),
            new TabInfo(Tab.QUEST_BLOCKS, "map_intro.tab.quest_blocks", 0xFFE0AD5B),
            new TabInfo(Tab.MECHANICS, "map_intro.tab.mechanics", 0xFFB18AE6));

    private EditBox search;
    private Tab tab = Tab.MAP_PROPERTIES;
    private Entry selected;
    private int listScroll;
    private int detailScroll;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int leftW;
    private int rightW;

    public MapIntroduceScreen() {
        super(Component.translatable("map_intro.title"));
    }

    public void updateFromPacket(MapIntroSyncPayload payload) {
        maps.clear();
        bagMaps.clear();
        policeMaps.clear();
        underwaterMaps.clear();
        airMaps.clear();
        bagMaps.addAll(payload.bagMaps());
        policeMaps.addAll(payload.policeMaps());
        underwaterMaps.addAll(payload.underwaterMaps());
        airMaps.addAll(payload.airMaps());
        for (MapIntroSyncPayload.MapJson map : payload.maps()) {
            try {
                JsonObject root = JsonParser.parseString(map.json()).getAsJsonObject();
                maps.add(new MapEntry(map.id(), root));
            } catch (Exception ignored) {
                maps.add(new MapEntry(map.id(), new JsonObject()));
            }
        }
        maps.sort(Comparator.comparing(m -> m.id));
        rebuildEntries();
        if (selected == null && !entries.isEmpty()) {
            selected = entries.get(0);
        }
        rebuildDetail();
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        search = new EditBox(font, panelX + PAD, panelY + PAD, leftW - PAD * 2, TOP_H, Component.translatable("map_intro.search"));
        search.setHint(Component.translatable("map_intro.search"));
        search.setMaxLength(64);
        addRenderableWidget(search);
        rebuildEntries();
        if (selected == null && !entries.isEmpty()) {
            selected = entries.get(0);
        }
        rebuildDetail();
    }

    private void computeLayout() {
        panelW = Math.min(MAX_WIDTH, (int) (width * 0.9F));
        panelH = Math.min(360, Math.max(230, (int) (height * 0.82F)));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        leftW = panelW * LEFT_RATIO_NUM / 100;
        rightW = panelW - leftW - GAP;
    }

    private void rebuildEntries() {
        entries.clear();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        switch (tab) {
            case MAP_PROPERTIES -> {
                for (MapEntry map : maps) {
                    Component name = Component.translatableWithFallback("map." + map.id + ".name", map.id);
                    if (matches(q, map.id, name.getString())) {
                        entries.add(Entry.map(map, name));
                    }
                }
            }
            case SCENE_BLOCKS -> sceneBlockItems().forEach(item -> addItemEntry(q, item, tab));
            case QUEST_BLOCKS -> questBlockItems().forEach(item -> addItemEntry(q, item, tab));
            case MECHANICS -> {
                for (String id : List.of("tasks", "status_bar", "sabotage", "conduit_core", "special_roles")) {
                    Component name = Component.translatable("map_intro.mechanic." + id + ".title");
                    if (matches(q, id, name.getString())) {
                        entries.add(Entry.text(id, name, tab));
                    }
                }
            }
        }
        if (selected != null && entries.stream().noneMatch(e -> e.sameTarget(selected))) {
            selected = entries.isEmpty() ? null : entries.get(0);
        }
    }

    private void addItemEntry(String query, Item item, Tab entryTab) {
        Component name = item.getDescription();
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (matches(query, id, name.getString())) {
            entries.add(Entry.item(item, name, entryTab));
        }
    }

    private static boolean matches(String query, String id, String name) {
        return query.isBlank()
                || id.toLowerCase(Locale.ROOT).contains(query)
                || name.toLowerCase(Locale.ROOT).contains(query);
    }

    private void rebuildDetail() {
        detailLines.clear();
        detailScroll = 0;
        int wrapW = Math.max(80, rightW - PAD * 2 - 4);
        if (selected == null) {
            addWrapped(Component.translatable("map_intro.loading").withStyle(ChatFormatting.GRAY), wrapW);
            return;
        }
        switch (selected.tab) {
            case MAP_PROPERTIES -> buildMapDetail(selected.map, wrapW);
            case SCENE_BLOCKS, QUEST_BLOCKS -> buildBlockDetail(selected.item, wrapW);
            case MECHANICS -> buildMechanicDetail(selected.id, wrapW);
        }
    }

    private void buildMapDetail(MapEntry map, int wrapW) {
        addWrapped(Component.literal(map.name.getString()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), wrapW);
        addWrapped(Component.translatable("map_intro.map.id", map.id).withStyle(ChatFormatting.GRAY), wrapW);
        addBlank();
        addSection("map_intro.section.special_roles", wrapW);
        boolean anySpecial = false;
        anySpecial |= addIfContains(bagMaps, map.id, "map_intro.special.bag", wrapW);
        anySpecial |= addIfContains(underwaterMaps, map.id, "map_intro.special.underwater", wrapW);
        anySpecial |= addIfContains(policeMaps, map.id, "map_intro.special.police", wrapW);
        anySpecial |= addIfContains(airMaps, map.id, "map_intro.special.air", wrapW);
        if (!anySpecial) {
            addWrapped(Component.translatable("map_intro.special.none").withStyle(ChatFormatting.GRAY), wrapW);
        }
        addBlank();
        addSection("map_intro.section.properties", wrapW);
        JsonObject json = map.json;
        addLine("map_intro.property.room_count", intValue(json, "roomCount", 1), wrapW);
        addTaskSet(json, "disabledTasks", "map_intro.property.disabled_tasks", false, wrapW);
        addTaskSet(json, "enableSceneTask", "map_intro.property.scene_tasks", true, wrapW);
        if (boolValue(json, "minigameQuestEnabled", false)) addLine("map_intro.property.minigame_quest", wrapW);
        String status = stringValue(json, "mapStatusBar", "NONE");
        if (!status.equalsIgnoreCase("NONE") && !status.isBlank()) addLine("map_intro.property.status_bar", statusName(status), wrapW);
        addLine(boolValue(json, "canSwim", false) ? "map_intro.property.can_swim.true" : "map_intro.property.can_swim.false", wrapW);
        if (boolValue(json, "enableOxygenDrowning", false)) addLine("map_intro.property.oxygen_drowning", wrapW);
        addLine(boolValue(json, "canJump", false) ? "map_intro.property.can_jump.true" : "map_intro.property.can_jump.false", wrapW);
        if (boolValue(json, "snowEnabled", false)) addLine("map_intro.property.snow", wrapW);
        if (boolValue(json, "sandEnabled", false)) addLine("map_intro.property.sand", wrapW);
        if (!boolValue(json, "fogEnabled", true)) addLine("map_intro.property.no_fog", wrapW);
        addLine("map_intro.property.fog_end", trimNumber(doubleValue(json, "fogEnd", 200.0D)), wrapW);
        String weather = stringValue(json, "weather", "clear");
        if (!weather.equalsIgnoreCase("clear")) addLine("map_intro.property.weather", weatherName(weather), wrapW);
        double gravity = doubleValue(json, "gravity", 0.08D);
        if (Math.abs(gravity - 0.08D) > 0.0001D) {
            addLine("map_intro.property.gravity", Component.translatable(gravity < 0.08D ? "map_intro.gravity.low" : "map_intro.gravity.high"), wrapW);
        }
        addEffects(json, wrapW);
        addInitialItems(json, wrapW);
        long time = longValue(json, "time", 18000L);
        if (time != 18000L) addLine("map_intro.property.time", Component.translatable(timeName(time)), wrapW);
        if (boolValue(json, "daylightCycle", false)) addLine("map_intro.property.daylight_cycle", wrapW);
        if (boolValue(json, "weatherCycle", false)) addLine("map_intro.property.weather_cycle", wrapW);
    }

    private boolean addIfContains(Set<String> set, String mapId, String key, int wrapW) {
        if (!set.contains(mapId)) return false;
        addWrapped(Component.translatable(key), wrapW);
        return true;
    }

    private void buildBlockDetail(Item item, int wrapW) {
        addWrapped(item.getDescription().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), wrapW);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        addWrapped(Component.translatable("map_intro.block.id", id.toString()).withStyle(ChatFormatting.GRAY), wrapW);
        addBlank();
        String descKey = "map_intro.block." + id.getNamespace() + "." + id.getPath() + ".desc";
        String desc = Language.getInstance().getOrDefault(descKey);
        if (!desc.equals(descKey)) {
            for (String part : desc.split("\\\\n|\\n")) {
                addWrapped(Component.literal(part), wrapW);
            }
        } else {
            addWrapped(Component.translatable("map_intro.block.no_desc").withStyle(ChatFormatting.GRAY), wrapW);
        }
    }

    private void buildMechanicDetail(String id, int wrapW) {
        addWrapped(Component.translatable("map_intro.mechanic." + id + ".title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), wrapW);
        addBlank();
        String text = Language.getInstance().getOrDefault("map_intro.mechanic." + id + ".body");
        for (String part : text.split("\\\\n|\\n")) {
            addWrapped(Component.literal(part), wrapW);
            addBlank();
        }
    }

    private void addTaskSet(JsonObject json, String key, String labelKey, boolean scene, int wrapW) {
        if (!json.has(key) || !json.get(key).isJsonArray() || json.getAsJsonArray(key).isEmpty()) return;
        List<String> names = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            String id = element.getAsString();
            names.add(taskName(id, scene));
        }
        addLine(labelKey, String.join(", ", names), wrapW);
    }

    private String taskName(String id, boolean scene) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (scene) {
            return Component.translatableWithFallback("scene_task.noellesroles." + normalized,
                    Component.translatableWithFallback("task." + normalized, id).getString()).getString();
        }
        if ("raed_book".equals(normalized)) normalized = "read_book";
        return Component.translatableWithFallback("task." + normalized, id).getString();
    }

    private void addEffects(JsonObject json, int wrapW) {
        if (!json.has("effect") || !json.get("effect").isJsonArray() || json.getAsJsonArray("effect").isEmpty()) return;
        List<String> parts = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("effect")) {
            String[] split = element.getAsString().split(",", 2);
            ResourceLocation id = ResourceLocation.tryParse(split[0]);
            int level = split.length > 1 ? parseInt(split[1], 1) : 1;
            String name = split[0];
            if (id != null) {
                var effect = BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
                if (effect != null) name = Component.translatable(effect.value().getDescriptionId()).getString();
            }
            parts.add(Component.translatable("map_intro.effect.entry", name, level).getString());
        }
        addLine("map_intro.property.effects", String.join(", ", parts), wrapW);
    }

    private void addInitialItems(JsonObject json, int wrapW) {
        if (!json.has("initialItems") || !json.get("initialItems").isJsonArray() || json.getAsJsonArray("initialItems").isEmpty()) return;
        List<String> parts = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("initialItems")) {
            String[] split = element.getAsString().split("[;,]", 2);
            ResourceLocation id = ResourceLocation.tryParse(split[0]);
            int count = split.length > 1 ? parseInt(split[1], 1) : 1;
            if (id == null) continue;
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;
            String name = item.getDescription().getString();
            parts.add(count > 1 ? Component.translatable("map_intro.item.entry", name, count).getString() : name);
        }
        if (!parts.isEmpty()) addLine("map_intro.property.initial_items", String.join(", ", parts), wrapW);
    }

    private void addSection(String key, int wrapW) {
        addWrapped(Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), wrapW);
    }

    private void addLine(String key, int wrapW) {
        addWrapped(Component.translatable(key), wrapW);
    }

    private void addLine(String key, Object value, int wrapW) {
        addWrapped(Component.translatable(key, value), wrapW);
    }

    private void addWrapped(Component text, int wrapW) {
        detailLines.addAll(font.split(text, wrapW));
    }

    private void addBlank() {
        detailLines.add(FormattedCharSequence.EMPTY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        computeLayout();
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        graphics.fill(panelX + leftW + GAP / 2, panelY, panelX + leftW + GAP / 2 + 1, panelY + panelH, LINE);
        graphics.drawString(font, title, panelX + PAD, panelY - 13, TEXT, false);
        super.render(graphics, mouseX, mouseY, delta);
        renderTabs(graphics, mouseX, mouseY);
        renderList(graphics, mouseX, mouseY);
        renderDetail(graphics);
        graphics.drawString(font, Component.translatable("map_intro.hint"), panelX + panelW - font.width(Component.translatable("map_intro.hint")), panelY + panelH + 4, MUTED, false);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = panelY + panelH - BOTTOM_H + 5;
        int x = panelX + PAD;
        int totalW = panelW - PAD * 2;
        int each = (totalW - GAP * 3) / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            TabInfo info = tabs.get(i);
            int bx = x + i * (each + GAP);
            int color = info.tab == tab ? info.color : 0xFF293242;
            graphics.fill(bx, y, bx + each, y + TAB_H, color);
            graphics.fill(bx, y + TAB_H - 1, bx + each, y + TAB_H, info.color);
            Component label = Component.translatable(info.labelKey);
            graphics.drawCenteredString(font, label, bx + each / 2, y + 5, 0xFFFFFFFF);
        }
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = panelX + PAD;
        int y = panelY + PAD + TOP_H + GAP;
        int h = panelH - TOP_H - BOTTOM_H - PAD * 2 - GAP;
        int visible = Math.max(1, h / ROW_H);
        int maxScroll = Math.max(0, entries.size() - visible);
        listScroll = Mth.clamp(listScroll, 0, maxScroll);
        for (int i = 0; i < visible && i + listScroll < entries.size(); i++) {
            Entry entry = entries.get(i + listScroll);
            int ry = y + i * ROW_H;
            boolean active = selected != null && entry.sameTarget(selected);
            graphics.fill(x, ry, x + leftW - PAD * 2, ry + ROW_H - 3, active ? 0xFF2B5870 : PANEL_ALT);
            if (entry.item != null) {
                graphics.renderItem(new ItemStack(entry.item), x + 5, ry + 8);
                graphics.drawString(font, entry.name, x + 26, ry + 7, TEXT, false);
                graphics.drawString(font, BuiltInRegistries.ITEM.getKey(entry.item).toString(), x + 26, ry + 19, MUTED, false);
            } else {
                graphics.drawString(font, entry.name, x + 6, ry + 7, TEXT, false);
                graphics.drawString(font, entry.id, x + 6, ry + 19, MUTED, false);
            }
        }
    }

    private void renderDetail(GuiGraphics graphics) {
        int x = panelX + leftW + GAP + PAD;
        int y = panelY + PAD;
        int h = panelH - BOTTOM_H - PAD * 2;
        graphics.fill(x - PAD / 2, y - 1, x + rightW - PAD, y + h, 0x9018202D);
        int visible = Math.max(1, h / 11);
        int maxScroll = Math.max(0, detailLines.size() - visible);
        detailScroll = Mth.clamp(detailScroll, 0, maxScroll);
        for (int i = 0; i < visible && i + detailScroll < detailLines.size(); i++) {
            graphics.drawString(font, detailLines.get(i + detailScroll), x, y + i * 11, TEXT, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int tabY = panelY + panelH - BOTTOM_H + 5;
            int totalW = panelW - PAD * 2;
            int each = (totalW - GAP * 3) / tabs.size();
            for (int i = 0; i < tabs.size(); i++) {
                int bx = panelX + PAD + i * (each + GAP);
                if (inside(mouseX, mouseY, bx, tabY, each, TAB_H)) {
                    tab = tabs.get(i).tab;
                    listScroll = 0;
                    selected = null;
                    rebuildEntries();
                    if (!entries.isEmpty()) selected = entries.get(0);
                    rebuildDetail();
                    return true;
                }
            }
            int listX = panelX + PAD;
            int listY = panelY + PAD + TOP_H + GAP;
            int listH = panelH - TOP_H - BOTTOM_H - PAD * 2 - GAP;
            if (inside(mouseX, mouseY, listX, listY, leftW - PAD * 2, listH)) {
                int idx = (int) ((mouseY - listY) / ROW_H) + listScroll;
                if (idx >= 0 && idx < entries.size()) {
                    selected = entries.get(idx);
                    rebuildDetail();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < panelX + leftW) {
            listScroll = Math.max(0, listScroll - (int) Math.signum(verticalAmount));
        } else {
            detailScroll = Math.max(0, detailScroll - (int) Math.signum(verticalAmount) * 3);
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean handled = super.charTyped(codePoint, modifiers);
        rebuildEntries();
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        rebuildEntries();
        return handled;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static List<Item> sceneBlockItems() {
        return List.of(
                ModSceneBlocks.POISON_ZONE.asItem(), ModSceneBlocks.BREAKING_BRIDGE.asItem(),
                ModSceneBlocks.SABOTAGE_BRIDGE.asItem(), ModSceneBlocks.DRIPPING_STALACTITE.asItem(),
                ModSceneBlocks.FOG_ZONE.asItem(), ModSceneBlocks.MANHOLE.asItem(), ModSceneBlocks.CELLAR.asItem(),
                ModSceneBlocks.SCENE_GATE.asItem(), ModSceneBlocks.FLAMETHROWER.asItem(),
                ModSceneBlocks.ROLLING_STONE_TRIGGER.asItem(), ModSceneBlocks.TRAIN_TARGET.asItem(),
                ModSceneBlocks.INCINERATOR.asItem(), ModSceneBlocks.MOVING_PLATFORM.asItem(),
                ModSceneBlocks.HURRICANE_DEVICE.asItem(), ModSceneBlocks.COFFIN.asItem(), ModSceneBlocks.WATER_PUMP.asItem(),
                ModSceneBlocks.TRASH_CAN.asItem(),
                ModBlocks.VENDING_MACHINES_BLOCK.asItem(), ModBlocks.LOTTERY_MACHINE_BLOCK.asItem(),
                ModBlocks.DEVIL_ROULETTE_TABLE.asItem(), ModBlocks.HOTBAR_STORAGE.asItem(),
                ModBlocks.SUPPLY_CRATE_BLOCK.asItem(), ModBlocks.KILL_BLOCK.asItem(), ModBlocks.KILL_BLOCK_PANEL.asItem(),
                SREBlocks.TRAIN_LIGHT.asItem(), SREBlocks.REMOTE_REDSTONE.asItem(),
                TMMBlocks.TRIMMED_LANTERN.asItem(), TMMBlocks.WALL_LAMP.asItem(), TMMBlocks.NEON_PILLAR.asItem(),
                TMMBlocks.NEON_TUBE.asItem(), TMMBlocks.ENTITY_INTERACTION_BLOCK_ITEM,
                TMMBlocks.ENTITY_INTERACTION_PANEL_ITEM, TMMBlocks.TICKET_OFFICE_ITEM,
                TMMBlocks.TICKET_GATE_ITEM, TMMBlocks.EFFECT_GENERATOR_ITEM);
    }

    private static List<Item> questBlockItems() {
        return List.of(
                ModSceneBlocks.REACTOR.asItem(), ModSceneBlocks.WATER_VALVE.asItem(), ModSceneBlocks.DEBRIS_PILE.asItem(),
                ModSceneBlocks.STOVE.asItem(), ModSceneBlocks.DUST.asItem(), ModSceneBlocks.TRANSPORT_POINT.asItem(),
                ModSceneBlocks.STATUE.asItem(), ModSceneBlocks.BUSH.asItem(), ModSceneBlocks.CROP.asItem(),
                Items.BLACK_CONCRETE, Items.NOTE_BLOCK, Items.LECTERN);
    }

    private static String statusName(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "COLD", "WARM", "WARMTH" -> Component.translatable("map_intro.status.warmth").getString();
            case "THIRST" -> Component.translatable("map_intro.status.thirst").getString();
            case "HUNGER" -> Component.translatable("map_intro.status.hunger").getString();
            default -> value;
        };
    }

    private static Component weatherName(String value) {
        return Component.translatableWithFallback("map_intro.weather." + value.toLowerCase(Locale.ROOT), value);
    }

    private static String timeName(long time) {
        long t = Math.floorMod(time, 24000L);
        long[] points = {6000L, 12000L, 18000L, 23000L};
        String[] keys = {"map_intro.time.noon", "map_intro.time.dusk", "map_intro.time.midnight", "map_intro.time.dawn"};
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < points.length; i++) {
            long dist = Math.min(Math.abs(t - points[i]), 24000L - Math.abs(t - points[i]));
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return keys[best];
    }

    private static int intValue(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static long longValue(JsonObject json, String key, long fallback) {
        return json.has(key) ? json.get(key).getAsLong() : fallback;
    }

    private static double doubleValue(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    private static boolean boolValue(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static String stringValue(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String trimNumber(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001D ? String.valueOf((int) Math.rint(value)) : String.format(Locale.ROOT, "%.2f", value);
    }

    private enum Tab {
        MAP_PROPERTIES, SCENE_BLOCKS, QUEST_BLOCKS, MECHANICS
    }

    private record TabInfo(Tab tab, String labelKey, int color) {
    }

    private static final class MapEntry {
        final String id;
        final JsonObject json;
        final Component name;

        MapEntry(String id, JsonObject json) {
            this.id = id;
            this.json = json;
            this.name = Component.translatableWithFallback("map." + id + ".name", id);
        }
    }

    private static final class Entry {
        final String id;
        final Component name;
        final Tab tab;
        final MapEntry map;
        final Item item;

        private Entry(String id, Component name, Tab tab, MapEntry map, Item item) {
            this.id = id;
            this.name = name;
            this.tab = tab;
            this.map = map;
            this.item = item;
        }

        static Entry map(MapEntry map, Component name) {
            return new Entry(map.id, name, Tab.MAP_PROPERTIES, map, null);
        }

        static Entry item(Item item, Component name, Tab tab) {
            return new Entry(BuiltInRegistries.ITEM.getKey(item).toString(), name, tab, null, item);
        }

        static Entry text(String id, Component name, Tab tab) {
            return new Entry(id, name, tab, null, null);
        }

        boolean sameTarget(Entry other) {
            return other != null && tab == other.tab && id.equals(other.id);
        }
    }
}
