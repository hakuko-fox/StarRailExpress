# VTuber 職業規範檢查與修正

日期：2026-09-14

依據：`AGENT.md`、`ai_doc.md`、`docs/角色开发指南.md`、`docs/AI创建新职业攻略.md`，以及目前專案的實際 API。

## 範圍與結果

掃描 `src/main/java` 的 VTuber 標籤，涵蓋 `ModRoles` 中全部 27 個職業。原本並非全部符合攻略：職業邏輯散在中央註冊器、物品與共用執行器，還有六個專用 CCA，以及遺漏的道具介紹。

本次將職業實作放到 `org.agmas.noellesroles.role.vtuber`，每人狀態使用 `role_data.vtuber`。`ModRoles` 保留 ID、陣營、標籤及刷新設定；中央技能註冊器只呼叫各職業的註冊方法。20 個統一技能定義分屬 16 個職業，每個職業只註冊一次。佳奈與貓倫保留原有 E 選單操作與伺服器驗證。

保留現有平衡數值，包括風太 200 金幣／120 秒、陌塵成功 120 秒／失敗 5 秒、佳奈選單 15 秒、白御 30 秒，以及白狐解除變身 20 秒、血狐與夜空解除變身 10 秒。佳奈與貓倫選單倒數改用帶 `Level` 參數的遊戲時鐘，依攻略在遊戲時鐘暫停時一起暫停。

## 逐職業紀錄

所有職業均檢查名稱、目標、簡介、詳細介紹的 `zh_cn`、`zh_tw`、`en_us` 鍵，以及共用端不可依賴客戶端類別。

| 職業 ID | 實作類別 | 本次整理或修正 |
| --- | --- | --- |
| halic | HalicRole | 分身與電擊集中；移除無狀態 CCA；離職清除分身；生成失敗不扣錢 |
| hakukofox | HakukoFoxRole | 修仙與變身移至 RoleData；保留解除後冷卻；離職先同步解除外觀 |
| 9muimui | NineMuiRole | 石化狀態與週期改用 RoleData；修正介紹百分比 |
| everly | EverlyRole | 時停技能集中；保留兩個一次性技能及共享回溯；移除無狀態 CCA |
| fu_tai | FuTaiRole | 神諭與護盾商店集中；移除專用 CCA 與重複冷卻來源 |
| hkc_alan | AlinRole | 商店與修門／破門集中；物品薄轉發；門只在伺服器修改 |
| lavanaii | LafinaRole | 衝刺狀態改用 RoleData；離職結束衝刺；保留碰撞與免死事件 |
| zora | HoshizoraRole | 狙擊裝備、商店與武器限制集中；計算已裝上的瞄準鏡；私有計時移入 RoleData |
| nine_one | SeptemberOneRole | 任務進度、受擊與正面免死狀態移入 RoleData；離職移除自身禁言與隱蔽效果 |
| kamikiri_ice | ShenwuBingfengRole | 假武器與陣營免死事件集中；免死組別使用 RoleData；修正介紹百分比 |
| meowlen | MaolunRole | 選人與挑戰集中；兩個目標一起驗證；選單冷卻使用 RoleData |
| yozora | YozoraRole | 貓形態與死亡通知計數使用 RoleData；離職同步解除外觀；修正介紹百分比 |
| amimi | AmiRole | 排斥、食物陷阱入口與路人被動集中；私有狀態使用 RoleData；修正介紹百分比 |
| xiaoye | XiaoyeRole | 武器限制與聯盟被動集中；私有狀態使用 RoleData；修正介紹百分比 |
| xianmiao | XianmiaoRole | 假左輪、聯盟與夜行被動集中；私有狀態使用 RoleData；修正介紹百分比 |
| yuyue | YuyueRole | 便利貼商店與貼背行為集中；物品薄轉發；生成失敗不消耗物品 |
| blood_fox | BloodFoxRole | 變身、理智消耗、免死與進食計時集中；離職同步解除外觀；修正介紹百分比 |
| mochen | MochenRole | 統一技能定義集中；保留回溯成功與失敗的不同冷卻 |
| tinalis | TinalisRole | 吸引技能與夜行被動集中；沿用跨玩家移動服務 |
| luna | LunaRole | 雙人技能集中；回房前驗證雙方房間，失敗不扣錢 |
| yoru | YoruRole | 自身技能註冊與被動集中；共用 Luna 的雙人技能驗證 |
| youjin | YoujinRole | 食物陷阱技能入口與註冊集中；沿用世界陷阱服務 |
| kana | KanaRole | 選人、派對與一次性刀集中；狀態使用 RoleData；保留刀的來源 UUID 驗證 |
| yuzu_fengling | YuzuFenglingRole | 敏捷、睡眠與聯盟被動集中；私有狀態使用 RoleData；商店保留撬棍；修正介紹百分比 |
| juka | JukaRole | 勝利規則與玩具槌效果集中；物品薄轉發；驗證雙方存活且同世界 |
| baiyu | BaiyuRole | 單人標記、技能與裝備集中；標記使用 RoleData，HUD 保留空值處理 |
| ayers | AyersRole | 速度切換與初始裝備集中；切換狀態使用 RoleData |

## 共用服務與保留項目

- `VtuberRoleRuntime` 保留跨玩家的回溯快照、強制移動、食物陷阱、死亡去重與共生處理。這些資料涉及不同職業的目標或世界方塊，不能僅綁在單一施術者的 RoleData 上。
- 貓倫的作答挑戰放在 `MaolunRole`，以受挑戰玩家為索引；阿麟的門使用紀錄以世界、位置及操作種類為索引。兩者於回合邊界清理。
- `VtuberRoleData` 共用每人欄位；只有動物外觀子類與白狐資料需要向其他玩家同步。一般選單、刀授權與白御標記僅同步給本人。伺服器內部計時與集合不寫入同步 NBT。
- 保留 `VtuberRoleItems` 的條件式初始物品轉發，維持既有發放順序、重複武器檢查及神霧的玩家隨機分支。
- 保留必要的白狐模型／眼高／攻擊限制 mixin，改為讀取 RoleData。刪除重置 mixin 中已由核心 RoleData 清理流程接管的白狐清理呼叫。
- 核心刀封包與九月一任務元件呼叫的舊入口保留為薄轉發；沒有修改 `io/wifi/starrailexpress/`。
- 沿用回溯快照與既有實體註冊，沒有擴大回溯內容或改動舊存檔實體相容策略。沒有新增依賴、按鍵、版本號或提交。

## 資源與失敗處理

- 移除六個舊 CCA 註冊及其 manifest 項目；清除所有原始碼舊類名引用。
- 金幣讀寫透過 `MoneyUtils`；既有消費提示與價格保留。
- 8 個職業的三語介紹修正 `%` 為 `%%`。
- 阿麟板手、阿麟螺絲刀、羽月便利貼、玩具槌新增三語 `.desc`；Luna／Yoru 新增三語缺少房間提示。
- 伺服器選單維持封鎖技能、存活、目標數量、自己／其他玩家、世界及冷卻檢查；變身與事件入口補上 RoleData 空值處理。

## 驗證

- `scripts/verify_vtuber_authoring.py`：27 個職業、單次技能註冊、三語資源、CCA 殘留與共用端依賴檢查通過。
- `scripts/verify_vtuber_known_issues.ps1`：改為檢查新的職業實作位置，保留原有行為期待，通過。
- `git diff --check` 通過；核心程式目錄沒有差異。
- 完整 Gradle 建置、169 項測試、`verifyMixinClasses`、資源處理與 JAR 打包通過。最後補上的九月一離職清理已再次通過完整建置與測試。
- 建置出現非致命的 `ModelBakery.addExtraModel` remap 提示及既有 Gradle/API 棄用提示；不能據此宣稱已完成遊戲內模型驗收。

尚未執行 Minecraft 多人實機驗收。須驗證他人視角下切換／解除變身、重連與回合重置、E 選單暫停倒數、佳奈刀授權、貓倫雙人作答、房間傳送、狙擊鏡限制、門工具與便利貼實際互動。靜態腳本及通用單元測試不等於這些遊戲路徑已實測。
