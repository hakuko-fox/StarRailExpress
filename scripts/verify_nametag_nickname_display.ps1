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

function Assert-NotContains([string] $text, [string] $unexpected, [string] $message) {
    if ($text.Contains($unexpected)) {
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
Assert-Contains $helper 'if (!player.isSpectator()) {' 'Only the TAB composer may branch on spectator mode.'
Assert-Contains $helper 'Component.translatable("starrailexpress.tag.spectator")' 'The TAB composer must prepend the localized spectator tag.'
Assert-Contains $helper 'Action.UPDATE_DISPLAY_NAME' 'Spectator-mode changes must be able to refresh the TAB-only display name.'

$playerMixin = Read-Utf8 $root 'src/main/java/io/wifi/starrailexpress/mixin/chat/PlayerPrefixMixin.java'
Assert-Contains $playerMixin 'PlayerDisplayNameHelper.compose(mainPlayer, currentName)' 'Chat display names must use the shared title and nickname composer.'

$tabMixin = Read-Utf8 $root 'src/main/java/io/wifi/starrailexpress/mixin/chat/ServerPlayerTabNameMixin.java'
Assert-Contains $tabMixin 'PlayerDisplayNameHelper.composeForTabList(player, currentName)' 'TAB display names must use the shared title and nickname composer.'

$serverPlayerMixin = Read-Utf8 $root 'src/main/java/io/wifi/starrailexpress/mixin/entity/player/ServerPlayerEntityMixin.java'
Assert-Contains $serverPlayerMixin '@Inject(method = "setGameMode", at = @At("RETURN"))' 'Game-mode changes must refresh the TAB-only spectator tag.'
Assert-Contains $serverPlayerMixin 'PlayerDisplayNameHelper.syncTabListDisplayName(self);' 'The TAB display name must be republished after entering or leaving spectator mode.'

$mixinConfig = Read-Utf8 $root 'src/main/resources/starrailexpress.mixins.json'
Assert-Contains $mixinConfig '"chat.ServerPlayerTabNameMixin"' 'The StarRailExpress TAB display-name mixin must be registered.'

$component = Read-Utf8 $root 'src/main/java/net/exmo/sre/nametag/NameTagInventoryComponent.java'
Assert-Contains $component 'PlayerDisplayNameHelper.syncTabListDisplayName(serverPlayer);' 'Changing a selected title must publish a vanilla TAB display-name update.'
Assert-Contains $component 'syncSelectedNameTagDisplay();' 'Selected-title changes must synchronize both title render data and the TAB display name.'
Assert-NotContains $component 'Component.translatable("starrailexpress.tag.spectator")' 'Spectator mode must not add a spectator title to the player display name.'

$renderer = Read-Utf8 $root 'src/main/java/io/wifi/starrailexpress/client/gui/RoleNameRenderer.java'
Assert-Contains $renderer 'return playerInfo.getTabListDisplayName();' 'The nearby-player HUD must read the synchronized TAB display name.'
Assert-Contains $renderer 'Component fallbackName = removeTabOnlySpectatorTag(displayName);' 'TAB-only spectator tags must be removed before nearby-player and end-game name rendering.'
Assert-Contains $renderer '"starrailexpress.tag.spectator".equals(translatable.getKey())' 'Only the structured spectator prefix may be removed outside the TAB list.'
Assert-Contains $renderer 'String title = displayTags.get(playerId);' 'The nearby-player HUD must resolve the synchronized title separately.'
Assert-Contains $renderer 'PlayerNameLines nameLines = getDisplayName(target);' 'The nearby-player HUD must resolve the title and player name as separate lines.'
Assert-Contains $renderer 'titleTag = nameLines.title();' 'The nearby-player HUD must keep the title separate from the player name.'
Assert-Contains $renderer 'ctx.drawString(font, titleTag,' 'The nearby-player HUD must draw the title on its own row.'
Assert-Contains $renderer 'ctx.drawString(font, nametag, -nameWidth / 2, nameY,' 'The nearby-player HUD must draw the nickname or username on a second row.'
Assert-Contains $renderer 'headerExtraLines * (font.lineHeight + 2)' 'Role and participation text must move down when the title adds a row.'
Assert-Contains $renderer 'record PlayerNameLines(Component title, Component name)' 'The nearby-player HUD and end-game report must model title and name rows separately.'

$roundRenderer = Read-Utf8 $root 'src/main/java/io/wifi/starrailexpress/client/gui/RoundTextRenderer.java'
Assert-Contains $roundRenderer 'RoleNameRenderer.PlayerNameLines nameLines = RoleNameRenderer.getDisplayName(' 'The end-game report must use the same title and player-name line resolver as the nearby-player HUD.'
Assert-Contains $roundRenderer 'private static final float ROUND_END_CONTENT_SCALE = 1.2f;' 'The complete end-game report must render 20 percent larger.'
Assert-Contains $roundRenderer 'context.pose().scale(ROUND_END_CONTENT_SCALE, ROUND_END_CONTENT_SCALE, 1f);' 'The end-game report scale must apply around its center without changing the internal layout.'
Assert-Contains $roundRenderer 'drawPlayerCardText(context, renderer, nameLines.title(), 8.5f,' 'The end-game report title must start below the player head.'
Assert-Contains $roundRenderer 'drawPlayerCardText(context, renderer, nameLines.name(), 10.5f,' 'The end-game report nickname or username must render below the title.'
Assert-Contains $roundRenderer 'context.pose().translate(38, 40, 200);' 'The role name must move below the title and player-name rows.'
Assert-Contains $roundRenderer 'PLAYER_CARD_TEXT_MAX_WIDTH / textWidth' 'Long end-game report text must scale down before it can overlap adjacent cards.'
Assert-Contains $renderer 'PlayerNameLines storedNameLines = splitStoredDisplayName(fallbackName);' 'The end-game report must split the stored composed display name when the live title map is unavailable.'
Assert-Contains $renderer 'fallbackName.getSiblings()' 'The stored end-game display name must be split from its structured Component children.'

$roundEndComponent = Read-Utf8 $root 'src/main/java/io/wifi/starrailexpress/cca/SREGameRoundEndComponent.java'
Assert-Contains $roundEndComponent 'player.getDisplayName().copy()' 'The end-game report must store the chat display name, not the TAB-only spectator name.'
Assert-NotContains $roundEndComponent 'getTabListDisplayName()' 'The end-game report must never store the TAB-only spectator name.'

if ($failures.Count -gt 0) {
    foreach ($failure in $failures) {
        Write-Host "FAIL: $failure" -ForegroundColor Red
    }
    throw "NameTag and nickname display regression check failed: $($failures.Count) issue(s)."
}

Write-Host 'PASS: NameTag, nickname, TAB, chat, and nearby-player HUD checks passed.' -ForegroundColor Green
