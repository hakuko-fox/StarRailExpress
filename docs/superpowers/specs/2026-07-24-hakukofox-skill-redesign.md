# HakukoFox 技能重製設計

## 概述
將 HakukoFox（白狐）的技能全面重製為獸化型態 + 狐狸分身 + 被動九命。

---

## 技能 1（G）— 獸化型態
- 按 G 變身雪狐，獲得**速度 II、跳躍 II**，**無限持續時間**
- 獸化時**無法攻擊**（攔截攻擊動作）
- 再按 G 回到人類型態，進入 **180 秒冷卻**
- **被動 — 狐有九命**：獸化型態下免疫一次致命傷害（消耗後消失）

## 技能 2（Shift+G）— 狐狸分身（冷卻 90 秒）

| 狀態 | 操作 | 結果 |
|------|------|------|
| 無分身 | Shift+G（-50 金） | 生成 AI 狐狸分身 |
| 分身存在 | Shift+G | 視角切到分身，原身體停在原地，分身繼續 AI 自由行動 |
| 分身視角 | Shift+G | **接管分身**，原身體消失 |
| 分身視角 | ESC | 退回原身體，分身繼續 AI |
| 已接管 | Shift+G | 分身變回原身體並消失 |

---

## 元件架構

### 1. `HakukoFoxPlayerComponent.java`
- 狀態機列舉：`HUMAN`、`BEAST_FORM`、`CLONE_EXISTS`（分身存在但人在本體）、`CLONE_POV`（視角在分身）、`CLONE_POSSESSED`（接管分身）
- 欄位：`foxFormActive`、`cloneEntityId`、`tickCount`
- 方法：`transformFox()`、`revertToHuman()`、`spawnClone()`、`enterClonePOV()`、`possessClone()`、`returnToBody()`、`serverTick()`
- 被動九命：使用 `wasInBeastForm` / `nineLivesUsed` 標記，在 `serverTick()` 檢測死亡事件

### 2. 分身管理
- 分身需以**伺服器端實體**存在，使用原版 `Fox` 實體 + `SNOW` 變種
- 透過 `ServerPlayer#setCamera()` 切換視角（Spectate 機制）
- 接管分身：`ServerPlayer#setCamera(null)` + 將 `Fox` 實體移除，在原位置生成玩家
- 原身體消失時：設為 `isInvisible(true)` + `noClip = true` + 傳送至虛空並設定 `setCamera`，防止被攻擊

### 3. 攻擊攔截（Mixin）
- Mixin `LivingEntity#hurt` 或 `Player#attack`：若玩家處於獸化型態，return false

### 4. 免疫死亡（Mixin）
- Mixin `LivingEntity#hurt` / `ServerPlayer#die`：若處於獸化型態且未消耗九命，設傷害為 0 / 阻止死亡

### 5. `HakukoFoxDisguiseRenderer.java`
- 保留既有狐狸渲染邏輯，可能需配合新狀態機調整

### 6. `ModRolesInitialEventRegister.java`
- 更新技能註冊，分為兩個獨立 skill entry

---

## 資料流
```
玩家按 G
  → ModRolesInitialEventRegister 偵測到 keybinding
  → HakukoFoxPlayerComponent.transformFox()
    → 設定 foxFormActive = true
    → 套用 Speed II, Jump II
    → sync() 同步至 client
    → Client: HakukoFoxDisguiseRenderer 開始渲染為狐狸

玩家按 Shift+G
  → if (無分身) → spawnClone()：生成 Fox 實體，扣 50 金
  → if (分身存在) → enterClonePOV()：setCamera(clone)
  → if (分身視角) → possessClone()：移除此地玩家，Fox 變玩家
  → if (分身視角, ESC) → returnToBody()：setCamera(original)
  → if (已接管) → 分身變回原身體，分身消失
```

---

## 冷卻管理
- 獸化型態：以獨立 cooldown 跟蹤，180 秒，從 `revertToHuman()` 開始計算
- 狐狸分身：90 秒冷卻，從生成分身 / 使用 Shift+G 開始計算

---

## 翻譯更新
需更新 `en_us.json`、`zh_tw.json`、`zh_cn.json`：
- `skill.noellesroles.hakukofox.transform` → 獸化型態說明
- `skill.noellesroles.hakukofox.foxfire` → 改為狐狸分身說明
- 移除舊的 foxfire 相關訊息
- 新增 `skill.noellesroles.hakukofox.clone`、`skill.noellesroles.hakukofox.possess` 等

---

## NPC 分身
- 使用原版 `Fox` 實體（`EntityType.FOX`），設定 `Variant = SNOW`
- 生成位置為玩家腳下
- 分身為 AI 自由行動（不設定任何目標）
- 隱藏名字標籤
- 當玩家退回原身體 / 接管後消失 → `Fox.discard()`

## 附身機制（玩家往返分身）
Minecraft 只有一個 Player 實體，故採用以下方式：

| 操作 | 實作 |
|------|------|
| 生成分身 | `Fox` 實體生成於玩家位置，設為 AI 自由行動 |
| 進入分身視角 | `ServerPlayer#setCamera(fox)`，玩家原身體保留在原地（可被攻擊），分身 AI 繼續移動 |
| 接管分身 | 儲存原位置 → 傳送玩家到狐狸位置 → `fox.discard()` → 玩家變回人類外觀在該處 |
| 退回原身體（分身視角→ESC） | `setCamera(original)`，分身繼續 AI 行動 |
| 退回原身體（已接管→Shift+G） | 傳送玩家回原位置，原位置即為人類身體 |

接管時玩家**持有物、血量、狀態效果**維持不變，因為始終是同一個 Player 實體。

## 獸化禁止攻擊
- 在 `HakukoFoxPlayerComponent` 中維持 `foxFormActive` 標記
- Mixin `Player#attack`：若 `foxFormActive == true` → return（cancel 攻擊動作）
- Mixin `Player#hurt`（九命）：若 `foxFormActive == true` 且 `!nineLivesUsed` → set `nineLivesUsed = true`，將傷害設為 0 或回復血量

## 技能冷卻
- 獸化型態冷卻使用既有 cooldown 系統（180 秒），從 `revertToHuman()` 開始計算
- 狐狸分身不設冷卻，僅金幣限制
