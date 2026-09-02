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
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.VtuberStorePurchasePayload;
import io.wifi.starrailexpress.network.VtuberStorePurchaseResultPayload;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import io.wifi.starrailexpress.vtuberstore.VtuberStoreManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class VtuberStoreScreen extends Screen {
    private static final int VISIBLE_ROWS = 2;
    private static final int GRID_COLUMNS = 3;
    private static final int GRID_GAP = 6;
    private static final int ACCENT = 0xFFD4AF37;
    private static final int BORDER = 0xFF72571D;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFFFE7B0;
    private static final int TEXT_OWNED = 0xFF8DFFA2;

    private final Screen parent;
    private Category category = Category.KNIFE;
    private int scrollRow;
    private String selectedProductId;
    private final Button[] categoryButtons = new Button[Category.values().length];
    private Button buyButton;
    private Button backButton;
    private Button dialogConfirmButton;
    private Button dialogCancelButton;
    private DialogState dialogState = DialogState.NONE;
    private String dialogProductId;
    private String dialogMessageKey = "";
    private int dialogCardCount = -1;

    public VtuberStoreScreen(Screen parent) {
        super(Component.translatable("screen.sre.vtuber_store.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Layout l = layout();
        int categoryGap = 6;
        int categoryAreaWidth = l.rightX + l.rightWidth - l.leftX;
        int categoryWidth = (categoryAreaWidth - categoryGap * (categoryButtons.length - 1))
                / categoryButtons.length;
        for (Category value : Category.values()) {
            int index = value.ordinal();
            int buttonX = l.leftX + index * (categoryWidth + categoryGap);
            int buttonWidth = index == categoryButtons.length - 1
                    ? categoryAreaWidth - index * (categoryWidth + categoryGap)
                    : categoryWidth;
            categoryButtons[index] = addRenderableWidget(Button.builder(value.label(),
                    button -> selectCategory(value)).bounds(buttonX, l.tabsY, buttonWidth, 20).build());
        }
        buyButton = addRenderableWidget(Button.builder(Component.translatable("screen.sre.vtuber_store.buy"),
                button -> purchaseSelected()).bounds(l.rightX, l.bottomY, l.rightWidth - 66, 20).build());
        backButton = addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(l.rightX + l.rightWidth - 60, l.bottomY, 60, 20).build());
        int dialogY = height / 2 + 30;
        dialogConfirmButton = addRenderableWidget(Button.builder(Component.translatable("gui.yes"),
                button -> handleDialogConfirm()).bounds(width / 2 - 104, dialogY, 100, 20).build());
        dialogCancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.no"),
                button -> closeDialog()).bounds(width / 2 + 4, dialogY, 100, 20).build());
        dialogConfirmButton.visible = false;
        dialogCancelButton.visible = false;
        ensureSelection();
        refreshButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Layout l = layout();
        graphics.fillGradient(l.panelX, l.panelY, l.panelX + l.panelWidth, l.panelY + l.panelHeight,
                0xF21B1109, 0xF20A0705);
        graphics.renderOutline(l.panelX, l.panelY, l.panelWidth, l.panelHeight, ACCENT);
        graphics.drawCenteredString(font, title, width / 2, l.panelY + 10, TEXT_PRIMARY);

        BackpackState backpack = backpack();
        graphics.drawString(font, Component.translatable("screen.sre.vtuber_store.balance", backpack.vtuberCoins),
                l.leftX, l.panelY + 30, TEXT_SECONDARY, true);

        List<VtuberStoreManager.CatalogEntry> products = filteredProducts();
        scrollRow = Math.min(scrollRow, maxScrollRow(products));
        ensureSelection();
        if (category == Category.INFO) {
            renderInfoPanel(graphics, l);
        } else {
            renderProductGrid(graphics, l, products, mouseX, mouseY, backpack);
            renderPreview(graphics, l, selectedProduct(), backpack, mouseX, mouseY);
        }
        refreshButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderDialog(graphics, mouseX, mouseY, partialTick);
    }

    private void renderInfoPanel(GuiGraphics graphics, Layout l) {
        int panelWidth = l.rightX + l.rightWidth - l.leftX;
        graphics.fill(l.leftX, l.gridY, l.leftX + panelWidth, l.gridY + l.gridHeight, 0xD5120B07);
        graphics.renderOutline(l.leftX, l.gridY, panelWidth, l.gridHeight, BORDER);
        graphics.drawCenteredString(font, Component.translatable("screen.sre.vtuber_store.info.title"),
                l.leftX + panelWidth / 2, l.gridY + 18, TEXT_SECONDARY);
        int textX = l.leftX + 24;
        int textY = l.gridY + 52;
        for (int index = 1; index <= 5; index++) {
            graphics.drawString(font, Component.translatable("screen.sre.vtuber_store.info.line" + index),
                    textX, textY + (index - 1) * 24, index == 1 ? TEXT_PRIMARY : 0xFFE8D8B8, true);
        }
    }

    private void renderDialog(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (dialogState == DialogState.NONE) {
            return;
        }
        int dialogWidth = Math.min(300, width - 32);
        int dialogHeight = 136;
        int x = (width - dialogWidth) / 2;
        int y = (height - dialogHeight) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 1000.0F);
        try {
            graphics.fill(0, 0, width, height, 0xE8000000);
            graphics.fill(x, y, x + dialogWidth, y + dialogHeight, 0xFF1A0F08);
            graphics.renderOutline(x, y, dialogWidth, dialogHeight, ACCENT);

            Component heading = switch (dialogState) {
                case CONFIRM -> Component.translatable("screen.sre.vtuber_store.confirm.title");
                case WAITING -> Component.translatable("screen.sre.vtuber_store.confirm.waiting");
                case SUCCESS -> Component.translatable("screen.sre.vtuber_store.result.success");
                case FAILURE -> Component.translatable("screen.sre.vtuber_store.result.failure");
                default -> Component.empty();
            };
            graphics.drawCenteredString(font, heading, width / 2, y + 14,
                    dialogState == DialogState.FAILURE ? 0xFFFF8C82 : TEXT_SECONDARY);

            VtuberStoreManager.CatalogEntry product = dialogProduct();
            if (product != null) {
                graphics.drawCenteredString(font, Component.translatable(product.translationKey()),
                        width / 2, y + 38, TEXT_PRIMARY);
                if (dialogState == DialogState.CONFIRM) {
                    graphics.drawCenteredString(font,
                            Component.translatable("screen.sre.vtuber_store.confirm.price", product.price()),
                            width / 2, y + 60, TEXT_PRIMARY);
                    if (isCardProduct(product)) {
                        int current = cardCount(product, backpack());
                        String countKey = "ROLE_CHOICE_CARD".equals(product.kind())
                                ? "screen.sre.vtuber_store.confirm.role_choice_count"
                                : "screen.sre.vtuber_store.confirm.card_count";
                        graphics.drawCenteredString(font,
                                Component.translatable(countKey, current, current + 1),
                                width / 2, y + 80, TEXT_SECONDARY);
                    }
                } else if (dialogState == DialogState.SUCCESS && dialogCardCount >= 0) {
                    String countKey = "ROLE_CHOICE_CARD".equals(product.kind())
                            ? "screen.sre.vtuber_store.result.role_choice_count"
                            : "screen.sre.vtuber_store.result.card_count";
                    graphics.drawCenteredString(font,
                            Component.translatable(countKey, dialogCardCount),
                            width / 2, y + 64, TEXT_OWNED);
                } else if (dialogState == DialogState.FAILURE && !dialogMessageKey.isBlank()) {
                    graphics.drawCenteredString(font, Component.translatable(dialogMessageKey),
                            width / 2, y + 64, 0xFFFFB4AC);
                }
            }
            if (dialogConfirmButton.visible) {
                dialogConfirmButton.render(graphics, mouseX, mouseY, partialTick);
            }
            if (dialogCancelButton.visible) {
                dialogCancelButton.render(graphics, mouseX, mouseY, partialTick);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderProductGrid(GuiGraphics graphics, Layout l, List<VtuberStoreManager.CatalogEntry> products,
            int mouseX, int mouseY, BackpackState backpack) {
        int start = scrollRow * GRID_COLUMNS;
        int end = Math.min(products.size(), start + VISIBLE_ROWS * GRID_COLUMNS);
        if (products.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.sre.vtuber_store.empty"),
                    l.leftX + l.leftWidth / 2, l.gridY + l.gridHeight / 2, TEXT_PRIMARY);
            return;
        }
        int cardWidth = (l.leftWidth - GRID_GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS;
        int cardHeight = (l.gridHeight - GRID_GAP) / 2;
        for (int i = start; i < end; i++) {
            int local = i - start;
            int x = l.leftX + local % GRID_COLUMNS * (cardWidth + GRID_GAP);
            int y = l.gridY + local / GRID_COLUMNS * (cardHeight + GRID_GAP);
            renderProductCard(graphics, products.get(i), x, y, cardWidth, cardHeight, mouseX, mouseY, backpack);
        }
        renderScrollbar(graphics, l, products);
    }

    private void renderScrollbar(GuiGraphics graphics, Layout l,
            List<VtuberStoreManager.CatalogEntry> products) {
        int totalRows = Math.max(1, (products.size() + GRID_COLUMNS - 1) / GRID_COLUMNS);
        if (totalRows <= VISIBLE_ROWS) {
            return;
        }
        int trackX = l.leftX + l.leftWidth - 3;
        graphics.fill(trackX, l.gridY, trackX + 2, l.gridY + l.gridHeight, 0xFF3B2A14);
        int thumbHeight = Math.max(14, l.gridHeight * VISIBLE_ROWS / totalRows);
        int travel = l.gridHeight - thumbHeight;
        int thumbY = l.gridY + travel * scrollRow / Math.max(1, totalRows - VISIBLE_ROWS);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, ACCENT);
    }

    private void renderProductCard(GuiGraphics graphics, VtuberStoreManager.CatalogEntry product,
            int x, int y, int width, int height, int mouseX, int mouseY, BackpackState backpack) {
        boolean selected = product.id().equals(selectedProductId);
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        graphics.fill(x, y, x + width, y + height,
                selected ? 0xE04B3012 : hovered ? 0xD0342210 : 0xC81B1109);
        graphics.renderOutline(x, y, width, height, selected ? 0xFFFFD84A : BORDER);

        ItemStack preview = previewStack(product);
        if (!preview.isEmpty()) {
            int rarityColor = rarityColor(product);
            graphics.fill(x + width / 2 - 20, y + 5, x + width / 2 + 20, y + 45, rarityColor);
            graphics.renderOutline(x + width / 2 - 20, y + 5, 40, 40,
                    selected ? 0xFFFFD84A : 0xCCFFFFFF);
            renderScaledItem(graphics, preview, x + width / 2, y + 25, 1.75f);
        } else {
            renderCardIcon(graphics, x + width / 2, y + 24, false);
        }
        graphics.drawCenteredString(font, Component.translatable(product.translationKey()),
                x + width / 2, y + height - 31, product.enabled() ? TEXT_PRIMARY : 0xFFB9AEA0);
        graphics.drawCenteredString(font, productState(product, backpack), x + width / 2, y + height - 17,
                productStateColor(product, backpack));
    }

    private void renderPreview(GuiGraphics graphics, Layout l, VtuberStoreManager.CatalogEntry product,
            BackpackState backpack, int mouseX, int mouseY) {
        graphics.fill(l.rightX, l.gridY, l.rightX + l.rightWidth, l.gridY + l.gridHeight, 0xD5120B07);
        graphics.renderOutline(l.rightX, l.gridY, l.rightWidth, l.gridHeight, BORDER);
        graphics.drawCenteredString(font, Component.translatable("screen.sre.vtuber_store.preview"),
                l.rightX + l.rightWidth / 2, l.gridY + 12, TEXT_SECONDARY);
        if (product == null) {
            return;
        }
        ItemStack preview = previewStack(product);
        if (!preview.isEmpty()) {
            renderPlayerPreview(graphics, l, preview, mouseX, mouseY);
        } else {
            renderCardIcon(graphics, l.rightX + l.rightWidth / 2,
                    l.gridY + l.gridHeight / 2 - 12, true);
        }
        if (!product.rarity().isBlank()) {
            graphics.drawCenteredString(font, rarityLabel(product),
                    l.rightX + l.rightWidth / 2, l.gridY + l.gridHeight - 55, rarityColor(product));
        }
        graphics.drawCenteredString(font, Component.translatable(product.translationKey()),
                l.rightX + l.rightWidth / 2, l.gridY + l.gridHeight - 39, TEXT_PRIMARY);
        graphics.drawCenteredString(font, productState(product, backpack),
                l.rightX + l.rightWidth / 2, l.gridY + l.gridHeight - 23,
                productStateColor(product, backpack));
    }

    private void renderPlayerPreview(GuiGraphics graphics, Layout l, ItemStack preview,
            int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) {
            renderScaledItem(graphics, preview, l.rightX + l.rightWidth / 2,
                    l.gridY + l.gridHeight / 2 - 8, 4.0f);
            return;
        }
        int x1 = l.rightX + 8;
        int y1 = l.gridY + 28;
        int x2 = l.rightX + l.rightWidth - 8;
        int y2 = l.gridY + l.gridHeight - 50;
        int modelScale = Math.max(20, Math.min(x2 - x1, y2 - y1) / 3);
        ItemStack originalMainhand = minecraft.player.getMainHandItem().copy();
        try {
            minecraft.player.setItemSlot(EquipmentSlot.MAINHAND, preview);
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 400.0F);
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                    x1, y1, x2, y2, modelScale, 0.0625F, mouseX, mouseY, minecraft.player);
            graphics.pose().popPose();
        } finally {
            minecraft.player.setItemSlot(EquipmentSlot.MAINHAND, originalMainhand);
        }
    }

    private void renderCardIcon(GuiGraphics graphics, int centerX, int centerY, boolean large) {
        renderScaledItem(graphics, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                centerX, centerY, large ? 4.0F : 1.75F);
    }

    private void renderScaledItem(GuiGraphics graphics, ItemStack stack, int centerX, int centerY, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderFakeItem(stack, -8, -8);
        graphics.pose().popPose();
    }

    private ItemStack previewStack(VtuberStoreManager.CatalogEntry product) {
        if (!"SKIN".equals(product.kind())) {
            return ItemStack.EMPTY;
        }
        Item item = switch (product.subtype()) {
            case "knife" -> TMMItems.KNIFE;
            case "revolver" -> TMMItems.REVOLVER;
            case "bat" -> TMMItems.BAT;
            case "grenade" -> TMMItems.GRENADE;
            default -> null;
        };
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        stack.set(SREDataComponentTypes.SKIN, product.value());
        return stack;
    }

    private Component productState(VtuberStoreManager.CatalogEntry product, BackpackState backpack) {
        if (isCardProduct(product)) {
            int count = cardCount(product, backpack);
            return Component.translatable("screen.sre.vtuber_store.card_count", count)
                    .append(Component.literal("  ·  " + product.price() + " Vtuber Coin"));
        }
        return isOwned(product, backpack)
                ? Component.translatable("screen.sre.vtuber_store.owned")
                : Component.literal(product.price() + " Vtuber Coin");
    }

    private int cardCount(VtuberStoreManager.CatalogEntry product, BackpackState backpack) {
        if ("ROLE_CHOICE_CARD".equals(product.kind())) {
            return backpack.roleChoiceCards;
        }
        return backpack.cards.getOrDefault(FactionCardType.fromString(product.value()), 0);
    }

    private boolean isCardProduct(VtuberStoreManager.CatalogEntry product) {
        return "CARD".equals(product.kind()) || "ROLE_CHOICE_CARD".equals(product.kind());
    }

    private int productStateColor(VtuberStoreManager.CatalogEntry product, BackpackState backpack) {
        if (isOwned(product, backpack)) {
            return TEXT_OWNED;
        }
        return backpack.vtuberCoins >= product.price() ? TEXT_SECONDARY : 0xFFFF8C82;
    }

    private int rarityColor(VtuberStoreManager.CatalogEntry product) {
        return switch (product.rarity()) {
            case "RARE" -> 0xFFAAA6FF;
            case "EPIC" -> 0xFFAA55FF;
            case "LEGENDARY" -> 0xFFFFAA55;
            default -> 0xFFEEEEEE;
        };
    }

    private Component rarityLabel(VtuberStoreManager.CatalogEntry product) {
        return Component.translatable("screen.sre.vtuber_store.rarity." + product.rarity().toLowerCase());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Layout l = layout();
            List<VtuberStoreManager.CatalogEntry> products = filteredProducts();
            int start = scrollRow * GRID_COLUMNS;
            int end = Math.min(products.size(), start + VISIBLE_ROWS * GRID_COLUMNS);
            int cardWidth = (l.leftWidth - GRID_GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS;
            int cardHeight = (l.gridHeight - GRID_GAP) / 2;
            for (int i = start; i < end; i++) {
                int local = i - start;
                int x = l.leftX + local % GRID_COLUMNS * (cardWidth + GRID_GAP);
                int y = l.gridY + local / GRID_COLUMNS * (cardHeight + GRID_GAP);
                if (inside(mouseX, mouseY, x, y, cardWidth, cardHeight)) {
                    selectedProductId = products.get(i).id();
                    refreshButtons();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout l = layout();
        if (dialogState == DialogState.NONE && scrollY != 0.0D
                && inside(mouseX, mouseY, l.leftX, l.gridY, l.leftWidth, l.gridHeight)) {
            int direction = scrollY > 0.0D ? -1 : 1;
            scrollRow = Mth.clamp(scrollRow + direction, 0, maxScrollRow(filteredProducts()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void selectCategory(Category next) {
        category = next;
        scrollRow = 0;
        selectedProductId = null;
        ensureSelection();
        refreshButtons();
    }

    private void ensureSelection() {
        List<VtuberStoreManager.CatalogEntry> products = filteredProducts();
        boolean visible = selectedProductId != null && products.stream()
                .anyMatch(product -> product.id().equals(selectedProductId));
        if (!visible) {
            selectedProductId = products.isEmpty() ? null : products.get(0).id();
        }
    }

    private void purchaseSelected() {
        VtuberStoreManager.CatalogEntry product = selectedProduct();
        if (product != null && canBuy(product, backpack())) {
            dialogProductId = product.id();
            dialogState = DialogState.CONFIRM;
            dialogMessageKey = "";
            dialogCardCount = -1;
            refreshButtons();
        }
    }

    private void confirmPurchase() {
        if (dialogState != DialogState.CONFIRM || dialogProductId == null) {
            return;
        }
        dialogState = DialogState.WAITING;
        refreshButtons();
        ClientPlayNetworking.send(new VtuberStorePurchasePayload(dialogProductId));
    }

    private void handleDialogConfirm() {
        if (dialogState == DialogState.CONFIRM) {
            confirmPurchase();
        } else if (dialogState == DialogState.SUCCESS || dialogState == DialogState.FAILURE) {
            closeDialog();
        }
    }

    private void closeDialog() {
        dialogState = DialogState.NONE;
        dialogProductId = null;
        dialogMessageKey = "";
        dialogCardCount = -1;
        refreshButtons();
    }

    public static void handlePurchaseResult(VtuberStorePurchaseResultPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof VtuberStoreScreen store) {
            store.applyPurchaseResult(payload);
        }
    }

    private void applyPurchaseResult(VtuberStorePurchaseResultPayload payload) {
        if (dialogState != DialogState.WAITING || !payload.productId().equals(dialogProductId)) {
            return;
        }
        BackpackState state = backpack();
        state.vtuberCoins = Math.max(0, payload.balance());
        VtuberStoreManager.CatalogEntry product = dialogProduct();
        if (payload.success() && product != null && "CARD".equals(product.kind()) && payload.cardCount() >= 0) {
            state.cards.put(FactionCardType.fromString(product.value()), payload.cardCount());
        } else if (payload.success() && product != null && "ROLE_CHOICE_CARD".equals(product.kind())
                && payload.cardCount() >= 0) {
            state.roleChoiceCards = payload.cardCount();
        } else if (payload.success() && product != null && "SKIN".equals(product.kind())) {
            state.purchasedSkins.add(BackpackManager.storeSkinKey(product.subtype(), product.value()));
        }
        dialogState = payload.success() ? DialogState.SUCCESS : DialogState.FAILURE;
        dialogMessageKey = payload.messageKey();
        dialogCardCount = payload.cardCount();
        refreshButtons();
    }

    private VtuberStoreManager.CatalogEntry dialogProduct() {
        if (dialogProductId == null) {
            return null;
        }
        return ClientVtuberStoreCache.products().stream()
                .filter(product -> product.id().equals(dialogProductId)).findFirst().orElse(null);
    }

    private void refreshButtons() {
        if (categoryButtons[0] == null) {
            return;
        }
        for (Category value : Category.values()) {
            categoryButtons[value.ordinal()].active = dialogState == DialogState.NONE && category != value;
        }
        List<VtuberStoreManager.CatalogEntry> products = filteredProducts();
        VtuberStoreManager.CatalogEntry selected = selectedProduct();
        buyButton.active = dialogState == DialogState.NONE && selected != null && canBuy(selected, backpack());
        backButton.active = dialogState == DialogState.NONE;
        dialogConfirmButton.visible = dialogState == DialogState.CONFIRM
                || dialogState == DialogState.SUCCESS || dialogState == DialogState.FAILURE;
        dialogCancelButton.visible = dialogState == DialogState.CONFIRM;
        dialogConfirmButton.setMessage(Component.translatable(dialogState == DialogState.CONFIRM ? "gui.yes" : "gui.ok"));
        dialogConfirmButton.setX(dialogState == DialogState.CONFIRM ? width / 2 - 104 : width / 2 - 50);
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
                && PlayerEconomyManager.isSkinUnlockedForItemType(minecraft.player, product.subtype(), product.value());
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
        return ClientVtuberStoreCache.products().stream()
                .filter(product -> category == Category.INFO ? false : category == Category.CARD
                        ? isCardProduct(product)
                        : "SKIN".equals(product.kind()) && category.subtype.equals(product.subtype()))
                .toList();
    }

    private Layout layout() {
        int panelWidth = Mth.clamp(width - 30, 380, 720);
        int panelHeight = Mth.clamp(height - 24, 280, 346);
        int panelX = (width - panelWidth) / 2;
        int panelY = Math.max(8, (height - panelHeight) / 2);
        int leftX = panelX + 12;
        int contentWidth = panelWidth - 24;
        int rightWidth = Math.max(150, contentWidth * 34 / 100);
        int leftWidth = contentWidth - rightWidth - 10;
        int tabsY = panelY + 46;
        int gridY = panelY + 72;
        int bottomY = panelY + panelHeight - 28;
        return new Layout(panelX, panelY, panelWidth, panelHeight, leftX, leftWidth,
                leftX + leftWidth + 10, rightWidth, tabsY, gridY, bottomY - gridY - 8, bottomY);
    }

    private static int maxScrollRow(List<?> products) {
        int totalRows = (products.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
        return Math.max(0, totalRows - VISIBLE_ROWS);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xB0050302, 0xD00C0805);
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

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
            int leftX, int leftWidth, int rightX, int rightWidth,
            int tabsY, int gridY, int gridHeight, int bottomY) {
    }

    private enum Category {
        KNIFE("knife", "screen.sre.vtuber_store.filter.knife"),
        REVOLVER("revolver", "screen.sre.vtuber_store.filter.revolver"),
        GRENADE("grenade", "screen.sre.vtuber_store.filter.grenade"),
        BAT("bat", "screen.sre.vtuber_store.filter.bat"),
        CARD("", "screen.sre.vtuber_store.tab.cards"),
        INFO("", "screen.sre.vtuber_store.tab.info");

        private final String subtype;
        private final String translationKey;

        Category(String subtype, String translationKey) {
            this.subtype = subtype;
            this.translationKey = translationKey;
        }

        private Component label() {
            return Component.translatable(translationKey);
        }
    }

    private enum DialogState { NONE, CONFIRM, WAITING, SUCCESS, FAILURE }
}
