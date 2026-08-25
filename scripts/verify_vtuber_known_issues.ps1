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

$fuTaiEntity = Read-Utf8 'src/main/java/org/agmas/noellesroles/content/entity/FuTaiGlitchEntity.java'
Assert-Contains $fuTaiEntity 'extends Entity implements ItemSupplier' '風太紅石必須是可攻擊的一般實體，不可使用會被伺服器拒絕攻擊的 ItemEntity。'
Assert-NotContains $fuTaiEntity 'extends ItemEntity' '風太紅石仍繼承 ItemEntity，攻擊會觸發「試圖攻擊無效的實體」。'

$client = Read-Utf8 'src/main/java/org/agmas/noellesroles/client/NoellesrolesClient.java'
Assert-Contains $client 'net.minecraft.client.renderer.entity.ThrownItemRenderer::new' '風太紅石改為 ItemSupplier 後必須使用 ThrownItemRenderer。'

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
    Assert-Contains ([string] $intro['info.screen.roleid.fu_tai']) '該紅石掉落物消失' "$language 的風太詳細說明漏掉紅石消失。"
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
