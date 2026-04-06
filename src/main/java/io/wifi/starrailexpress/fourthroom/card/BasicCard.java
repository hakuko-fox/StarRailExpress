package io.wifi.starrailexpress.fourthroom.card;

import io.wifi.starrailexpress.fourthroom.game.FourthRoomGameManager;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public enum BasicCard implements Card {
    DEATH("death") {
        @Override
        public boolean isInstantOnDraw() {
            return true;
        }

        @Override
        public void onDraw(FourthRoomGameManager manager, UUID playerId, CardInstance instance) {
            manager.inflictCardDamage(playerId, playerId, "death_card");
        }

        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            return false;
        }
    },
    CLEANSE("cleanse") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            manager.shuffleDiscardIntoDeck(playerId);
            return true;
        }
    },
    BOTTOM_DRAW("bottom_draw") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            manager.drawCards(playerId, 1, true);
            return true;
        }
    },
    SEIZE("seize") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            return manager.stealRandomCard(playerId, targetId);
        }
    },
    SKIP("skip") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            return manager.skipOpponentTurn(playerId, targetId);
        }
    },
    VETO("veto") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            if (manager.hasCardInHand(targetId, "veto")) {
                manager.removeCardFromHand(targetId, "veto");
                manager.logPlayerRoomAction(playerId, "card", "否决", "被否决抵消", manager.playerName(targetId), "");
            } else {
                manager.drawCards(targetId, 1, false);
                manager.logPlayerRoomAction(playerId, "card", "否决", "强制摸牌", manager.playerName(targetId), "");
            }
            return true;
        }
    },
    POINT_KILL("point_kill") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            manager.addExtraTurns(targetId, 1);
            return true;
        }
    },
    DISMANTLE("dismantle") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            return manager.dismantleRandomCard(targetId);
        }
    },
    PEEK("peek") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            manager.peekTopCards(playerId, 5);
            return true;
        }
    },
    LIFE("life") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            manager.addLifeShield(playerId, 1);
            return true;
        }
    };

    private final String id;

    BasicCard(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public CardCategory category() {
        return CardCategory.BASIC;
    }
}