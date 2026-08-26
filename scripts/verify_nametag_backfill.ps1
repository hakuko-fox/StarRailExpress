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

$command = Read-Utf8 'src/main/java/net/exmo/sre/nametag/NameTagCommand.java'
Assert-Contains $command 'Commands.literal("nametag:backfill").requires(source -> source.hasPermission(2))' '補發指令必須要求權限等級 2。'
Assert-Contains $command 'Commands.argument("targets", EntityArgument.players())' '補發指令必須支援玩家選擇器與多個線上玩家。'
Assert-Contains $command 'PlayerStatsManager.isReadyForTitleBackfill(target)' '補發前必須等待玩家統計完成載入。'
Assert-Contains $command 'TitleUnlockManager.backfillStoredTitles(target)' '補發指令未呼叫 server-side 稱號判定。'

$manager = Read-Utf8 'src/main/java/net/exmo/sre/nametag/TitleUnlockManager.java'
foreach ($criterion in @(
        'KILLER_WINS', 'POLICE_WINS', 'NEUTRAL_WINS', 'GAMES_PLAYED',
        'KILLER_STREAK', 'POLICE_STREAK', 'NEUTRAL_STREAK', 'ALL_FACTION_WINS',
        'LOSS_STREAK', 'LOW_WIN_RATE', 'FIRST_DEATH_STREAK')) {
    Assert-Contains $manager "case $criterion" "補發判定漏掉可由現有資料驗證的 $criterion。"
}
Assert-Contains $manager 'case FIRST_DEATH, KILLER_PERFECT_WIN, POLICE_PERFECT_WIN, ADMIN_GRANTED -> false;' '補發判定必須排除單局事件與管理員專屬稱號。'
Assert-Contains $manager 'component.addNameTagsSilently(eligibleTitles)' '補發不得逐個觸發全服解鎖通知。'

$component = Read-Utf8 'src/main/java/net/exmo/sre/nametag/NameTagInventoryComponent.java'
Assert-Contains $component 'public List<String> addNameTagsSilently(Iterable<String> recoveredNameTags)' '缺少靜默批次寫入介面。'
Assert-Contains $component 'this.nameTags.addAll(added);' '靜默補發沒有寫入稱號清單。'
Assert-Contains $component 'this.persistLocal();' '靜默補發沒有持久化本機資料。'
Assert-Contains $component 'syncToNetwork();' '靜默補發沒有同步 MySQL 路徑。'
if ([regex]::Matches($component, 'this\.broadcastUnlock\(').Count -ne 1) {
    $failures.Add('全服解鎖廣播只能保留在一般單筆解鎖流程，不得出現在補發流程。')
}

$statsManager = Read-Utf8 'src/main/java/io/wifi/starrailexpress/stats/PlayerStatsManager.java'
Assert-Contains $statsManager 'public static boolean isReadyForTitleBackfill(ServerPlayer player)' '缺少補發資料載入完成檢查。'
Assert-Contains $statsManager '(!isDatabaseEnabled() || entry.databaseLoaded)' '啟用資料庫同步時，補發必須等待遠端統計載入完成。'

foreach ($language in @('en_us', 'zh_cn', 'zh_tw')) {
    $lang = Read-Utf8 "src/main/resources/assets/starrailexpress/lang/$language.json" | ConvertFrom-Json -AsHashtable
    foreach ($key in @(
            'command.sre.nametag.backfill.success',
            'command.sre.nametag.backfill.none',
            'command.sre.nametag.backfill.not_ready')) {
        if (-not $lang.ContainsKey($key) -or [string]::IsNullOrWhiteSpace([string] $lang[$key])) {
            $failures.Add("$language 缺少翻譯：$key")
        }
    }
}

$docs = Read-Utf8 'docs/commands.md'
Assert-Contains $docs '### `nametag:backfill`' '指令文件缺少 nametag:backfill。'
Assert-Contains $docs '`@a`' '指令文件沒有說明多玩家選擇器。'

if ($failures.Count -gt 0) {
    foreach ($failure in $failures) {
        Write-Host "FAIL: $failure" -ForegroundColor Red
    }
    throw "NameTag backfill regression check failed: $($failures.Count) issue(s)."
}

Write-Host 'PASS: NameTag admin backfill command checks passed.' -ForegroundColor Green
