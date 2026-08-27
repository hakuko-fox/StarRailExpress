param(
    [string] $OfflineNicknamesRoot = ''
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OfflineNicknamesRoot)) {
    $OfflineNicknamesRoot = Join-Path (Split-Path -Parent $root) 'offline-nicknames-fabric-1.21.10'
}

$failures = [System.Collections.Generic.List[string]]::new()

function Read-Utf8([string] $basePath, [string] $relativePath) {
    $path = Join-Path $basePath $relativePath
    if (-not (Test-Path -LiteralPath $path)) {
        $failures.Add("Missing required file: $path")
        return ''
    }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $path
}

function Assert-Contains([string] $text, [string] $expected, [string] $message) {
    if (-not $text.Contains($expected)) {
        $failures.Add($message)
    }
}

$offlineMod = Read-Utf8 $OfflineNicknamesRoot 'src/main/java/hakukofox/nickname/OfflineNicknamesMod.java'
Assert-Contains $offlineMod 'public static boolean isDisplayManagedByStarRailExpress()' 'Offline Nicknames must expose the StarRailExpress display ownership check.'
Assert-Contains $offlineMod 'FabricLoader.getInstance().isModLoaded("starrailexpress")' 'Offline Nicknames must detect StarRailExpress by mod id.'
Assert-Contains $offlineMod 'applyNicknameAndRefreshTabList(server, handler.player)' 'Offline Nicknames must refresh the composed TAB name after login.'
Assert-Contains $offlineMod 'applyNicknameAndRefreshTabList(newPlayer.getServer(), newPlayer)' 'Offline Nicknames must refresh the composed TAB name after respawn.'

foreach ($mixinPath in @(
        'src/main/java/hakukofox/nickname/mixin/EntityDisplayNameMixin.java',
        'src/main/java/hakukofox/nickname/mixin/ServerPlayerTabNameMixin.java')) {
    $mixin = Read-Utf8 $OfflineNicknamesRoot $mixinPath
    Assert-Contains $mixin 'OfflineNicknamesMod.isDisplayManagedByStarRailExpress()' "$mixinPath must yield display ownership to StarRailExpress."
}

$helper = Read-Utf8 $root 'src/main/java/net/exmo/sre/nametag/PlayerDisplayNameHelper.java'
Assert-Contains $helper 'private static final String OFFLINE_NICKNAMES_MOD_ID = "nickname";' 'The display helper must identify Offline Nicknames by mod id.'
Assert-Contains $helper 'FabricLoader.getInstance().isModLoaded(OFFLINE_NICKNAMES_MOD_ID)' 'The display helper must only consume custom names when Offline Nicknames is loaded.'
Assert-Contains $helper 'player.getCustomName()' 'The display helper must use the synchronized nickname custom name.'
Assert-Contains $helper 'Component.literal("[")' 'The display helper must format the title inside brackets.'

$playerMixin = Read-Utf8 $root 'src/main/java/io/wifi/starrailexpress/mixin/chat/PlayerPrefixMixin.java'
Assert-Contains $playerMixin 'PlayerDisplayNameHelper.compose(mainPlayer, currentName)' 'Chat display names must use the shared title and nickname composer.'

$tabMixin = Read-Utf8 $root 'src/main/java/io/wifi/starrailexpress/mixin/chat/ServerPlayerTabNameMixin.java'
Assert-Contains $tabMixin 'PlayerDisplayNameHelper.composeForTabList(player, currentName)' 'TAB display names must use the shared title and nickname composer.'

$mixinConfig = Read-Utf8 $root 'src/main/resources/starrailexpress.mixins.json'
Assert-Contains $mixinConfig '"chat.ServerPlayerTabNameMixin"' 'The StarRailExpress TAB display-name mixin must be registered.'

$component = Read-Utf8 $root 'src/main/java/net/exmo/sre/nametag/NameTagInventoryComponent.java'
Assert-Contains $component 'ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME' 'Changing a selected title must publish a vanilla TAB display-name update.'
Assert-Contains $component 'syncSelectedNameTagDisplay();' 'Selected-title changes must synchronize both title render data and the TAB display name.'

$renderer = Read-Utf8 $root 'src/main/java/io/wifi/starrailexpress/client/gui/RoleNameRenderer.java'
$tabReturn = $renderer.IndexOf('return playerInfo.getTabListDisplayName();')
$fallbackTitle = $renderer.IndexOf('String title = displayTags.get(target.getUUID());')
if ($tabReturn -lt 0 -or $fallbackTitle -lt 0 -or $tabReturn -gt $fallbackTitle) {
    $failures.Add('The nearby-player HUD must prefer the fully composed TAB display name before the legacy title fallback.')
}

if ($failures.Count -gt 0) {
    foreach ($failure in $failures) {
        Write-Host "FAIL: $failure" -ForegroundColor Red
    }
    throw "NameTag and nickname display regression check failed: $($failures.Count) issue(s)."
}

Write-Host 'PASS: NameTag, nickname, TAB, chat, and nearby-player HUD checks passed.' -ForegroundColor Green
