$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$failures = [System.Collections.Generic.List[string]]::new()

function Read-Utf8([string] $relativePath) {
    return Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $root $relativePath)
}

function Assert-Contains([string] $text, [string] $expected, [string] $message) {
    if (-not $text.Contains($expected)) {
        $failures.Add($message)
    }
}

function Assert-NotContains([string] $text, [string] $unexpected, [string] $message) {
    if ($text.Contains($unexpected)) {
        $failures.Add($message)
    }
}

function Assert-Matches([string] $text, [string] $pattern, [string] $message) {
    if ($text -notmatch $pattern) {
        $failures.Add($message)
    }
}

$fuTai = Read-Utf8 'src/main/java/org/agmas/noellesroles/game/roles/innocence/futai/FuTaiPlayerComponent.java'
Assert-NotContains $fuTai 'spawnRoundGlitches' '風太的新版本仍會生成舊版異常紅石。'
Assert-NotContains $fuTai 'collectedGlitches' '風太的新版本仍保留異常紅石收集與神諭升級狀態。'
Assert-Contains $fuTai 'int cost = 200;' '風太神諭應固定消耗 200 金幣。'
Assert-Contains $fuTai 'nextOracleTick = now + 20L * 150L;' '風太神諭應固定冷卻 150 秒。'

$client = Read-Utf8 'src/main/java/org/agmas/noellesroles/client/NoellesrolesClient.java'
Assert-Matches $client '(?s)isRole\(client\.player, ModRoles\.KANA\).*?new VtuberPlayerSelectScreen\(1, false\)' '佳奈按 E 後未開啟排除自己的單人選擇選單。'

$knifePayload = Read-Utf8 'src/main/java/io/wifi/starrailexpress/network/original/KnifeStabPayload.java'
Assert-Contains $knifePayload '!role.onUseKnife(player)' '刀擊封包缺少伺服器端 onUseKnife 驗證，柚封凌的禁用狀態可被繞過。'

$roleEvents = Read-Utf8 'src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java'
Assert-Contains $roleEvents 'NINE_ONE_ATTACKED' '九月一缺少「受攻擊後」狀態，40 秒任務週期會在受攻擊前啟動。'
Assert-Contains $roleEvents 'ModEffects.CHAT_BAN' '九月一受攻擊後只禁語音，未禁止文字聊天。'
Assert-Contains $roleEvents 'context.setSkillCooldown(5 * 20)' '陌塵技能失敗後未套用 5 秒冷卻。'
Assert-Matches $roleEvents '(?s)RoleSkill\.register\(ModRoles\.BAIYU,.*?\.cooldownSeconds\(120\)\.showOnHud\(true\)\.build\(\)\);' '白御「記錄二三事」冷卻未更新為 120 秒。'

$taskComponent = Read-Utf8 'src/main/java/io/wifi/starrailexpress/cca/SREPlayerTaskComponent.java'
Assert-Contains $taskComponent 'isNineOneTaskCycleActive()' '九月一任務刷新與 40 秒有效期未限定在受攻擊後。'
Assert-Contains $taskComponent 'this.nextTaskTimer = 0;' '九月一任務過期後未立即排入下一個 40 秒週期。'

$runtime = Read-Utf8 'src/main/java/org/agmas/noellesroles/game/roles/vtuber/VtuberRoleRuntime.java'
Assert-Contains $runtime 'KANA_MENU_COOLDOWN.put(caster.getUUID(), now + 20L * 30L);' '佳奈拖放技能冷卻不是 30 秒。'
Assert-Matches $runtime '(?s)selectKanaTarget\(ServerPlayer caster, ServerPlayer target.*?target == caster.*?KANA_MENU_COOLDOWN\.put\(caster\.getUUID\(\), now \+ 20L \* 30L\)' '佳奈選擇技能未在伺服器排除自己，或成功選擇後未套用 30 秒冷卻。'
Assert-Matches $runtime '(?s)long cooldownUntil = KANA_MENU_COOLDOWN\.getOrDefault.*?if \(now < cooldownUntil\).*?return;.*?target\.removeEffect' '佳奈冷卻期間仍可把效果套用到其他玩家。'
Assert-Matches $runtime '(?s)tickNocturnalAndStableSan\(ServerPlayer player.*?game\.isRole\(player, ModRoles\.FU_TAI\).*?player\.removeEffect\(MobEffects\.BLINDNESS\)' '風太被動「夜行性動物」未移除失明。'
Assert-NotContains $runtime 'BAIYU_EXAMINATIONS' '白御仍使用延遲 10 秒且要求站定的舊版檢查流程。'
Assert-Contains $runtime 'displayBaiyuDeathReason(player, body);' '白御未在對屍體使用技能後立即顯示死因。'

$shop = Read-Utf8 'src/main/java/org/agmas/noellesroles/init/RoleShopHandler.java'
foreach ($roleId in @('YUZU_FENGLING_ID', 'HAKUKO_FOX_ID', 'KANA_ID')) {
    Assert-Contains $shop "registerDefaultKillerShopWithCrowbar(ModRoles.$roleId);" "$roleId 未明確註冊包含撬棍的角色商店。"
}
Assert-Contains $shop 'HOSHIZORA_SHOP.add(maxOneScopeEntry(25));' '星空宙商店缺少上限 1 的狙擊鏡。'
Assert-Contains $shop 'private static ShopEntry maxOneScopeEntry(int price)' '星空宙商店未檢查狙擊槍內置瞄準鏡，可能重複購買瞄準鏡。'
Assert-Contains $shop 'SniperRifleItem.hasScopeAttached(inventoryStack)' '星空宙商店未將狙擊槍內置瞄準鏡計入瞄準鏡上限。'
Assert-Contains $shop 'maxOneItemEntry(TMMItems.SNIPER_RIFLE.getDefaultInstance()' '星空宙商店缺少上限 1 的狙擊槍。'
Assert-Matches $shop '(?s)var HOSHIZORA_SHOP.*?TMMItems\.CROWBAR\.getDefaultInstance\(\).*?customEntries\.put\(ModRoles\.HOSHIZORA_ID' '星空宙角色商店缺少撬棍。'

$sniperPayload = Read-Utf8 'src/main/java/io/wifi/starrailexpress/network/original/SniperShootPayload.java'
Assert-NotContains $sniperPayload 'ZORA_TARGET_HITS.remove(zoraHitKey);' '星空宙目標死亡後仍會清除命中次數，復活後會被重置。'

foreach ($language in @('zh_tw', 'zh_cn', 'en_us')) {
    $langPath = "src/main/resources/assets/noellesroles/lang/$language.json"
    $lang = Read-Utf8 $langPath | ConvertFrom-Json -AsHashtable
    foreach ($key in @('announcement.star.win.kamikiri_ice', 'game.win.star.kamikiri_ice')) {
        if (-not $lang.ContainsKey($key) -or [string]::IsNullOrWhiteSpace([string] $lang[$key])) {
            $failures.Add("$language 缺少翻譯：$key")
        }
    }

    $introPath = "src/main/resources/assets/role_modifier_intro/lang/$language.json"
    $intro = Read-Utf8 $introPath | ConvertFrom-Json -AsHashtable
    if (-not ([string] $intro['info.screen.roleid.kamikiri_ice.simple']).StartsWith('中立陣營')) {
        $failures.Add("$language 的神霧冰封簡介仍標成非中立陣營。")
    }
    Assert-NotContains ([string] $intro['info.screen.roleid.fu_tai']) '填補漏洞' "$language 的風太詳細說明仍保留舊版紅石被動。"
    Assert-NotContains ([string] $intro['info.screen.roleid.fu_tai']) '紅石掉落物' "$language 的風太詳細說明仍保留舊版紅石內容。"
    Assert-Contains ([string] $intro['info.screen.roleid.fu_tai']) '被動技〖夜行性動物〗' "$language 的風太詳細說明缺少新版被動。"
    Assert-Contains ([string] $intro['info.screen.roleid.fu_tai']) '無視失明' "$language 的風太詳細說明缺少無視失明效果。"
    Assert-Contains ([string] $intro['info.screen.roleid.kana']) '按下E打開選單可選擇1位不同玩家為目標 〖冷卻時間為30秒〗' "$language 的佳奈詳細說明未同步 E 選單與 30 秒冷卻。"
    Assert-Contains ([string] $intro['info.screen.roleid.nine_one']) '被攻擊後任務會每40秒刷新一次' "$language 的九月一詳細說明未同步 x.x.1。"
    Assert-Contains ([string] $intro['info.screen.roleid.baiyu']) '冷卻時間120秒' "$language 的白御詳細說明仍是舊冷卻。"
    Assert-Contains ([string] $intro['info.screen.roleid.baiyu']) '立即知道死者死因' "$language 的白御詳細說明仍是舊版 10 秒流程。"
}

if ($failures.Count -gt 0) {
    foreach ($failure in $failures) {
        Write-Host "FAIL: $failure" -ForegroundColor Red
    }
    throw "VTuber known-issue regression check failed: $($failures.Count) issue(s)."
}

Write-Host 'PASS: all VTuber known-issue regression checks passed.' -ForegroundColor Green
