/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.backpack.BackpackManager;
import io.wifi.starrailexpress.backpack.BackpackState;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.client.data.ClientPlayerDataCache;
import io.wifi.starrailexpress.client.data.ClientVtuberStoreCache;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.network.VtuberStorePurchasePayload;
import io.wifi.starrailexpress.vtuberstore.VtuberStoreManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public final class VtuberStoreScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int PAGE_SIZE = 8;
    private static final String[] FILTERS = { "all", "knife", "revolver", "bat", "grenade" };

    private final Screen parent;
    private Tab tab = Tab.SKIN;
    private int filterIndex;
    private int page;
    private String selectedProductId;
    private Button skinTabButton;
    private Button cardTabButton;
    private Button filterButton;
    private Button previousButton;
    private Button nextButton;
    private Button buyButton;

    public VtuberStoreScreen(Screen parent) {
        super(Component.translatable("screen.sre.vtuber_store.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelWidth = Mth.clamp(width - 40, 300, 460);
        int x = (width - panelWidth) / 2;
        int top = Math.max(20, (height - 276) / 2);
        skinTabButton = addRenderableWidget(Button.builder(Component.translatable("screen.sre.vtuber_store.tab.skins"),
                button -> switchTab(Tab.SKIN)).bounds(x, top + 34, 90, 20).build());
        cardTabButton = addRenderableWidget(Button.builder(Component.translatable("screen.sre.vtuber_store.tab.cards"),
                button -> switchTab(Tab.CARD)).bounds(x + 94, top + 34, 90, 20).build());
        filterButton = addRenderableWidget(Button.builder(filterLabel(), button -> cycleFilter())
                .bounds(x + panelWidth - 110, top + 34, 110, 20).build());
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(x, top + 252, 32, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(x + 36, top + 252, 32, 20).build());
        buyButton = addRenderableWidget(Button.builder(Component.translatable("screen.sre.vtuber_store.buy"),
                button -> purchaseSelected()).bounds(x + panelWidth - 154, top + 252, 92, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(x + panelWidth - 58, top + 252, 58, 20).build());
        refreshButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = Mth.clamp(width - 40, 300, 460);
        int x = (width - panelWidth) / 2;
        int top = Math.max(20, (height - 276) / 2);
        graphics.fillGradient(x - 8, top - 8, x + panelWidth + 8, top + 280, 0xEE1A1008, 0xEE0C0805);
        graphics.renderOutline(x - 8, top - 8, panelWidth + 16, 288, 0xFFD4AF37);
        graphics.drawCenteredString(font, title, width / 2, top, 0xFFFFE7A0);

        BackpackState backpack = backpack();
        graphics.drawString(font, Component.translatable("screen.sre.vtuber_store.balance", backpack.vtuberCoins),
                x, top + 18, 0xFFFFD45A, false);

        List<VtuberStoreManager.CatalogEntry> products = filteredProducts();
        int maxPage = maxPage(products);
        if (page > maxPage) {
            page = maxPage;
        }
        int start = page * PAGE_SIZE;
        int end = Math.min(products.size(), start + PAGE_SIZE);
        if (products.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.sre.vtuber_store.empty"),
                    width / 2, top + 130, 0xFF9E8B6E);
        }
        for (int i = start; i < end; i++) {
            renderRow(graphics, products.get(i), x, top + 60 + (i - start) * ROW_HEIGHT,
                    panelWidth, mouseX, mouseY, backpack);
        }
        graphics.drawString(font, Component.translatable("screen.sre.vtuber_store.page", page + 1, maxPage + 1),
                x + 76, top + 258, 0xFFBBAA88, false);
        refreshButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRow(GuiGraphics graphics, VtuberStoreManager.CatalogEntry product, int x, int y,
            int width, int mouseX, int mouseY, BackpackState backpack) {
        boolean selected = product.id().equals(selectedProductId);
        boolean hovered = inside(mouseX, mouseY, x, y, width, ROW_HEIGHT - 2);
        boolean owned = isOwned(product, backpack);
        int background = selected ? 0xAA6B4D18 : hovered ? 0x88463016 : 0x66170E07;
        graphics.fill(x, y, x + width, y + ROW_HEIGHT - 2, background);
        graphics.renderOutline(x, y, width, ROW_HEIGHT - 2, selected ? 0xFFFFD45A : 0xFF5E4824);
        Component name = Component.translatable(product.translationKey());
        graphics.drawString(font, name, x + 8, y + 7, product.enabled() ? 0xFFFFF4DC : 0xFF776F64, false);
        String state = owned
                ? Component.translatable("screen.sre.vtuber_store.owned").getString()
                : product.price() + " Vtuber Coin";
        int stateWidth = font.width(state);
        graphics.drawString(font, state, x + width - stateWidth - 8, y + 7,
                owned ? 0xFF79CC82 : backpack.vtuberCoins >= product.price() ? 0xFFFFD45A : 0xFFC75450, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int panelWidth = Mth.clamp(width - 40, 300, 460);
            int x = (width - panelWidth) / 2;
            int top = Math.max(20, (height - 276) / 2);
            List<VtuberStoreManager.CatalogEntry> products = filteredProducts();
            int start = page * PAGE_SIZE;
            int end = Math.min(products.size(), start + PAGE_SIZE);
            for (int i = start; i < end; i++) {
                int y = top + 60 + (i - start) * ROW_HEIGHT;
                if (inside(mouseX, mouseY, x, y, panelWidth, ROW_HEIGHT - 2)) {
                    selectedProductId = products.get(i).id();
                    refreshButtons();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void switchTab(Tab next) {
        tab = next;
        page = 0;
        selectedProductId = null;
        refreshButtons();
    }

    private void cycleFilter() {
        filterIndex = (filterIndex + 1) % FILTERS.length;
        page = 0;
        selectedProductId = null;
        filterButton.setMessage(filterLabel());
        refreshButtons();
    }

    private Component filterLabel() {
        return Component.translatable("screen.sre.vtuber_store.filter." + FILTERS[filterIndex]);
    }

    private void changePage(int amount) {
        page = Mth.clamp(page + amount, 0, maxPage(filteredProducts()));
        selectedProductId = null;
        refreshButtons();
    }

    private void purchaseSelected() {
        VtuberStoreManager.CatalogEntry product = selectedProduct();
        if (product != null && canBuy(product, backpack())) {
            ClientPlayNetworking.send(new VtuberStorePurchasePayload(product.id()));
        }
    }

    private void refreshButtons() {
        if (skinTabButton == null) {
            return;
        }
        skinTabButton.active = tab != Tab.SKIN;
        cardTabButton.active = tab != Tab.CARD;
        filterButton.visible = tab == Tab.SKIN;
        List<VtuberStoreManager.CatalogEntry> products = filteredProducts();
        previousButton.active = page > 0;
        nextButton.active = page < maxPage(products);
        VtuberStoreManager.CatalogEntry selected = selectedProduct();
        buyButton.active = selected != null && canBuy(selected, backpack());
    }

    private boolean canBuy(VtuberStoreManager.CatalogEntry product, BackpackState backpack) {
        return product.enabled() && !isOwned(product, backpack) && backpack.vtuberCoins >= product.price();
    }

    private boolean isOwned(VtuberStoreManager.CatalogEntry product, BackpackState backpack) {
        if (!"SKIN".equals(product.kind())) {
            return false;
        }
        boolean storeOwned = backpack.purchasedSkins.contains(
                BackpackManager.storeSkinKey(product.subtype(), product.value()));
        boolean remoteOwned = minecraft != null && minecraft.player != null
                && SREPlayerSkinsComponent.KEY.get(minecraft.player)
                        .isSkinUnlockedForItemType(product.subtype(), product.value());
        boolean economyOwned = minecraft != null && minecraft.player != null
                && PlayerEconomyManager.isSkinUnlockedForItemType(
                        minecraft.player, product.subtype(), product.value());
        return storeOwned || remoteOwned || economyOwned;
    }

    private BackpackState backpack() {
        return minecraft == null || minecraft.player == null
                ? BackpackState.createDefault()
                : ClientPlayerDataCache.backpack(minecraft.player.getUUID()).normalized();
    }

    private VtuberStoreManager.CatalogEntry selectedProduct() {
        if (selectedProductId == null) {
            return null;
        }
        return ClientVtuberStoreCache.products().stream()
                .filter(product -> product.id().equals(selectedProductId)).findFirst().orElse(null);
    }

    private List<VtuberStoreManager.CatalogEntry> filteredProducts() {
        String kind = tab.name();
        String filter = FILTERS[filterIndex];
        return ClientVtuberStoreCache.products().stream()
                .filter(product -> product.kind().equals(kind))
                .filter(product -> tab == Tab.CARD || "all".equals(filter) || product.subtype().equals(filter))
                .toList();
    }

    private static int maxPage(List<?> products) {
        return Math.max(0, (products.size() - 1) / PAGE_SIZE);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xD0050302, 0xF00C0805);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Tab { SKIN, CARD }
}
