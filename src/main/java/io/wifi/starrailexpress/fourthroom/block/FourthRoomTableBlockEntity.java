package io.wifi.starrailexpress.fourthroom.block;

import io.wifi.starrailexpress.fourthroom.effect.EffectEvent;
import io.wifi.starrailexpress.fourthroom.effect.EffectQueue;
import io.wifi.starrailexpress.fourthroom.effect.TableEffectEvents;
import io.wifi.starrailexpress.fourthroom.network.FourthRoomTableEffectsPayload;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * In-world host for a FourthRoom card battle table.
 * Holds the visual state synced to clients and drives animations via EffectQueue.
 * The authoritative game logic still lives in FourthRoomGameManager / FourthRoomSavedData;
 * this entity acts as a rendering + interaction bridge.
 */
public class FourthRoomTableBlockEntity extends BlockEntity {

    private static final int MAX_SEATED_PLAYERS = 2;

    /** Room ID this table is linked to (-1 = unlinked). */
    private int linkedRoomId = -1;

    /** UUIDs of players currently seated at this table. */
    private final List<UUID> seatedPlayers = new ArrayList<>();

    /** Last action description shown on the table surface. */
    private String lastActionText = "";

    /** Seat A player uuid/name snapshot. */
    private String seatAPlayerUuid = "";
    private String seatAPlayerName = "";
    private boolean seatAAlive;
    private int seatAHiddenIdentityCount;

    /** Seat B player uuid/name snapshot. */
    private String seatBPlayerUuid = "";
    private String seatBPlayerName = "";
    private boolean seatBAlive;
    private int seatBHiddenIdentityCount;

    /** Number of cards in draw pile (for visual stack height). */
    private int drawPileSize;

    /** Number of cards in discard pile. */
    private int discardPileSize;

    /** Top discard card id for face-up rendering. */
    private String topDiscardCardId = "";

    /** Active player UUID (for turn indicator). */
    private String activePlayerUuid = "";

    /** Current game phase name. */
    private String phase = "";

    // --- Client-side animation ---

    /** Client-side effect queue for timed animations. */
    private transient EffectQueue clientEffectQueue;

    /** Client-side animation tick counter. */
    private transient int clientTicks;

    /** Table actions displayed as physical cards on the surface. */
    private final List<TableCardDisplay> tableCards = new ArrayList<>();

    /** Temporary in-world flying cards and highlight rings. */
    private transient List<ActiveCardAnimation> activeCardAnimations = new ArrayList<>();
    private transient List<AnchorPulseState> activePulses = new ArrayList<>();
    private transient BannerState activeBanner;

    public FourthRoomTableBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.FOURTH_ROOM_TABLE, pos, state);
    }

    // ── Server Methods ──────────────────────────────────────────

    public void onPlayerInteract(ServerPlayer player) {
        if (level == null || level.isClientSide) return;

        UUID uuid = player.getUUID();
        if (seatedPlayers.contains(uuid)) {
            seatedPlayers.remove(uuid);
            player.displayClientMessage(Component.literal("§e[第四房间] §f已离开牌桌"), true);
        } else if (seatedPlayers.size() < MAX_SEATED_PLAYERS) {
            seatedPlayers.add(uuid);
            player.displayClientMessage(Component.literal("§e[第四房间] §f已加入牌桌 (" + seatedPlayers.size() + "/" + MAX_SEATED_PLAYERS + ")"), true);
        } else {
            player.displayClientMessage(Component.literal("§e[第四房间] §c当前牌桌只支持双人对战"), true);
        }
        sync();
    }

    /**
     * Called by FourthRoomGameManager to push room visual state to the world table.
     */
    public void applyRoomVisualState(int roomId, @Nullable SeatView seatA, @Nullable SeatView seatB,
                                     int drawPile, int discardPile, String topDiscard,
                                     String activePlayer, String phase, String actionText,
                                     List<TableCardDisplay> recentCards) {
        this.linkedRoomId = roomId;
        this.drawPileSize = drawPile;
        this.discardPileSize = discardPile;
        this.topDiscardCardId = topDiscard != null ? topDiscard : "";
        this.activePlayerUuid = activePlayer != null ? activePlayer : "";
        this.phase = phase != null ? phase : "";
        this.lastActionText = actionText != null ? actionText : "";
        applySeat(true, seatA);
        applySeat(false, seatB);
        this.tableCards.clear();
        this.tableCards.addAll(recentCards);
        sync();
    }

    public void clearLinkedRoomState() {
        linkedRoomId = -1;
        drawPileSize = 0;
        discardPileSize = 0;
        topDiscardCardId = "";
        activePlayerUuid = "";
        phase = "";
        lastActionText = "";
        seatAPlayerUuid = "";
        seatAPlayerName = "";
        seatAAlive = false;
        seatAHiddenIdentityCount = 0;
        seatBPlayerUuid = "";
        seatBPlayerName = "";
        seatBAlive = false;
        seatBHiddenIdentityCount = 0;
        tableCards.clear();
        if (clientEffectQueue != null) {
            clientEffectQueue.clear();
        }
        activeCardAnimations.clear();
        activePulses.clear();
        activeBanner = null;
        sync();
    }

    /**
     * Push a list of effects to nearby clients.
     */
    public void broadcastEffects(List<EffectEvent> effects) {
        if (!(level instanceof ServerLevel serverLevel) || effects.isEmpty()) return;
        FourthRoomTableEffectsPayload.sendNearby(serverLevel, getBlockPos(), effects);
    }

    public void addTableCard(String titleText, String summaryText, int accentColor, boolean highlight) {
        tableCards.add(new TableCardDisplay(titleText, summaryText, accentColor, highlight, System.currentTimeMillis()));
        if (tableCards.size() > 8) {
            tableCards.removeFirst();
        }
        sync();
    }

    public int linkedRoomId() {
        return linkedRoomId;
    }

    public void setLinkedRoomId(int roomId) {
        this.linkedRoomId = roomId;
        sync();
    }

    public List<UUID> seatedPlayers() {
        return seatedPlayers;
    }

    // ── Client Methods ──────────────────────────────────────────

    public void clientTick() {
        clientTicks++;
        if (clientEffectQueue == null) {
            clientEffectQueue = new EffectQueue();
            clientEffectQueue.setOrigin(getBlockPos());
        }
        clientEffectQueue.tick();
        long now = System.currentTimeMillis();
        activeCardAnimations.removeIf(animation -> animation.expired(now));
        activePulses.removeIf(pulse -> pulse.expired(now));
        if (activeBanner != null && activeBanner.expired(now)) {
            activeBanner = null;
        }
    }

    public int clientTicks() {
        return clientTicks;
    }

    public EffectQueue clientEffectQueue() {
        if (clientEffectQueue == null) {
            clientEffectQueue = new EffectQueue();
            clientEffectQueue.setOrigin(getBlockPos());
        }
        return clientEffectQueue;
    }

    public void startCardAnimation(String label, boolean gold, TableEffectEvents.TableAnchor from,
                                   TableEffectEvents.TableAnchor to, int color, long durationMs) {
        activeCardAnimations.add(new ActiveCardAnimation(label, gold, from, to, color,
                System.currentTimeMillis(), Math.max(180L, durationMs)));
        if (activeCardAnimations.size() > 16) {
            activeCardAnimations.removeFirst();
        }
    }

    public void startPulse(TableEffectEvents.TableAnchor anchor, int color, float intensity, long durationMs) {
        activePulses.add(new AnchorPulseState(anchor, color, intensity, System.currentTimeMillis(),
                Math.max(180L, durationMs)));
        if (activePulses.size() > 12) {
            activePulses.removeFirst();
        }
    }

    public void showBanner(String text, int color, long durationMs) {
        activeBanner = new BannerState(text, color, System.currentTimeMillis(), Math.max(320L, durationMs));
    }

    // ── Accessors for Renderer ──────────────────────────────────

    public int drawPileSize() { return drawPileSize; }
    public int discardPileSize() { return discardPileSize; }
    public String topDiscardCardId() { return topDiscardCardId; }
    public String activePlayerUuid() { return activePlayerUuid; }
    public String phase() { return phase; }
    public String lastActionText() { return lastActionText; }
    public String seatAPlayerUuid() { return seatAPlayerUuid; }
    public String seatAPlayerName() { return seatAPlayerName; }
    public boolean seatAAlive() { return seatAAlive; }
    public int seatAHiddenIdentityCount() { return seatAHiddenIdentityCount; }
    public String seatBPlayerUuid() { return seatBPlayerUuid; }
    public String seatBPlayerName() { return seatBPlayerName; }
    public boolean seatBAlive() { return seatBAlive; }
    public int seatBHiddenIdentityCount() { return seatBHiddenIdentityCount; }
    public List<TableCardDisplay> tableCards() { return tableCards; }
    public List<ActiveCardAnimation> activeCardAnimations() { return activeCardAnimations; }
    public List<AnchorPulseState> activePulses() { return activePulses; }
    public BannerState activeBanner() { return activeBanner; }

    // ── Sync (BlockEntity Update Packet) ────────────────────────

    public void sync() {
        setChanged();
        if (level != null) {
            BlockState state = level.getBlockState(getBlockPos());
            level.sendBlockUpdated(getBlockPos(), state, state, 2);
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, provider);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ── NBT Persistence ─────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("LinkedRoomId", linkedRoomId);
        tag.putInt("DrawPileSize", drawPileSize);
        tag.putInt("DiscardPileSize", discardPileSize);
        tag.putString("TopDiscardCardId", topDiscardCardId);
        tag.putString("ActivePlayerUuid", activePlayerUuid);
        tag.putString("Phase", phase);
        tag.putString("LastActionText", lastActionText);
        tag.putString("SeatAPlayerUuid", seatAPlayerUuid);
        tag.putString("SeatAPlayerName", seatAPlayerName);
        tag.putBoolean("SeatAAlive", seatAAlive);
        tag.putInt("SeatAHiddenIdentityCount", seatAHiddenIdentityCount);
        tag.putString("SeatBPlayerUuid", seatBPlayerUuid);
        tag.putString("SeatBPlayerName", seatBPlayerName);
        tag.putBoolean("SeatBAlive", seatBAlive);
        tag.putInt("SeatBHiddenIdentityCount", seatBHiddenIdentityCount);

        ListTag seated = new ListTag();
        for (UUID uuid : seatedPlayers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Uuid", uuid);
            seated.add(entry);
        }
        tag.put("SeatedPlayers", seated);

        ListTag cards = new ListTag();
        for (TableCardDisplay card : tableCards) {
            cards.add(card.save());
        }
        tag.put("TableCards", cards);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        linkedRoomId = tag.getInt("LinkedRoomId");
        drawPileSize = tag.getInt("DrawPileSize");
        discardPileSize = tag.getInt("DiscardPileSize");
        topDiscardCardId = tag.getString("TopDiscardCardId");
        activePlayerUuid = tag.getString("ActivePlayerUuid");
        phase = tag.getString("Phase");
        lastActionText = tag.getString("LastActionText");
        seatAPlayerUuid = tag.getString("SeatAPlayerUuid");
        seatAPlayerName = tag.getString("SeatAPlayerName");
        seatAAlive = tag.getBoolean("SeatAAlive");
        seatAHiddenIdentityCount = tag.getInt("SeatAHiddenIdentityCount");
        seatBPlayerUuid = tag.getString("SeatBPlayerUuid");
        seatBPlayerName = tag.getString("SeatBPlayerName");
        seatBAlive = tag.getBoolean("SeatBAlive");
        seatBHiddenIdentityCount = tag.getInt("SeatBHiddenIdentityCount");

        seatedPlayers.clear();
        for (Tag entry : tag.getList("SeatedPlayers", Tag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag compound) {
                seatedPlayers.add(compound.getUUID("Uuid"));
            }
        }

        tableCards.clear();
        for (Tag entry : tag.getList("TableCards", Tag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag compound) {
                tableCards.add(TableCardDisplay.load(compound));
            }
        }
    }

    private void applySeat(boolean seatAFlag, @Nullable SeatView seat) {
        if (seatAFlag) {
            seatAPlayerUuid = seat == null ? "" : seat.playerUuid();
            seatAPlayerName = seat == null ? "" : seat.playerName();
            seatAAlive = seat != null && seat.alive();
            seatAHiddenIdentityCount = seat == null ? 0 : seat.hiddenIdentityCount();
            return;
        }
        seatBPlayerUuid = seat == null ? "" : seat.playerUuid();
        seatBPlayerName = seat == null ? "" : seat.playerName();
        seatBAlive = seat != null && seat.alive();
        seatBHiddenIdentityCount = seat == null ? 0 : seat.hiddenIdentityCount();
    }

    public record SeatView(String playerUuid, String playerName, boolean alive, int hiddenIdentityCount) {
    }

    // ── Inner: Table Card Display Data ──────────────────────────

    public record TableCardDisplay(String titleText, String summaryText, int accentColor, boolean highlight,
                                   long timestamp) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("TitleText", titleText);
            tag.putString("SummaryText", summaryText);
            tag.putInt("AccentColor", accentColor);
            tag.putBoolean("Highlight", highlight);
            tag.putLong("Timestamp", timestamp);
            return tag;
        }

        public static TableCardDisplay load(CompoundTag tag) {
            return new TableCardDisplay(
                    tag.contains("TitleText") ? tag.getString("TitleText") : tag.getString("CardId"),
                    tag.contains("SummaryText") ? tag.getString("SummaryText") : tag.getString("PlayerName"),
                    tag.contains("AccentColor") ? tag.getInt("AccentColor") : 0xFFD7D7D7,
                    tag.contains("Highlight") && tag.getBoolean("Highlight"),
                    tag.getLong("Timestamp"));
        }
    }

    public record ActiveCardAnimation(String label, boolean gold, TableEffectEvents.TableAnchor from,
                                      TableEffectEvents.TableAnchor to, int color, long startTimeMs,
                                      long durationMs) {
        public boolean expired(long now) {
            return now >= startTimeMs + durationMs;
        }

        public float progress(long now) {
            return Mth.clamp((now - startTimeMs) / (float) durationMs, 0.0F, 1.0F);
        }
    }

    public record AnchorPulseState(TableEffectEvents.TableAnchor anchor, int color, float intensity, long startTimeMs,
                                   long durationMs) {
        public boolean expired(long now) {
            return now >= startTimeMs + durationMs;
        }

        public float progress(long now) {
            return Mth.clamp((now - startTimeMs) / (float) durationMs, 0.0F, 1.0F);
        }
    }

    public record BannerState(String text, int color, long startTimeMs, long durationMs) {
        public boolean expired(long now) {
            return now >= startTimeMs + durationMs;
        }

        public float alpha(long now) {
            float progress = Mth.clamp((now - startTimeMs) / (float) durationMs, 0.0F, 1.0F);
            float fadeIn = Mth.clamp(progress / 0.18F, 0.0F, 1.0F);
            float fadeOut = Mth.clamp((1.0F - progress) / 0.22F, 0.0F, 1.0F);
            return Math.min(fadeIn, fadeOut);
        }
    }
}
