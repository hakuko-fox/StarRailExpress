/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.backpack;

import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 场外背包数据模型（Gson POJO）。卡牌以计数形式存储，复用通行证的 {@link FactionCardType}。
 * 序列化格式与 {@code progression} 分区里的 {@code factionCards} 字节兼容，迁移值可直接搬运。
 */
public final class BackpackState {
    public Map<FactionCardType, Integer> cards = new EnumMap<>(FactionCardType.class);
    /** 跨局 Vtuber Coin。與局內 {@code SREPlayerShopComponent} 金幣完全分離。 */
    public int vtuberCoins;
    /** 商店購買的永久皮膚權益，元素格式為 {@code skin:<type>:<id>}。 */
    public Set<String> purchasedSkins = new HashSet<>();
    /** 可指定下一局具體職業的自選卡數量。 */
    public int roleChoiceCards;
    /** 已扣除但尚未結算的自選職業 ID；空字串表示沒有預約。 */
    public String pendingRoleId = "";
    /** 已啟用但尚未結算的陣營卡；與 pendingRoleId 互斥。 */
    public FactionCardType pendingFactionCard = FactionCardType.NONE;
    /** 最近一次已發獎的場次 UUID，防止同一結算重複入帳。 */
    public String lastVtuberCoinRoundId = "";
    /** 一次性「移动」迁移守卫：通行证卡牌已搬入背包后置 true。 */
    public boolean migrated = false;
    /** 各阵营卡最近一次成功激活的墙钟时间（epoch ms）。用于同种类使用间隔。 */
    public Map<FactionCardType, Long> cardLastUsedAt = new EnumMap<>(FactionCardType.class);
    public long version;

    public static BackpackState createDefault() {
        BackpackState state = new BackpackState();
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                state.cards.put(type, 0);
            }
        }
        return state;
    }

    public BackpackState normalized() {
        if (cards == null) {
            cards = new EnumMap<>(FactionCardType.class);
        }
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                cards.putIfAbsent(type, 0);
            }
        }
        // 钳制负值
        cards.replaceAll((type, count) -> count == null ? 0 : Math.max(0, count));
        vtuberCoins = Math.max(0, vtuberCoins);
        roleChoiceCards = Math.max(0, roleChoiceCards);
        if (purchasedSkins == null) {
            purchasedSkins = new HashSet<>();
        }
        purchasedSkins.removeIf(id -> id == null || id.isBlank());
        if (lastVtuberCoinRoundId == null) {
            lastVtuberCoinRoundId = "";
        }
        if (pendingRoleId == null) {
            pendingRoleId = "";
        }
        if (pendingFactionCard == null) {
            pendingFactionCard = FactionCardType.NONE;
        }
        if (!pendingRoleId.isBlank()) {
            pendingFactionCard = FactionCardType.NONE;
        }
        cardLastUsedAt = normalizeLastUsedAt(cardLastUsedAt);
        return this;
    }

    public long lastUsedAt(FactionCardType type) {
        if (cardLastUsedAt == null || type == null || type == FactionCardType.NONE) {
            return 0L;
        }
        Long value = cardLastUsedAt.get(type);
        return value == null ? 0L : Math.max(0L, value);
    }

    public void markUsed(FactionCardType type, long epochMs) {
        if (type == null || type == FactionCardType.NONE || epochMs <= 0L) {
            return;
        }
        if (cardLastUsedAt == null) {
            cardLastUsedAt = new EnumMap<>(FactionCardType.class);
        }
        cardLastUsedAt.put(type, epochMs);
    }

    public void copyFrom(BackpackState other) {
        this.cards = new EnumMap<>(FactionCardType.class);
        if (other.cards != null) {
            this.cards.putAll(other.cards);
        }
        this.migrated = other.migrated;
        this.vtuberCoins = other.vtuberCoins;
        this.roleChoiceCards = other.roleChoiceCards;
        this.pendingRoleId = other.pendingRoleId;
        this.pendingFactionCard = other.pendingFactionCard;
        this.purchasedSkins = other.purchasedSkins == null
                ? new HashSet<>()
                : new HashSet<>(other.purchasedSkins);
        this.lastVtuberCoinRoundId = other.lastVtuberCoinRoundId;
        this.version = other.version;
        this.cardLastUsedAt = other.cardLastUsedAt;
        normalized();
    }

    private static Map<FactionCardType, Long> normalizeLastUsedAt(Map<FactionCardType, Long> raw) {
        EnumMap<FactionCardType, Long> used = new EnumMap<>(FactionCardType.class);
        if (raw == null) {
            return used;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            FactionCardType type = parseType(entry.getKey());
            if (type == FactionCardType.NONE) {
                continue;
            }
            long epoch = toEpoch(entry.getValue());
            if (epoch > 0L) {
                used.put(type, epoch);
            }
        }
        return used;
    }

    private static FactionCardType parseType(Object key) {
        if (key instanceof FactionCardType type) {
            return type;
        }
        if (key == null) {
            return FactionCardType.NONE;
        }
        return FactionCardType.fromString(String.valueOf(key));
    }

    private static long toEpoch(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
