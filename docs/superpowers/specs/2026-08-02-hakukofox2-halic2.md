# 白狐2.0 與 哈力克2.0 — 新增角色

## 概述
在不更動既有角色（`HAKUKO_FOX` / `HALIC`）的前提下，新增兩個角色：
- **白狐2.0**（`HAKUKO_FOX2`）— 殺手陣營
- **哈力克2.0**（`HALIC2`）— 平民陣營

兩者皆帶有 `hkvtuber` 標籤，並沿用既有角色開發管道（API / 事件優先，僅極少數必要 Mixin）。

---

## 白狐 2.0（殺手）

### 主動技 1（G）— 修仙化狐
- 按 G 變身為雪狐獸化型態，獲得**速度 II、跳躍 II**，**無限持續時間**（可隨時關閉）
- 獸化時**無法攻擊**（攔截 `Player#attack`）
- 獸化時**受到傷害不會死亡**（免疫死亡）
- 再按 G（或再次觸發）即可回到人類型態，進入 **180 秒冷卻**

### 主動技 2（Shift+G）— 瞬結
- 消耗 **100 金幣**，凍結**其他所有玩家** 5 秒
- **60 秒冷卻**

### 被動 — 修仙成狐
- 開局時**失明 60 秒**
- 60 秒結束後**自動變身**為雪狐

---

## 哈力克 2.0（平民）

### 主動技 1（G）— 量產分身
- 每 **10 秒**冷卻，消耗 **10 金幣**，生成一隻**永久存在**的哈力克分身
- 分身被攻擊時**消失**，且**攻擊者失明 5 秒**
- 完整復用既有分身管線（`PuppeteerBodyEntity` + `setHalicDecoy(true)`，`playerHurt` 已處理受擊行為）

### 主動技 2（Shift+G）— 漏電
- **每局遊戲最多使用 1 次**，消耗 **50 金幣**
- 電暈（停止行動）7 格內所有玩家，持續 **7 秒**
- 效果以 `ModEffects.MOVE_BANED / USED_BANED / INVENTORY_BANED` 實作

### 被動
- **無法被殺手識破透視**（`SRERole.setAllBeSeenInstinctType(InstinctType.NONE)`，不寫新程式碼）
- **無法購買武器**（監聽 `OnVendingMachinesBuyItems.EVENT`，阻斷 GUNS 及其他武器物）

---

## 元件架構

### 新增檔案
| 檔案 | 說明 |
|------|------|
| `game/roles/killer/hakukofox2/Hakukofox2PlayerComponent.java` | 白狐2.0 CCA 元件（修仙成狐、獸化死亡免疫、瞬結消耗） |
| `game/roles/innocence/halic2/Halic2PlayerComponent.java` | 哈力克2 CCA 元件（量產分身、漏電電網） |
| `mixin/roles/hakukofox2/Hakukofox2AttackBlockMixin.java` | 白狐2 獸化時取消 `Player#attack`（於 mixins 註冊） |

### 修改檔案
| 檔案 | 內容 |
|------|------|
| `role/ModRoles.java` | 註冊 `HALIC2`、`HAKUKO_FOX2`（`NormalRole` 構造：後者為殺手、淡藍白色），並加入 `canSyncedRolePaths` |
| `component/ModComponents.java` | 新增兩組 CCA `ComponentKey`／factory（`RespawnCopyStrategy.NEVER_COPY`） |
| `init/ModRolesInitialEventRegister.java` | 註冊四個 Skill entry（transform/freeze/decoy/electrocute）、開局修仙失明（`ModdedRoleAssigned.EVENT`）、武器購買封鎖 |
| `client/HakukoFoxDisguiseRenderer.java` | 狐狸變身渲染同時判斷 `Hakukofox2PlayerComponent.isDisguised` |
| `mixin/roles/hakukofox/HakukoFoxEyeHeightMixin.java` | 眼睛高度同時判斷白狐2.0 變身 |
| `resources/fabric.mod.json` | CCA 註冊 `noellesroles:halic2`、`noellesroles:hakukofox2` |
| `resources/noellesroles.mixins.json` | 註冊 `roles.hakukofox2.Hakukofox2AttackBlockMixin` |

### 按鍵
- 全部復用既有 **G / Shift+G**（與白狐、哈力克相同的按鍵模式），**不新增**按鍵綁定，且為常數註冊。

---

## 翻譯（三語齊備）

- `assets/noellesroles/lang/*`：
  - `announcement.star.role.hakukofox2` / `.goals`、`announcement.star.role.halic2` / `.goals`
  - `skill.noellesroles.hakukofox2.*`（transform/transform_on/transform_off/freeze/freeze_notify/freeze_self）
  - `skill.noellesroles.halic2.*`（decoy/sanity）
  - `message.noellesroles.hakukofox2.*`（not_enough_money/cultivation_start）
  - `message.noellesroles.halic2.*`（decoy_created/decoy_cooldown/not_enough_money/electrocute_used/electrocuted）
- `assets/role_modifier_intro/lang/`：
  - `info.screen.roleid.hakukofox2` / `.simple`、`info.screen.roleid.halic2` / `.simple`

所有 key 皆與程式碼實際引用一致（已核對無重複、JSON 語法正確）。

---

## 建構與驗證

- `.\gradlew.bat compileJava`：通過（僅既有 deprecated/unchecked 警告）。
- `.\gradlew.bat build`：通過，產出 `build/libs/star_rail_express-4.3.5.jar`（2026-08-02）。
- 未改動模組版本號（版本號由人工手動）。