package io.wifi.starrailexpress.game.data;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import net.minecraft.core.BlockPos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MapConfig {
    public static final Gson gson = new Gson();
    public static MapConfig instance;
    
    @SerializedName("maps")
    public List<MapEntry> maps;
    
    public static class MapEntry {
        @SerializedName("id")
        public String id;
        
        @SerializedName("displayName")
        public String displayName;

        @SerializedName("maxcount")
        public int maxCount;

        @SerializedName("canSelect")
        public boolean canSelect = true;

        @SerializedName("description")
        public String description;
        
        @SerializedName("color")
        public String color;

        // 禁用的任务列表（任务名称，如 "SLEEP", "EXERCISE" 等）
        @SerializedName("disabledTasks")
        public List<String> disabledTasks = new ArrayList<>();
        
        // 支持的游戏模式列表（如 "repairmode", "murder" 等），为空表示支持所有模式
        @SerializedName("gameModes")
        public List<String> gameModes = new ArrayList<>();

        @SerializedName("repair")
        public RepairConfig repair;
        
        // 用于运行时转换颜色值
        public transient int parsedColor;
        
        public MapEntry(String id, String displayName, String description, String color, int maxCount) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.color = color;
            this.parsedColor = parseColor(color);
            this.maxCount = maxCount;
        }
        
        // 为Gson反序列化添加无参构造函数
        public MapEntry() {}
        
        public String getId() {
            return id;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
        
        public int getColor() {
            if (parsedColor == 0) {
                parsedColor = parseColor(color);
            }
            return parsedColor;
        }
        
        public int parseColor(String colorStr) {
            if (colorStr != null && colorStr.startsWith("0x")) {
                try {
                    return (int) Long.parseLong(colorStr.substring(2), 16);
                } catch (NumberFormatException e) {
                    return 0xFFFFFFFF; // 默认白色
                }
            }
            return 0xFFFFFFFF; // 默认白色
        }
        
        // Getter方法用于序列化
        public String getColorStr() {
            return color;
        }
    }

    public static class RepairConfig {
        @SerializedName("cloneEntries")
        public List<CloneEntry> cloneEntries = new ArrayList<>();
        @SerializedName("repairStations")
        public List<Pos> repairStations = new ArrayList<>();
        @SerializedName("lockedDoors")
        public List<LockedDoorEntry> lockedDoors = new ArrayList<>();
        @SerializedName("lootPoints")
        public List<LootPointEntry> lootPoints = new ArrayList<>();
        @SerializedName("escapeRoutes")
        public List<EscapeRouteEntry> escapeRoutes = new ArrayList<>();
        @SerializedName("trialStands")
        public List<Pos> trialStands = new ArrayList<>();
        @SerializedName("hunterSpawns")
        public List<Pos> hunterSpawns = new ArrayList<>();
        @SerializedName("survivorSpawns")
        public List<Pos> survivorSpawns = new ArrayList<>();
    }

    public static class CloneEntry {
        @SerializedName("source")
        public Pos source = new Pos();
        @SerializedName("target")
        public Pos target = new Pos();
        @SerializedName("size")
        public Pos size = new Pos(1, 1, 1);
    }

    public static class LockedDoorEntry {
        @SerializedName("pos")
        public Pos pos = new Pos();
        @SerializedName("lockId")
        public String lockId = "";
        @SerializedName("requiredItem")
        public String requiredItem = "";
        @SerializedName("consume")
        public boolean consume = true;
    }

    public static class LootPointEntry {
        @SerializedName("pos")
        public Pos pos = new Pos();
        @SerializedName("category")
        public String category = "tool";
        @SerializedName("guaranteed")
        public boolean guaranteed = false;
        @SerializedName("chance")
        public double chance = 1.0D;
        @SerializedName("pool")
        public List<String> pool = new ArrayList<>();
    }

    public static class EscapeRouteEntry {
        @SerializedName("id")
        public String id = "";
        @SerializedName("displayKey")
        public String displayKey = "";
        @SerializedName("pos")
        public Pos pos = new Pos();
        @SerializedName("capacity")
        public int capacity = 1;
        @SerializedName("requiredItems")
        public List<String> requiredItems = new ArrayList<>();
    }

    public static class Pos {
        @SerializedName("x")
        public int x;
        @SerializedName("y")
        public int y;
        @SerializedName("z")
        public int z;

        public Pos() {
        }

        public Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }

        public static Pos from(BlockPos pos) {
            return new Pos(pos.getX(), pos.getY(), pos.getZ());
        }
    }
    
    public List<MapEntry> getMaps() {
        return maps;
    }
    
    public static synchronized MapConfig getInstance() {
        if (instance == null) {
            instance = loadConfig();
        }
        return instance;
    }
    
    public static synchronized void reload() {
        instance = loadConfig();
    }
    
    public static MapConfig loadConfig() {
        // 首先尝试从资源目录加载默认配置
        InputStream inputStream = MapConfig.class.getClassLoader()
                .getResourceAsStream("assets/starrailexpress/config/maps.json");
        
        if (inputStream == null) {
            // 如果资源目录中没有配置文件，创建默认配置
            return createDefaultConfig();
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return gson.fromJson(reader, MapConfig.class);
        } catch (IOException e) {
            e.printStackTrace();
            return createDefaultConfig();
        }
    }
    
    public static MapConfig createDefaultConfig() {
        MapConfig config = new MapConfig();
        config.maps = java.util.Arrays.asList(
            new MapEntry("random", 
                "gui.sre.map_selector.random",
                "gui.sre.map_selector.random.desc", 
                "0xFF4CC9F0",100),
            new MapEntry("areas1", 
                "gui.sre.map_selector.zeppelin",
                "gui.sre.map_selector.zeppelin.desc", 
                "0xFF9D0208",100),
            new MapEntry("areas2", 
                "gui.sre.map_selector.star_train_v2",
                "gui.sre.map_selector.star_train_v2.desc", 
                "0xFF70E000",100),
            new MapEntry("areas3", 
                "gui.sre.map_selector.pirate_ship",
                "gui.sre.map_selector.pirate_ship.desc", 
                "0xFFF72585",100),
            new MapEntry("areas4", 
                "gui.sre.map_selector.star_train_expanded",
                "gui.sre.map_selector.star_train_expanded.desc", 
                "0xFF7209B7",100),
            new MapEntry("areas5", 
                "gui.sre.map_selector.original",
                "gui.sre.map_selector.original.desc", 
                "0xFF00B4D8",12),
            new MapEntry("areas6", 
                "gui.sre.map_selector.wider_train",
                "gui.sre.map_selector.wider_train.desc", 
                "0xFFF72585",100)
        );
        return config;
    }
    
    public MapEntry getMapById(String id) {
        if (maps != null) {
            for (MapEntry entry : maps) {
                if (entry.getId().equals(id)) {
                    return entry;
                }
            }
        }
        return null;
    }
}
