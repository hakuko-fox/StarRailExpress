# VTuber 角色規格核查（2026-09-07）

## 範圍與版本

- 規格：`C:\Users\KEN\Git\Vtuber角色` 的27份角色文件、`0 已知問題.txt` 32項，以及四份陣營補充清單30項，合共62項。
- 主模組：先拉取 `origin/char` 的 `c46de319c`，再與本機 `2b37b8dbb` 合併為 `12d04e995`。兩邊提交均保留，未推送。
- 背景直接抄錄：txt「簡介」→ `role_stories` 的 `star.story.role.*`；txt「介紹」→ `role_modifier_intro` 的 `.simple`。只移除段落邊界空白，保留內文及換行。神霧冰封首行陣營經使用者確認更正為中立。
- 技能詳細說明按操作、金幣、冷卻／次數、效果與限制重新分段，並同步角色目標與相關技能名稱。27個角色沿用原有三個 locale 共用中文介紹的安排；背景不另行翻譯。

## 本次補修

1. 白御原先未實作夜行性動物；補入失明免疫判斷。
2. 佳奈選人封包原先可略過「魔／精靈」的技能封鎖；在選單入口統一檢查，貓倫保留相同行為及既有 SAFE_TIME 限制。
3. 風太伺服器已有120秒冷卻，但統一技能 HUD 未倒數；補上120秒冷卻註冊。
4. 補齊27份背景故事、直接同步原文介紹、改寫玩法段落，修正白御過時的查死因目標、技能名稱及指定的貓倫／夜空／星空宙提示文字；清除本次涉及的重複貓倫訊息鍵。

## 證據界線

下表「程式已對齊」表示已閱讀實作與實際呼叫／資源路徑，數值及條件有程式證據；不表示已做 Minecraft 多人實測。未取得遊戲執行日誌或可操作的測試伺服器，沒有宣稱語音、動畫、碰撞、即時封包同步或完整勝利畫面已驗收。

菈菲娜的擊退措辭經使用者確認為「大約3格，保留現有推力」，因此保留既有速度脈衝，詳細說明使用「約3格」。沒有宣稱精確位移或硬性上限。下表標示「待實機」的項目有程式路徑，但須依下方步驟驗證。


## 0 已知問題.txt（32項）

| 角色／原項目 | 結果 | 核查內容 | 證據 |
| --- | --- | --- | --- |
| 織夢玖璃：1) 檢查石化期間是不是無法說話 | 程式已對齊 | 石化10秒同時套用 VOICE_SILENCE、CHAT_BAN；語音插件及文字聊天事件均有攔截。語音實效需連線驗證。 | [ModEffects.VOICE_SILENCE](../src/main/java/org/agmas/noellesroles/game/roles/killer/nine_mui/NineMuiPlayerComponent.java#L113) |
| 阿米米：1) 被動技〖過客〗數值修改 | 程式已對齊 | 3格內持續有人10秒後，每秒扣0.01理智；離開範圍後重設計時。 | [private static void tickPasserby](../src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java#L760) |
| 哈力克：1) 被動技〖小透明〗已刪除 | 程式已對齊 | HALIC 沒有小透明／隱藏直覺的角色旗標；只保留不能購買武器及無視理智。技能說明亦不再列小透明。 | [public static SRERole HALIC](../src/main/java/org/agmas/noellesroles/role/ModRoles.java#L1132) |
| 菈菲娜：1) 主動技〖巨熊衝擊〗數值修改 | 程式已對齊 | 150金幣、70秒冷卻、速度III、固定方向、撞牆停止、衝刺免死、命中眩暈3秒。使用者確認擊退為大約3格、保留現有推力，沒有要求硬性上限。 | [private static void tickLafinaCharge](../src/main/java/org/agmas/noellesroles/role/ModRoles.java#L162) |
| 嵐狐風太：1) 主動技〖占星探究〗數值修改 | 程式已對齊 | 200金幣、120秒、分開計算存活殺手及中立人數。本次補上 HUD 的120秒冷卻，統一技能名稱為占星探究。 | [public boolean useOracleSkill](../src/main/java/org/agmas/noellesroles/game/roles/innocence/futai/FuTaiPlayerComponent.java#L58) |
| Luna Yoru：1) 主動技 〖召喚夥伴〗所需金幣修改 | 程式已對齊 | 召喚夥伴扣200金幣；每位角色每局1次，3格內外分別送回出生點／召喚身邊。 | [public static boolean usePairSkill](../src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java#L177) |
| 羽月：1) 便利貼價格修改 | 程式已對齊 | YUYUE_NOTE 售價25金幣。 | [YUYUE_NOTE.getDefaultInstance(), 25](../src/main/java/org/agmas/noellesroles/init/RoleShopHandler.java#L3711) |
| 血狐：1) 主動技〖血化之力〗數值修改 | 程式已對齊 | 血狐速度II、每秒扣0.5%理智、低於50%自動解除；變身期間不計冷卻，解除後才計10秒並緩速1秒。 | [private static void tickBloodFox](../src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java#L558) |
| 鴻御陌塵：1) 主動技〖時間倒流.強〗數值修改 | 程式已對齊 | 200金幣，倒退3秒，冷卻120秒；透過復活流程恢復期間死者。 | [RoleSkill.register(ModRoles.MOCHEN](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L1746) |
| 幽燼 冰封：1) 被動技〖精靈〗數值修改 | 程式已對齊 | 死亡後速度III及禁技能均為5秒。本次把佳奈的選單封包納入技能封鎖；貓倫沿用相同共用檢查。 | [boolean spirit =](../src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java#L832) |
| 艾爾斯 小夜 提娜莉斯：1) 被動技〖魔〗數值修改 | 程式已對齊 | 小夜、提娜莉斯、艾爾斯死亡後禁技能3秒；G鍵及角色選單入口均有封鎖檢查。 | [boolean demon =](../src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java#L829) |
| 十七夜佳奈：1) 開局沒有武器 | 程式已對齊 | 佳奈沒有初始武器配置；SRERole 預設物品為空，派對獎勵另由 runtime 發放。 | [public List<ItemStack> getDefaultItems](../src/main/java/io/wifi/starrailexpress/api/SRERole.java#L1203) |
| 十七夜佳奈：2) 不能購買武器 | 程式已對齊 | 購買事件拒絕佳奈的 WEAPON 商品，另由角色武器鉤子及小刀授權檢查拒絕外來武器。 | [|| gameWorldComponent.isRole(player, ModRoles.KANA)](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L1715) |
| 十七夜佳奈：3) 〖冷卻時間〗已下調到15秒 | 程式已對齊 | 伺服器冷卻15秒，同步結束時間供客戶端倒數。 | [KANA_MENU_COOLDOWN.put(caster.getUUID(), now + 20L * 15L)](../src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java#L400) |
| 十七夜佳奈：4) 選單以玩家頭像跟名稱顯示 | 程式已對齊 | E鍵導向選人畫面；按鈕繪製玩家頭像及名稱。實際布局需客戶端確認。 | [PlayerFaceRenderer.draw](../src/main/java/org/agmas/noellesroles/client/screen/VtuberPlayerSelectScreen.java#L110) |
| 十七夜佳奈：5) 冷卻過程中無法點選玩家頭像，頭像會有倒時器 | 程式已對齊 | 冷卻時停用頭像與確認按鈕，顯示秒數；送出及伺服器兩端都有冷卻檢查。 | [button.active = ready](../src/main/java/org/agmas/noellesroles/client/screen/VtuberPlayerSelectScreen.java#L35) |
| 十七夜佳奈：6) 因達成條件所獲得的小刀更改為一次性小刀 | 程式已對齊 | 派對小刀帶有當次授權標記；有效命中時消耗1把，結束派對並清除詛咒。護盾／分身互動仍需實機測試。 | [consumeKanaKnife(player)](../src/main/java/io/wifi/starrailexpress/network/original/KnifeStabPayload.java#L84) |
| 十七夜佳奈：7) 只能靠達成特定條件才能獲得一次性小刀 | 程式已對齊 | 只有達成派對條件才建立刀具授權；攻擊時比對授權，拒絕普通刀或其他角色撿到的派對刀。 | [public static boolean canUseKanaKnife](../src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java#L129) |
| 白狐：1) 主動技1〖獸化之力〗數值修改 | 程式已對齊 | 白狐解除變身後才計20秒冷卻；啟用期間沒有冷卻，解除緩速1秒。修仙首60秒例外失明保留。 | [context.setSkillCooldown(20 * 20)](../src/main/java/org/agmas/noellesroles/game/roles/killer/hakukofox/HakukoFoxPlayerComponent.java#L154) |
| 啾卡：1) 被動技〖這裡只剩我跟你〗描述修改 | 程式已對齊 | 詳細說明已明確寫為平民及警察全滅、自己仍生存，不要求只剩兩人；勝利判斷使用相同條件。 | [boolean civilianOrPoliceAlive](../src/main/java/org/agmas/noellesroles/role/ModRoles.java#L1667) |
| 貓倫：1) 選單以玩家頭像跟名稱顯示 | 程式已對齊 | E鍵打開雙人選擇畫面；與佳奈共用頭像及名稱按鈕。 | [new VtuberPlayerSelectScreen(2, false)](../src/main/java/org/agmas/noellesroles/client/NoellesrolesClient.java#L1265) |
| 貓倫：2) 〖冷卻時間〗已下調到70秒 | 程式已對齊 | 成功建立兩名目標挑戰後才啟動70秒冷卻。 | [MEOWLEN_MENU_COOLDOWN.put(caster.getUUID(), now + 20L * 70L)](../src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java#L460) |
| 貓倫：3) 回答時限已下調到60秒 | 程式已對齊 | 伺服器挑戰期限及客戶端題目畫面均為60秒。 | [new org.agmas.noellesroles.packet.ProblemScreenOpenC2SPacket(true, 2, 60, true)](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L629) |
| 貓倫：4) 可答錯次數已下調到2次 | 程式已對齊 | 最大答錯次數2；首次答錯重設連續答對進度，第二次直接判失敗。 | [} else if (consecutive)](../src/main/java/org/agmas/noellesroles/client/screen/MathSolverScreen.java#L399) |
| 貓倫：5) 如果因答錯次數達2次而獲得1次失敗次數後 該次作答為視作完成 不需要繼續作答 | 程式已對齊 | 第二次答錯立即送出一次失敗封包並關閉畫面；伺服器清除控制效果及挑戰，累計兩次測驗失敗才殺死目標。 | [private void failConsecutiveImmediately](../src/main/java/org/agmas/noellesroles/client/screen/MathSolverScreen.java#L150) |
| 夜空：1) 主動技〖貓之第六感〗數值修改 | 程式已對齊 | 白貓速度III、解除後10秒冷卻與1秒緩速；仍可被殺；第9次死亡提示會自殺。 | [int notices = YOZORA_DEATH_NOTICES.merge](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L157) |
| 夜空：2) 被動技〖過客〗數值修改 | 程式已對齊 | 與阿米共用3格、10秒後每秒扣1%理智規則。 | [private static void tickPasserby](../src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java#L760) |
| 綺芙妮：1) 主動技1〖時間停止〗數值修改 | 程式已對齊 | 成功時停才扣150金幣，持續5秒；獨立1次充能。 | [TimeStopEffect.tryTriggerStart(sp, 5 * 20](../src/main/java/org/agmas/noellesroles/game/roles/vigilante/everly/EverlyPlayerComponent.java#L72) |
| 綺芙妮：2) 主動技2〖時間倒流〗數值修改 | 程式已對齊 | 倒退5秒、150金幣；Shift+G獨立1次充能，與時停次數分開。 | [.useRewind(context.player(), 5, 150)](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L1743) |
| 白御：1) 主動技〖記錄二三事〗描述修改 | 程式已對齊 | 詳細說明及開局目標都改為標記存活玩家與死亡提示；本次另補上原先遺漏的夜行性失明免疫。 | ["info.screen.roleid.baiyu"](../src/main/resources/assets/role_modifier_intro/lang/zh_tw.json#L544) |
| 白御：2) 只能同時標記一名玩家 當標記新玩家後 舊玩家標記會失效 | 程式已對齊 | Map 以施術者UUID為鍵，標記新玩家時覆蓋舊值；死亡通知後移除。 | [BAIYU_MARKED_TARGETS.put](../src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java#L305) |
| 白御：3) 右下角技能會顯示當前標記的玩家 | 程式已對齊 | 同步目前標記名稱；HUD 讀取並顯示，目標死亡時清除。 | [getMarkedTargetName()](../src/main/java/org/agmas/noellesroles/client/hud/UnifiedSkillHud.java#L174) |

## 1 平民.txt（6項）

| 角色／原項目 | 結果 | 核查內容 | 證據 |
| --- | --- | --- | --- |
| 血狐：1) 轉獸化型態 有冇得轉skin？ | 程式已對齊 | 血狐變身走紅狐模型，白狐走雪狐模型；不是更換玩家皮膚。實機外觀需確認。 | [fox.setVariant(red ? Fox.Type.RED : Fox.Type.SNOW)](../src/main/java/org/agmas/noellesroles/client/HakukoFoxDisguiseRenderer.java#L71) |
| 哈力克：1) 分身實體 | 程式已對齊 | 分身使用 PuppeteerBodyEntity，設為永久存續並能碰撞，被攻擊後移除。 | [decoy.setHalicDecoy(true)](../src/main/java/org/agmas/noellesroles/game/roles/innocence/halic/HalicPlayerComponent.java#L107) |
| 哈力克：2) 指向分身時都會顯示名字/指向所有玩家時不會顯示名字 | 顯名路徑已核查；待實機 | 指向哈力克分身會顯示擁有者名字；一般玩家仍走原有名稱渲染分支。是否重現原先「所有玩家不顯名」需在客戶端確認。 | [if (!pbe.isHalicDecoy() &&](../src/main/java/io/wifi/starrailexpress/client/gui/RoleNameRenderer.java#L331) |
| 風太：1) Confirm 紅石掉落物唔會生成喺出生點 | 新版已取消 | 個別角色新版只保留占星探究與夜行性動物，沒有紅石掉落物；目前亦沒有生成流程。 | [public boolean useOracleSkill](../src/main/java/org/agmas/noellesroles/game/roles/innocence/futai/FuTaiPlayerComponent.java#L58) |
| 風太：2) 冇高亮顯示 | 新版已取消 | 新版規格沒有紅石或漏洞高亮被動；不恢復舊版高亮。 | ["info.screen.roleid.fu_tai"](../src/main/resources/assets/role_modifier_intro/lang/zh_tw.json#L516) |
| 菈菲娜：1) 視覺上身型要變大 但確保可穿過門 | 程式已對齊；待實機 | 角色渲染放大1.5倍，沒有改碰撞體積；需實機確認各種門及第一／第三人稱。 | [poseStack.scale(1.5F](../src/main/java/org/agmas/noellesroles/mixin/client/roles/lafina/LafinaPlayerRenderMixin.java#L28) |

## 2 殺手.txt（10項）

| 角色／原項目 | 結果 | 核查內容 | 證據 |
| --- | --- | --- | --- |
| 白狐：1) 頭60秒有失明，60秒後先提供失明 | 依個別角色新版對齊 | 按個別角色規格處理：開局失明60秒，之後自動獸化且免失明。補充清單末句「提供失明」視為筆誤，未改成永久失明。 | [.get(player).isCultivating()](../src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java#L794) |
| 白狐：2) 主動技2 失明效果為正設 | 程式已對齊 | 瞬結對所有其他玩家施加3秒失明及緩速；有夜行性動物的角色仍會免疫失明。 | [public boolean useFreezeSkill](../src/main/java/org/agmas/noellesroles/game/roles/killer/hakukofox/HakukoFoxPlayerComponent.java#L169) |
| 白狐：3) 預設有刀 | 程式已對齊 | 白狐角色初始化明確發放小刀。 | [if (role.identifier().equals(ModRoles.HAKUKO_FOX.identifier()))](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L355) |
| 星空宙：1) 其他玩家被狙擊時 會收到"你已經被狙擊一次 再次受傷你就會死亡" | 程式已對齊 | 已照補充清單寫入指定提示文字；第一次狙擊命中有通知及速度II。 | ["message.noellesroles.zora.first_hit"](../src/main/resources/assets/noellesroles/lang/zh_tw.json#L4291) |
| 星空宙：2) 星空宙狙擊失敗時 會收到"你沒有擊中任何人 你還有X次機會" | 程式已對齊 | 已照補充清單寫入指定文字與剩餘次數參數；第5次落空自殺。 | ["message.noellesroles.zora.miss_remaining"](../src/main/resources/assets/noellesroles/lang/zh_tw.json#L4352) |
| 星空宙：3) 星空宙不可以出現別墅之類回型地圖，以免槍無法使用 | 程式限制已存在；地圖待實測 | 角色有 BIGMAP 隨機出現限制。手動強制選角仍需服主避免不適用地圖，逐張地圖標記未實測。 | [.setSpecialMapRole(SRERole.SpecialMapRoleMap.BIGMAP)](../src/main/java/org/agmas/noellesroles/role/ModRoles.java#L1284) |
| 星空宙：4) 槍為不可見物品 | 程式已對齊；待實機 | 手持顯示事件對其他玩家隱藏星空宙狙擊槍，持有者仍可看見。 | [Hoshizora's sniper rifle is invisible](../src/main/java/org/agmas/noellesroles/client/InvisbleHandItem.java#L44) |
| 星空宙：5) 槍冷卻時間為20秒 | 程式已對齊 | 星空宙射擊路徑使用20×20 ticks。 | [? 20 * 20](../src/main/java/io/wifi/starrailexpress/network/original/SniperShootPayload.java#L127) |
| 柚封凌：1) 只要有上床動作 被動技〖超長睡眠〗時間就會重設 | 程式已對齊 | 躺床時重設90秒期限，並移除睡眠禁武狀態；不要求睡完整段時間。 | [private static void tickYuzuSleep](../src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java#L722) |
| 柚封凌：2) 預設有刀 | 程式已對齊 | 柚封凌初始化明確發放小刀。 | [if (role.identifier().equals(ModRoles.YUZU_FENGLING.identifier()))](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L386) |

## 3 中立.txt（10項）

| 角色／原項目 | 結果 | 核查內容 | 證據 |
| --- | --- | --- | --- |
| 九月一：1) 確保一次擋死為正面攻擊 | 程式已對齊 | 攻擊方向與面向做水平內積，只在正面且尚未使用護盾時抵擋。 | [if (facing.dot(toAttacker) <= 0.0D)](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L205) |
| 九月一：2) 被攻擊後不能說話 | 程式已對齊 | 受攻擊時加入本局永久 VOICE_SILENCE 及 CHAT_BAN；傷害和直接擊殺路徑均呼叫紀錄。 | [public static void recordShenwuDamage](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L578) |
| 九月一：3) 唔會掉SAN | 程式已對齊 | 角色 MoodType.NONE，另清除混亂效果。 | [public static SRERole SEPTEMBER_ONE](../src/main/java/org/agmas/noellesroles/role/ModRoles.java#L1524) |
| 神霧冰封：1) 70% 同 30% 非獨立計算 所以必定獲得其中一個 | 程式已對齊 | 只做一次 nextFloat < 0.70，true派假槍，false派假刀。 | [boolean sheriffVariant =](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L401) |
| 神霧冰封：2) 擋死睇下有冇得計陣營 冇我再轉 | 程式已對齊 | 依 CIVILIAN、SHERIFF、KILLER 三組分別記錄；每組第一下致命攻擊可抵擋。 | [private enum ShenwuDamageGroup](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L133) |
| 神霧冰封：3) 條件達成下 優先結算為冰封獲勝 | 程式已對齊 | 三組齊全且仍有非殺手玩家時，再受致命攻擊直接寫入自訂勝者並停局。 | [groups.size() >= ShenwuDamageGroup.values().length](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L177) |
| 貓倫：1) 大致同小鎮做題家一樣 | 程式已對齊 | 使用 MathSolverScreen 強制作答模式：連對5題、60秒、2次錯誤、兩次測驗失敗死亡。 | [public static boolean startMaolunChallenge](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L616) |
| 貓倫：2) 成功答題 會收到"你合格了 準備下次測驗吧" | 本次文字修正 | 已照原文寫入「你合格了 準備下次測驗吧」。 | ["message.noellesroles.meowlen.challenge_succeeded"](../src/main/resources/assets/noellesroles/lang/zh_tw.json#L4355) |
| 貓倫：3) 失敗答題 會收到"你不合格！你還尚餘1次機會補測！" | 本次文字修正 | 已照原文寫入不合格與補測剩餘次數；使用單一%s，移除同鍵舊版雙參數文字。 | ["message.noellesroles.meowlen.challenge_failed"](../src/main/resources/assets/noellesroles/lang/zh_tw.json#L4354) |
| 啾卡：1) 條件達成下 優先結算為啾卡獲勝 | 程式已對齊；待實機 | 自訂勝者檢查會處理 Juka 的 CUSTOM 並立即呼叫 win；需要實機測試末位平民／警察死亡的結算畫面。 | [WinStatus resultWinStatus = cwr.checkWin](../src/main/java/org/agmas/noellesroles/CustomWinnerClass.java#L69) |

## 4 警察.txt（4項）

| 角色／原項目 | 結果 | 核查內容 | 證據 |
| --- | --- | --- | --- |
| 夜空：1) 提示音效你決定 | 程式已對齊；待實機 | 夜空死亡感應使用 NOTE_BLOCK_PLING，音高1.2。 | [observer.playNotifySound](../src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java#L158) |
| 夜空：2) 有人死 會收到 "你感覺到有人死亡 看來你還可以見證X次" | 本次文字修正 | 已照原文寫入「你感覺到有人死亡 看來你還可以見證%s次」。 | ["message.noellesroles.yozora.death_notice"](../src/main/resources/assets/noellesroles/lang/zh_tw.json#L4290) |
| 夜空：3) 變貓可被殺 | 程式已對齊 | 動物免死判斷只覆蓋血狐，白貓不在免死分支。 | [private static boolean allowAnimalFormDeath](../src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java#L588) |
| 綺芙妮：1) 經技能2〖時間倒流〗所復活的玩家是可以說話 | 程式已對齊；待實機 | 倒流復活使用 revivePlayerToItsRoom，會重設 TrainVoicePlugin；再恢復生前狀態。語音插件連線需實機確認；生前已有禁言者仍保留該限制。 | [public static void revivePlayerToItsRoom](../src/main/java/io/wifi/starrailexpress/game/GameUtils.java#L1568) |

## 實機驗收步驟

1. 白狐／血狐／夜空：進入形態後等待超過原冷卻時間，解除才應出現20／10／10秒倒數；倒數期間反覆按G不得再次變身。血狐理智降至50%以下自動解除亦應重新計10秒。白狐首60秒失明與自動變身另驗。
2. 佳奈：確認開局無武器；未達派對條件無法攻擊；派對刀命中後消耗，不能借刀或撿刀繞過。冷卻期間關閉／重開E畫面仍顯示倒數及停用頭像；魔／精靈死亡後3／5秒內不可選人施技。
3. 貓倫：兩名目標各答錯兩次後立即關閉畫面、恢復移動且只記一次失敗；第二次測驗失敗死亡。另測60秒逾時、連對5題及逾時封包晚到，避免重複結算。
4. 白御：先標記A再標記B，HUD只顯示B；A死亡不提示、B死亡提示並清除。對白御施加失明應移除。
5. 玖璃：石化時語音及文字聊天都被阻擋，10秒後恢復；九月一前／後方攻擊的護盾、永久禁言及40秒任務週期另驗。
6. 菈菲娜：平地、冰面、牆角、門框下確認擊退手感及1.5倍外觀仍可通門；按使用者決定保留現有推力，距離約3格。
7. 夜空／血狐／哈力克：確認模型、第一／第三人稱、分身碰撞與準星名稱；夜空第9次感應死亡不被免死攔截。
8. 陌塵／綺芙妮：期間死亡者復活後恢復語音；檢查已持有禁言效果的生前狀態仍按規則保留。
9. 冰封／啾卡：以完整多人回合測試陣營來源、末位平民／警察死亡與自訂勝利優先結算。星空宙另測適用地圖標記、槍隱藏及第一次命中／落空提示。

## 本機驗證

- `scripts/verify_vtuber_known_issues.ps1`：靜態回歸檢查，不等同62項全程實機測試。
- Java 21 `compileJava` 與 `SkillCooldownAccountingTest`：最後修改後再次通過，3項測試、0失敗、0錯誤。
- 背景原文、角色key及變動範圍：以解析後 JSON 對照27份來源，檢查非本次角色鍵沒有改動。
- `git diff --check`：通過。沒有修改提供的txt原檔，也沒有加入其他未追蹤檔案或推送遠端。
- OpenCode（Z.AI）指定資料唯讀覆核：接受並清除共用選單檢查後的重複判斷；其「SAFE_TIME等同時停」及「玖璃等同柚封凌」判斷不符原碼，沒有採納。玖璃文字禁言已有 CHAT_BAN 實作。
