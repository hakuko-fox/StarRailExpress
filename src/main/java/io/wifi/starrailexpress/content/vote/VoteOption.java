package io.wifi.starrailexpress.content.vote;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 投票中的一个选项，可以是玩家、文本或物品。
 */
public interface VoteOption {

    /** 该选项在 UI 中显示的文本 */
    Component display();

    /** 用于网络传输的标识符 */
    ResourceLocation typeId();

    /** 是否为玩家类型 */
    boolean isPlayer();

    /** 是否为物品类型 */
    boolean isItem();

    // ── 具体实现 ───────────────────────────────────────────

    class PlayerOption implements VoteOption {
        private Component displayName = null;
        private UUID player = null;

        public UUID player() {
            return player;
        }

        public PlayerOption(Component text, UUID uuid) {
            this.player = uuid;
            this.displayName = text;
        }

        public PlayerOption(UUID player) {
            this.player = player;
        }

        public PlayerOption(Player player) {
            if (player != null) {
                this.player = player.getUUID();
                this.displayName = player.getDisplayName();
            }
        }

        @Override
        public Component display() {
            return displayName;
        }

        @Override
        public ResourceLocation typeId() {
            return ResourceLocation.withDefaultNamespace("player");
        }

        @Override
        public boolean isPlayer() {
            return true;
        }

        @Override
        public boolean isItem() {
            return false;
        }
    }

    record TextOption(Component text, ResourceLocation id) implements VoteOption {
        public TextOption(Component text) {
            this(text, ResourceLocation.withDefaultNamespace("text"));
        }

        @Override
        public Component display() {
            return text;
        }

        @Override
        public ResourceLocation typeId() {
            return id;
        }

        @Override
        public boolean isPlayer() {
            return false;
        }

        @Override
        public boolean isItem() {
            return false;
        }
    }

    record ItemOption(ItemStack stack) implements VoteOption {
        @Override
        public Component display() {
            return stack.getHoverName();
        }

        @Override
        public ResourceLocation typeId() {
            return BuiltInRegistries.ITEM.getKey(stack.getItem());
        }

        @Override
        public boolean isPlayer() {
            return false;
        }

        @Override
        public boolean isItem() {
            return true;
        }
    }

    // 工厂方法
    static VoteOption player(Player player) {
        return new PlayerOption(player);
    }

    /**
     * @deprecated
     * 请使用 {@link #player(Component, UUID)} 代替。
     */
    @Deprecated
    static VoteOption player(UUID player) {
        return new PlayerOption(player);
    }

    // 工厂方法
    static VoteOption player(Component text, UUID player) {
        return new PlayerOption(text, player);
    }

    static VoteOption text(Component text) {
        return new TextOption(text);
    }

    static VoteOption item(ItemStack stack) {
        return new ItemOption(stack);
    }
}