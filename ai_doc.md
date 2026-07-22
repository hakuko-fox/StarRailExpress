# AI指导
请不要修改现有内容！

请不要修改别人的代码！

尽量使用API和各种Event而不是直接写进代码甚至是mixin。

如果实在没办法请告知用户让其自行鉴定后修改。

# 有关Component（CCA）
如果你打算写冷却等需要ticking的cca：
- 请服务端尽量在重大更改时同步，而不是每秒同步！
- 如果是类似于 cooldown-- 的需要同步的逻辑，每10s再同步。
- 或者使用 `level.getGameTime() + time` 设定触发时间来代替（只需要在触发和结束的时候同步更改）（推荐）

# 新角色開發流程

以新增一個平民陣營角色（如 Halic）為例：

## 1. CCA Component（角色資料與技能邏輯）
- 在 `src/main/java/org/agmas/noellesroles/game/roles/innocence/` 下以角色名新建 package
- 建立 `XxxPlayerComponent.java`，實作 `RoleComponent` + `ServerTickingComponent`
- ComponentKey 使用 `ComponentRegistry.getOrCreate()` 註冊，ID 格式為 `noellesroles:xxx`

## 2. ModComponents.java — 註冊 Component Key
- 在 `org.agmas.noellesroles.component.ModComponents` 中加上 `public static final ComponentKey<XxxPlayerComponent> XXX = XxxPlayerComponent.KEY;`
- 在 `init()` 中註冊 entity factory

## 3. fabric.mod.json — CCA 註冊
- 在 `custom.cardinal-components` 陣列中加入 `"noellesroles:xxx"`

## 4. ModRoles.java — 定義角色
- 在 `org.agmas.noellesroles.role.ModRoles` 中建立 `NormalRole` 匿名實例
- 設定 camp（isInnocent / canUseKiller）、角色 ID、顯示名稱
- 如有固定皮膚，覆寫 `getNormalSkin()`
- 在 `init()` 中將角色 ID path 加入 `SREPlayerMoodComponent.canSyncedRolePaths`

## 5. ModRolesInitialEventRegister.java — 註冊技能
- 使用 `SkillBuilder.createSkill().withXxx()` 鏈式建立技能
- 技能1（G 鍵）：預設不 shifted
- 技能2（Shift+G）：設定 `setShifted(true)`
- 技能冷卻用 `setCooldown()`（單位：tick，20 ticks = 1 秒）

## 6. 翻譯文件
- `assets/noellesroles/lang/{zh_cn,zh_tw,en_us}.json` — 角色名稱、技能描述等 key
- `assets/role_modifier_intro/lang/{zh_cn,zh_tw,en_us}.json` — 角色介紹畫面文字（key: `info.screen.roleid.xxx` 與 `info.screen.roleid.xxx.simple`）

## 7. 實體模型變身（如白狐變狐狸、皮革噶變豬）

如果需要把玩家渲染成動物實體（如白狐變白色狐狸），需要額外實作：

### 7a. Component 新增方法
- 在 `XxxPlayerComponent` 中新增 `isDisguised()` 方法（回傳 disguise state）
- 新增 `static isDisguised(Player)` 靜態輔助方法，供 mixin/renderer 使用
- 在 disguise state 改變時呼叫 `player.refreshDimensions()` 更新眼高

### 7b. Client 端 Disguise Renderer
- 在 `org.agmas.noellesroles.client` 下建立 `XxxDisguiseRenderer.java`
- 維護一個 `Map<UUID, AnimalEntity> ENTITIES` 儲存不入世界的客戶端動物實體
- 渲染時逐幀複製玩家的位置、旋轉、行走動畫到動物實體
- 呼叫 `EntityRenderDispatcher.getRenderer(animal).render()` 繪製
- 範例參考：`HakukoFoxDisguiseRenderer.java`、`LeatherPigDisguiseRenderer.java`

### 7c. Mixin（共 3 個）

| Mixin | 位置 | 作用 |
|-------|------|------|
| `XxxPlayerRenderMixin` | `mixin/client/roles/xxx/` | 攔截 `PlayerRenderer.render()` HEAD，取消原渲染並委託給 DisguiseRenderer |
| `XxxSelfRenderMixin` | `mixin/client/roles/xxx/` | `LevelRenderer.renderLevel` 中攔截 `Camera.isDetached()`，讓第一人稱也能看到自己。**注意**：若動物模型過大（如狐狸），第一人稱視野會被模型阻擋，此時應移除該 Mixin，只靠第三人稱顯示 |
| `XxxEyeHeightMixin` | `mixin/roles/xxx/` | `Player.getDefaultDimensions()` 中修改眼高（狐狸約 0.6F） |

### 7d. Mixin 註冊
- 在 `noellesroles.mixins.json` 中：
  - `mixins` 列表加入 `"roles.xxx.XxxEyeHeightMixin"`
  - `client` 列表加入 `"client.roles.xxx.XxxPlayerRenderMixin"` 和 `"client.roles.xxx.XxxSelfRenderMixin"`

## 8. 消耗金幣的技能（Coin Skill）

需求：技能消耗玩家持有的遊戲內金幣（非帳號級貨幣），使用 `SREPlayerShopComponent`。

### 8a. 讀取金幣與扣款
- 匯入 `io.wifi.starrailexpress.cca.SREPlayerShopComponent`
- 取得 component：`var shop = SREPlayerShopComponent.KEY.get(player)`
- 讀取餘額：`shop.balance`
- 扣款：`shop.addToBalance(-cost)`（**不要**直接修改 `shop.balance` 後手動呼叫 `sync()`，`addToBalance` 已自帶同步）
- 若使用 `shop.balance -= cost` + `shop.sync()` 亦可，但推薦統一使用 `addToBalance`

### 8b. 標準扣款模板
```java
int cost = 50;
var shop = SREPlayerShopComponent.KEY.get(sp);
if (shop.balance < cost) {
    sp.displayClientMessage(
            Component.translatable("message.noellesroles.xxx.not_enough_money", cost),
            true);
    return false;
}
shop.addToBalance(-cost);
```

### 8c. 不得使用 PlayerEconomyManager
`PlayerEconomyManager` 是永續儲存的帳號級貨幣，**不要**用它做技能消耗。一律使用 `SREPlayerShopComponent`。

### 8d. 設定初始金幣
可在 `ModRolesInitialEventRegister` 的 `ModdedRoleAssigned.EVENT` 中透過 `SREPlayerShopComponent.KEY.get(player).setBalance(N)` 設定角色開局金幣。範例：
```java
if (role.equals(ModRoles.BROADCASTER)) {
    SREPlayerShopComponent.KEY.get(player).setBalance(200);
}
```

### 8e. 實例
- 哈力克 `restoreSanity()`：扣 50 金幣，恢復範圍 8 格內玩家理智
- 白狐 `foxFire()`：扣 40 金幣，使範圍 6 格內玩家失明 + 發光
- 滯時鬼 `delayer_anchor`：扣 config 設定金幣，錨定狀態
