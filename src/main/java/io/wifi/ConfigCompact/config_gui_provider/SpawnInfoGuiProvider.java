package io.wifi.ConfigCompact.config_gui_provider;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.agmas.noellesroles.config.NoellesRolesConfig.RoleSpawnInfoEntries;
import org.agmas.noellesroles.config.NoellesRolesConfig.SpawnInfo;
import org.agmas.noellesroles.utils.RoleUtils;

import com.google.common.collect.Iterators;

import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.MultiElementListEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class SpawnInfoGuiProvider {

    // ---------- 内部可编辑条目 ----------
    private static class EditableEntry {
        String key;
        Component name;
        SpawnInfo spawnInfo;

        EditableEntry(String key, SpawnInfo info) {
            this.key = key;
            this.spawnInfo = info;
            this.name = Component.literal(key != null ? key : "(empty)");
        }

        EditableEntry(String key, SpawnInfo info, Component name) {
            this.key = key;
            this.spawnInfo = info;
            this.name = name != null ? name : Component.literal(key != null ? key : "(empty)");
        }
    }

    // ---------- 可保存且支持搜索的 MultiElementListEntry 分页 ----------
    private static class SavablePageEntry extends MultiElementListEntry<Object> {
        private final Runnable saveCallback;
        private final String pageLabel;

        public SavablePageEntry(Component pageTitle, List<AbstractConfigListEntry<?>> entries,
                boolean defaultExpanded, Runnable saveCallback) {
            super(pageTitle, null, entries, defaultExpanded);
            this.saveCallback = saveCallback;
            this.pageLabel = pageTitle.getString();
        }

        @Override
        public void save() {
            super.save();
            if (saveCallback != null) {
                saveCallback.run();
            }
        }

        @Override
        public Iterator<String> getSearchTags() {
            // 添加分页标题作为搜索标签
            return Iterators.concat(
                    super.getSearchTags(),
                    Stream.of(pageLabel).iterator()
            );
        }
    }

    // ---------- 支持搜索的行条目 ----------
    private static class SearchableRowEntry extends MultiElementListEntry<EditableEntry> {
        private final EditableEntry entry;

        public SearchableRowEntry(Component title, EditableEntry entry,
                List<AbstractConfigListEntry<?>> entries, boolean defaultExpanded) {
            super(title, entry, entries, defaultExpanded);
            this.entry = entry;
        }

        @Override
        public Iterator<String> getSearchTags() {
            // 添加 key 和 name 的文本作为搜索标签
            Iterator<String> baseTags = super.getSearchTags();
            if (entry == null) {
                return baseTags;
            }
            String keyTag = entry.key != null ? entry.key : "";
            String nameTag = entry.name != null ? entry.name.getString() : "";
            return Iterators.concat(
                    baseTags,
                    Stream.of(keyTag, nameTag).filter(s -> !s.isEmpty()).iterator()
            );
        }
    }

    // ---------- 注册到 AutoConfig ----------
    public static void register(GuiRegistry registry) {
        registry.registerPredicateProvider(
                SpawnInfoGuiProvider::provide,
                field -> field.getType() == RoleSpawnInfoEntries.class);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<AbstractConfigListEntry> provide(
            String i18n,
            Field field,
            Object config,
            Object defaults,
            GuiRegistryAccess access) {

        RoleSpawnInfoEntries currentObj = (RoleSpawnInfoEntries) getFieldValue(field, config);
        RoleSpawnInfoEntries defaultObj = (RoleSpawnInfoEntries) getFieldValue(field, defaults);

        if (currentObj == null) {
            currentObj = new RoleSpawnInfoEntries();
            if (defaultObj != null) {
                currentObj = defaultObj;
            }
            setFieldValue(field, config, currentObj);
        }
        if (defaultObj == null) {
            if (currentObj.type == 1) {
                defaultObj = RoleSpawnInfoEntries.createDefaultRoleInfo();
            } else if (currentObj.type == 2) {
                defaultObj = RoleSpawnInfoEntries.createDefaultModifierInfo();
            } else {
                defaultObj = new RoleSpawnInfoEntries();
            }
            setFieldValue(field, defaults, defaultObj);
        }

        // 构建当前条目列表和默认条目列表
        List<EditableEntry> entries = toEditableList(currentObj.maps, currentObj.type);
        List<EditableEntry> defaultEntries = toEditableList(defaultObj.maps, defaultObj.type);

        int pageSize = 20;
        List<SavablePageEntry> pageContainers = new ArrayList<>();
        List<SearchableRowEntry> allRowContainers = new ArrayList<>();

        // 如果没有条目，显示一个空分页
        if (entries.isEmpty()) {
            List<AbstractConfigListEntry<?>> emptyRowList = new ArrayList<>();
            SearchableRowEntry emptyRow = buildRow(
                    new EditableEntry("", new SpawnInfo()),
                    new EditableEntry("", new SpawnInfo()),
                    i18n);
            emptyRowList.add(emptyRow);
            allRowContainers.add(emptyRow);

            SavablePageEntry emptyPage = new SavablePageEntry(
                    Component.literal("0-0"),
                    emptyRowList,
                    false,
                    () -> saveAllRows(field, config, allRowContainers));
            pageContainers.add(emptyPage);
        } else {
            for (int i = 0; i < entries.size(); i += pageSize) {
                int start = i;
                int end = Math.min(i + pageSize, entries.size());
                List<EditableEntry> subEntries = entries.subList(start, end);
                List<EditableEntry> subDefaults = defaultEntries.size() > start
                        ? defaultEntries.subList(start, Math.min(end, defaultEntries.size()))
                        : Collections.emptyList();

                Component pageTitle = Component.literal((start + 1) + "-" + end);
                List<AbstractConfigListEntry<?>> pageChildren = new ArrayList<>();

                // 构建该分页下的每一行
                for (int j = 0; j < subEntries.size(); j++) {
                    EditableEntry current = subEntries.get(j);
                    EditableEntry def = (j < subDefaults.size()) ? subDefaults.get(j) : null;
                    if (def == null) {
                        def = new EditableEntry(current.key, new SpawnInfo());
                    }
                    SearchableRowEntry row = buildRow(current, def, i18n);
                    pageChildren.add(row);
                    allRowContainers.add(row);
                }

                SavablePageEntry page = new SavablePageEntry(
                        pageTitle,
                        pageChildren,
                        false,
                        () -> saveAllRows(field, config, allRowContainers));
                pageContainers.add(page);
            }
        }

        // 父级容器
        List<AbstractConfigListEntry<?>> parentChildren = new ArrayList<>(pageContainers);
        MultiElementListEntry<Object> parentEntry = new MultiElementListEntry<>(
                Component.translatable(i18n),
                null,
                parentChildren,
                false);

        return Collections.singletonList(parentEntry);
    }

    // ---------- 保存所有行数据到配置 ----------
    private static void saveAllRows(Field field, Object config,
            List<SearchableRowEntry> allRowContainers) {
        List<EditableEntry> allEntries = new ArrayList<>();
        for (SearchableRowEntry row : allRowContainers) {
            EditableEntry value = row.getValue();
            if (value != null) {
                allEntries.add(value);
            }
        }
        saveBack(field, config, allEntries);
    }

    // ---------- 构建每一行（返回 SearchableRowEntry） ----------
    private static SearchableRowEntry buildRow(
            EditableEntry currentEntry,
            EditableEntry defaultEntry,
            String i18n) {

        ConfigEntryBuilder eb = ConfigEntryBuilder.create();

        List<AbstractConfigListEntry<?>> controls = buildSpawnInfoControls(
                currentEntry.spawnInfo,
                defaultEntry.spawnInfo,
                i18n);

        Component title = currentEntry.name != null ? currentEntry.name
                : Component.literal(currentEntry.key != null ? currentEntry.key : "(empty)");

        return new SearchableRowEntry(
                title,
                currentEntry,
                controls,
                false);
    }

    // ---------- 通过反射生成 SpawnInfo 字段控件，并使用默认值 ----------
    private static List<AbstractConfigListEntry<?>> buildSpawnInfoControls(
            SpawnInfo info,
            SpawnInfo defaultInfo,
            String i18n) {
        List<AbstractConfigListEntry<?>> list = new ArrayList<>();
        ConfigEntryBuilder eb = ConfigEntryBuilder.create();

        for (Field field : SpawnInfo.class.getDeclaredFields()) {
            field.setAccessible(true);
            String fieldName = field.getName();
            String translationKey = i18n + "." + fieldName;

            try {
                Class<?> type = field.getType();
                Object currentValue = field.get(info);
                Object defaultValue = field.get(defaultInfo);

                AbstractConfigListEntry<?> control = createControlForField(
                        eb, translationKey, type, currentValue, defaultValue, field, info);
                if (control != null) {
                    list.add(control);
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    // ---------- 根据字段类型创建控件，使用默认值 ----------
    @SuppressWarnings("unchecked")
    private static AbstractConfigListEntry<?> createControlForField(
            ConfigEntryBuilder eb,
            String translationKey,
            Class<?> type,
            Object currentValue,
            Object defaultValue,
            Field field,
            SpawnInfo info) {

        Component label = Component.translatable(translationKey);

        if (type == int.class || type == Integer.class) {
            int val = currentValue != null ? (int) currentValue : 0;
            int def = defaultValue != null ? (int) defaultValue : 0;
            return eb.startIntField(label, val)
                    .setDefaultValue(def)
                    .setSaveConsumer(v -> {
                        try {
                            field.set(info, v);
                        } catch (IllegalAccessException ignored) {
                        }
                    })
                    .build();
        }
        if (type == long.class || type == Long.class) {
            long val = currentValue != null ? (long) currentValue : 0L;
            long def = defaultValue != null ? (long) defaultValue : 0L;
            return eb.startLongField(label, val)
                    .setDefaultValue(def)
                    .setSaveConsumer(v -> {
                        try {
                            field.set(info, v);
                        } catch (IllegalAccessException ignored) {
                        }
                    })
                    .build();
        }
        if (type == float.class || type == Float.class) {
            float val = currentValue != null ? (float) currentValue : 0f;
            float def = defaultValue != null ? (float) defaultValue : 0f;
            return eb.startFloatField(label, val)
                    .setDefaultValue(def)
                    .setSaveConsumer(v -> {
                        try {
                            field.set(info, v);
                        } catch (IllegalAccessException ignored) {
                        }
                    })
                    .build();
        }
        if (type == double.class || type == Double.class) {
            double val = currentValue != null ? (double) currentValue : 0.0;
            double def = defaultValue != null ? (double) defaultValue : 0.0;
            return eb.startDoubleField(label, val)
                    .setDefaultValue(def)
                    .setSaveConsumer(v -> {
                        try {
                            field.set(info, v);
                        } catch (IllegalAccessException ignored) {
                        }
                    })
                    .build();
        }
        if (type == boolean.class || type == Boolean.class) {
            boolean val = currentValue != null && (boolean) currentValue;
            boolean def = defaultValue != null && (boolean) defaultValue;
            return eb.startBooleanToggle(label, val)
                    .setDefaultValue(def)
                    .setSaveConsumer(v -> {
                        try {
                            field.set(info, v);
                        } catch (IllegalAccessException ignored) {
                        }
                    })
                    .build();
        }
        if (type == String.class) {
            String val = currentValue != null ? (String) currentValue : "";
            String def = defaultValue != null ? (String) defaultValue : "";
            return eb.startStrField(label, val)
                    .setDefaultValue(def)
                    .setSaveConsumer(v -> {
                        try {
                            field.set(info, v);
                        } catch (IllegalAccessException ignored) {
                        }
                    })
                    .build();
        }
        if (type == ArrayList.class) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 1 && args[0] == String.class) {
                    List<String> currentList = (List<String>) currentValue;
                    if (currentList == null) {
                        currentList = new ArrayList<>();
                    }
                    List<String> defaultList = (List<String>) defaultValue;
                    if (defaultList == null) {
                        defaultList = new ArrayList<>();
                    }
                    List<String> finalCurrentList = currentList;
                    List<String> finalDefaultList = defaultList;
                    return new StringListListEntry(
                            label,
                            finalCurrentList,
                            false,
                            () -> Optional.empty(),
                            newList -> {
                                try {
                                    field.set(info, newList);
                                } catch (IllegalAccessException ignored) {
                                }
                            },
                            () -> new ArrayList<>(finalDefaultList),
                            Component.translatable("text.cloth-config.reset_value"),
                            false,
                            true,
                            false);
                }
            }
        }
        // fallback
        String val = currentValue != null ? currentValue.toString() : "";
        String def = defaultValue != null ? defaultValue.toString() : "";
        return eb.startStrField(label, val)
                .setDefaultValue(def)
                .setSaveConsumer(v -> {
                    try {
                        field.set(info, v);
                    } catch (IllegalAccessException ignored) {
                    }
                })
                .build();
    }

    // ---------- 工具方法 ----------
    private static Object getFieldValue(Field field, Object obj) {
        try {
            field.setAccessible(true);
            return field.get(obj);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static void setFieldValue(Field field, Object obj, Object value) {
        try {
            field.setAccessible(true);
            field.set(obj, value);
        } catch (IllegalAccessException ignored) {
        }
    }

    private static List<EditableEntry> toEditableList(HashMap<ResourceLocation, SpawnInfo> map, int type) {
        if (map == null)
            return new ArrayList<>();
        return map.entrySet().stream()
                .map(e -> new EditableEntry(e.getKey().toString(), cloneSpawnInfo(e.getValue()),
                        getKeyName(e.getKey(), type)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static Component getKeyName(ResourceLocation key, int type) {
        if (type == 1) {
            return RoleUtils.getRoleName(key).append(Component.translatable(" (%s)", key.toString()));
        } else if (type == 2) {
            return RoleUtils.getModifierName(key).append(Component.translatable(" (%s)", key.toString()));
        }
        return Component.literal(key.toString());
    }

    private static SpawnInfo cloneSpawnInfo(SpawnInfo original) {
        if (original == null)
            return new SpawnInfo();
        SpawnInfo copy = new SpawnInfo(
                original.minEnabledPlayer,
                original.maxEnabledPlayer,
                original.enableChance,
                original.maxSpawn);
        if (original.map != null) {
            copy.map = new ArrayList<>(original.map);
        }
        return copy;
    }

    private static void saveBack(Field field, Object config, List<EditableEntry> updated) {
        RoleSpawnInfoEntries obj = (RoleSpawnInfoEntries) getFieldValue(field, config);
        if (obj == null) {
            obj = new RoleSpawnInfoEntries();
            setFieldValue(field, config, obj);
        }
        int originalType = obj.type;

        HashMap<ResourceLocation, SpawnInfo> newMap = new HashMap<>();
        for (EditableEntry e : updated) {
            if (e.key == null || e.key.isBlank())
                continue;
            ResourceLocation rl = ResourceLocation.tryParse(e.key);
            if (rl == null)
                continue;
            newMap.put(rl, e.spawnInfo);
        }
        obj.maps = newMap;
        obj.type = originalType;
    }
}