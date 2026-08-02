# 玖璃、芙妮、風太 — 新增三名香港 Vtuber 角色

## 概述
沿用白狐2.0 / 哈力克2.0 的開發管道（API / 事件優先，不更動 `io/wifi/starrailexpress/` 內程式），新增三個角色：
- **玖璃**（`NINE_MUI`，9mui）— 殺手陣營
- **芙妮**（`EVERLY`，Everly）— 警長陣營
- **風太**（`FU_TAI`，Fu_Tai）— 平民陣營

三者皆帶有 `hkvtuber` 標籤，`setCanSeeCoin(true)`、`setDefaultMax(1)`。

---

## 玖璃（殺手）

### 主動技 1（G）— 信仰之力
- 消耗 **100 金幣**，獲得企鵝公主雕像的力量（**速度 II**）**10 秒**
- 冷卻 **120 秒**（`cooldownSeconds(120)`）

### 被動 — 石化狀態
- 每分鐘有 **30%** 機率進入**石化**狀態 **10 秒**
- 石化時：無法手機時動彈（`MOVE_BANED / TURN_BANED / USED_BANED / INVENTORY_BANED`）、無法說話/對話（`VOICE_SILENCE / CHAT_BAN`），同時**無敵**（`INVINCIBLE`）

---

## 芙妮（警長）

### 主動技 1（G）— 時間停止
- 使**全場時間停止 3 秒**，每局最多 **2 次**（`charges(2)`，連同組件 `timeStopUsed` 上限）
- 冷卻 **60 秒**（`cooldownSeconds(60)`）
- 透過 `TimeStopEffect.tryTriggerStart(serverPlayer, 60, title)`（60 ticks = 3 秒）觸發，並顯示技能標題

### 被動 — 無視時間停止
- 身為時間管理局成員，**免疫所有時間停止**：`TimeStopEffect` 於 `canMovePlayers` 中對 `ModRoles.EVERLY` 加入豁免（比照 `CLOCKMAKER`）

---

## 風太（平民）

### 主動技 1（G）— 神諭
- 消耗 **200 金幣**，得知剩餘**殺手**及**中立**（含中立）數量
- 判定方式：`role.canUseKiller() && !role.isInnocent() && !role.isNeutrals()` → 殺手；`role.isNeutrals()` → 中立
- 冷卻 **120 秒**（`cooldownSeconds(120)`）

### 被動 — 巫女祝福
- 抵擋**一次任何方式死亡**（監聽 `AllowPlayerDeathWithKiller.EVENT` / `AllowPlayerDeath.EVENT`，返回 `false` 攔截）
- 觸發時回復滿血並獲得 **3 秒** 回復（`REGENERATION`）

---

## 元件結構

### 新增檔案
| 檔案 | 說明 |
|------|------|
| `game/roles/killer/nine_mui/NineMuiPlayerComponent.java` | 玖璃 CCA 元件（信仰之力、石化被動） |
| `game/roles/vigilante/everly/EverlyPlayerComponent.java` | 芙妮 CCA 元件（時間停止、每局 2 次上限） |
| `game/roles/innocence/futai/FuTaiPlayerComponent.java` | 風太 CCA 元件（神諱、巫女祝福 static 註冊） |

### 修改檔案
| 檔案 | 內容 |
|------|------|
| `role/ModRoles.java` | 註冊 `NINE_MUI`（殺手、淡紫）、`EVERLY`（警長、`setVigilanteTeam(true)` ＋ `setCanPickUpRevolver(true)`）、`FU_TAI`（平民、暖紅）；`canSyncedRolePaths` 加入三路徑 |
| `component/ModComponents.java` | 三組 CCA `ComponentKey`（delegate 至元件類別 KEY，`RespawnCopyStrategy.NEVER_COPY`，`beginRegistration`） |
| `init/ModRolesInitialEventRegister.java` | 註冊三個 Skill entry（`nine_mui_blessing` / `everly_timestop` / `futai_oracle`） |
| `content/effects/TimeStopEffect.java` | `canMoves` 加入 `ModRoles.EVERLY` 豁免（免疫時間停止） |
| `resources/fabric.mod.json` | CCA 註冊 `noellesroles:nine_mui` / `noellesroles:everly` / `noellesroles:futai` |

### 按鍵
- 全部復用既有 **G** 鍵（與其他角色相同），**不新增**按鍵綁定，且為常數註冊。

---

## 翻譯（三語齊備）

`assets/noellesroles/lang/{zh_cn,zh_tw,en_us}.json`：
- `announcement.star.role.nine_mui` / `.goals`、`announcement.star.role.everly` / `.goals`、`announcement.star.role.futai` / `.goals`
- `skill.noellesroles.nine_mui.*`（blessing / blessing_on / petrified_on）
- `skill.noellesroles.everly.*`（timestop）
- `skill.noellesroles.futai.*`（oracle）
- `message.noellesroles.nine_mui.*`（not_enough_money）
- `message.noellesroles.everly.*`（timestop_used_out / timestop_final）
- `message.noellesroles.futai.*`（not_enough_money / oracle_result / bless_saved）

`assets/role_modifier_intro/lang/{zh_cn,zh_tw,en_us}.json`：
- `info.screen.roleid.nine_mui` / `.simple`、`info.screen.roleid.everly` / `.simple`、`info.screen.roleid.futai` / `.simple`

所有 key 皆與程式碼實際引用一致（已核對無重複，六大 JSON 以 node 嚴格驗證通過）。

---

## 建構與驗證

- `.\gradlew.bat compileJava`：通過（僅既有 deprecated/unchecked 警告）。
- `.\gradlew.bat build`：通過，產出 `build/libs/star_rail_express-4.3.5.jar`（2026-08-02）。
- 已驗證 jar 內含 `NineMuiPlayerComponent / EverlyPlayerComponent / FuTaiPlayerComponent` class。
- 未更動模組版本號（版本號由人工手動）。