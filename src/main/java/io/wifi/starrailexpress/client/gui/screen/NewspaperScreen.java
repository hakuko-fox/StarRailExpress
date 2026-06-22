package io.wifi.starrailexpress.client.gui.screen; // 替换为你的实际包名

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

/**
 * 报纸 UI —— 支持编辑模式（List<String>）和只读模式（List<Component>）
 * <p>
 * 背景材质：noellesroles:textures/gui/newspaper.png
 * 宽高比 3:2，内边距 10，屏幕外边距 20。
 */
public class NewspaperScreen extends Screen {

    private static final ResourceLocation NEWSPAPER_TEXTURE =
            ResourceLocation.parse("noellesroles:textures/gui/newspaper.png");
    private static final int PADDING = 10;
    private static final int SCREEN_MARGIN = 20;
    private static final int MAX_PAGES = 100;

    private static final Component EDIT_TITLE_LABEL = Component.translatable("book.editTitle");
    private static final Component FINALIZE_WARNING_LABEL = Component.translatable("book.finalizeWarning");
    private static final FormattedCharSequence BLACK_CURSOR =
            FormattedCharSequence.forward("_", Style.EMPTY.withColor(ChatFormatting.BLACK));
    private static final FormattedCharSequence GRAY_CURSOR =
            FormattedCharSequence.forward("_", Style.EMPTY.withColor(ChatFormatting.GRAY));

    // ---------- 核心数据 ----------
    private final boolean editable;                    // true = 可编辑，false = 只读
    private final List<String> stringPages;            // 编辑模式使用
    private final List<Component> componentPages;      // 只读模式使用
    private String title = "";                         // 编辑模式下的标题
    private int currentPage = 0;
    private boolean isModified = false;
    private boolean isSigning = false;                 // 仅编辑模式有效
    private int frameTick = 0;

    // 物品相关（仅当从物品构造时使用）
    @Nullable
    private final Player player;
    @Nullable
    private final ItemStack book;
    @Nullable
    private final InteractionHand hand;

    // 输入辅助（仅编辑模式）
    @Nullable
    private TextFieldHelper pageEdit;
    private final TextFieldHelper titleEdit;

    // 缓存
    @Nullable
    private DisplayCache displayCache;                 // 编辑模式用
    @Nullable
    private List<FormattedCharSequence> cachedPageLines; // 只读模式用
    private int cachedPage = -1;
    // 控件
    private PageButton forwardButton;
    private PageButton backButton;
    private Button doneButton;       // 保存为草稿 / 关闭
    private Button signButton;       // 进入签名模式（仅编辑）
    private Button finalizeButton;   // 确定发布（仅编辑）
    private Button cancelButton;     // 退出签名（仅编辑）

    // 报纸矩形
    private int newspaperX, newspaperY, newspaperWidth, newspaperHeight;
    private int textX, textY, textWidth, textHeight;

    // ==================== 构造函数 ====================

    /**
     * 从物品堆栈自动识别：WRITTEN_BOOK → 只读，WRITABLE_BOOK → 编辑
     */
    public NewspaperScreen(ItemStack book, InteractionHand hand) {
        super(GameNarrator.NO_TITLE);
        this.player = Minecraft.getInstance().player;
        this.book = book;
        this.hand = hand;
        this.titleEdit = new TextFieldHelper(
                () -> this.title,
                s -> this.title = s,
                this::getClipboard,
                this::setClipboard,
                s -> s.length() < 16
        );

        WrittenBookContent written = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (written != null) {
            // 只读模式
            this.editable = false;
            this.stringPages = null;
            this.componentPages = new ArrayList<>();
            for (Filterable<Component> filtered : written.pages()) {
                this.componentPages.add(filtered.raw());
            }
            Component.literal(written.title().filtered().orElse("Unknown"));
            Component.literal(written.author()).withStyle(ChatFormatting.DARK_GRAY);
            this.pageEdit = null;
        } else {
            // 编辑模式（或空书）
            this.editable = true;
            this.componentPages = null;
            this.stringPages = new ArrayList<>();
            WritableBookContent writable = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
            if (writable != null) {
                for (Filterable<String> filtered : writable.pages()) {
                    this.stringPages.add(filtered.raw());
                }
            }
            if (this.stringPages.isEmpty()) {
                this.stringPages.add("");
            }
            this.pageEdit = new TextFieldHelper(
                    this::getCurrentPageText,
                    this::setCurrentPageText,
                    this::getClipboard,
                    this::setClipboard,
                    this::isTextValid
            );
        }
    }

    /**
     * 纯编辑模式：直接传入 String 列表
     */
    public NewspaperScreen(List<String> editablePages) {
        super(GameNarrator.NO_TITLE);
        this.editable = true;
        this.stringPages = new ArrayList<>(editablePages);
        if (this.stringPages.isEmpty()) this.stringPages.add("");
        this.componentPages = null;
        this.player = null;
        this.book = null;
        this.hand = null;
        this.titleEdit = new TextFieldHelper(
                () -> this.title,
                s -> this.title = s,
                this::getClipboard,
                this::setClipboard,
                s -> s.length() < 16
        );
        this.pageEdit = new TextFieldHelper(
                this::getCurrentPageText,
                this::setCurrentPageText,
                this::getClipboard,
                this::setClipboard,
                this::isTextValid
        );
    }

    /**
     * 纯只读模式：传入 Component 列表，以及可选的标题和作者
     */
    public NewspaperScreen(List<Component> readOnlyPages, Component title, Component author) {
        super(GameNarrator.NO_TITLE);
        this.editable = false;
        this.componentPages = new ArrayList<>(readOnlyPages);
        if (this.componentPages.isEmpty()) this.componentPages.add(CommonComponents.EMPTY);
        this.stringPages = null;
        this.player = null;
        this.book = null;
        this.hand = null;
        this.pageEdit = null;
        this.titleEdit = null;
    }

    // ==================== 页面数据访问（仅编辑模式） ====================

    private String getCurrentPageText() {
        if (!editable || stringPages == null) return "";
        return currentPage >= 0 && currentPage < stringPages.size() ? stringPages.get(currentPage) : "";
    }

    private void setCurrentPageText(String text) {
        if (!editable || stringPages == null) return;
        if (currentPage >= 0 && currentPage < stringPages.size()) {
            stringPages.set(currentPage, text);
            isModified = true;
            clearDisplayCache();
        }
    }

    private boolean isTextValid(String text) {
        return text.length() < 1024 && font.wordWrapHeight(text, textWidth) <= textHeight;
    }

    private String getClipboard() {
        return minecraft != null ? TextFieldHelper.getClipboardContents(minecraft) : "";
    }

    private void setClipboard(String text) {
        if (minecraft != null) TextFieldHelper.setClipboardContents(minecraft, text);
    }

    private int getNumPages() {
        return editable ? stringPages.size() : componentPages.size();
    }

    private Component getCurrentPageComponent() {
        if (editable) {
            String s = getCurrentPageText();
            return s.isEmpty() ? CommonComponents.EMPTY : Component.literal(s);
        } else {
            return componentPages.get(currentPage);
        }
    }

    // ==================== 初始化 ====================

    @Override
    protected void init() {
        super.init();
        calculateNewspaperSize();
        createButtons();
        clearDisplayCache();
        updateButtonVisibility();
        if (editable && pageEdit != null) pageEdit.setCursorToEnd();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        this.width = width;
        this.height = height;
        calculateNewspaperSize();
        this.clearWidgets();
        createButtons();
        updateButtonVisibility();
        clearDisplayCache();
    }

    private void calculateNewspaperSize() {
        int availW = width - 2 * SCREEN_MARGIN;
        int availH = height - 2 * SCREEN_MARGIN;
        double ratio = 3.0 / 2.0;
        int w, h;
        if (availW / (double) availH > ratio) {
            h = availH;
            w = (int) (h * ratio);
        } else {
            w = availW;
            h = (int) (w / ratio);
        }
        newspaperWidth = w;
        newspaperHeight = h;
        newspaperX = (width - w) / 2;
        newspaperY = (height - h) / 2;
        textX = newspaperX + PADDING;
        textY = newspaperY + PADDING;
        textWidth = newspaperWidth - 2 * PADDING;
        textHeight = newspaperHeight - 2 * PADDING;
    }

    private void createButtons() {
        int btnW = 23, btnH = 13;
        int btnY = newspaperY + newspaperHeight - PADDING - btnH - 2;

        // 翻页按钮
        this.forwardButton = this.addRenderableWidget(
                new PageButton(newspaperX + newspaperWidth - PADDING - btnW, btnY, true,
                        b -> pageForward(), true));
        this.backButton = this.addRenderableWidget(
                new PageButton(newspaperX + PADDING, btnY, false,
                        b -> pageBack(), true));

        int bottomY = Math.min(newspaperY + newspaperHeight + 10, height - 30);
        int btnWidth = 98, spacing = 4;
        int totalWidth = btnWidth * 2 + spacing;
        int startX = (width - totalWidth) / 2;

        if (editable) {
            // 完成（保存为草稿）
            this.doneButton = this.addRenderableWidget(
                    Button.builder(CommonComponents.GUI_DONE,
                            b -> { saveChanges(false); onClose(); })
                            .bounds(startX, bottomY, btnWidth, 20).build()
            );
            // 签名
            this.signButton = this.addRenderableWidget(
                    Button.builder(Component.translatable("book.signButton"),
                            b -> { isSigning = true; updateButtonVisibility(); })
                            .bounds(startX + btnWidth + spacing, bottomY, btnWidth, 20).build()
            );
            // 最终确定（发布）
            this.finalizeButton = this.addRenderableWidget(
                    Button.builder(Component.translatable("book.finalizeButton"),
                            b -> { if (isSigning) { saveChanges(true); onClose(); } })
                            .bounds(startX, bottomY, btnWidth, 20).build()
            );
            // 取消签名
            this.cancelButton = this.addRenderableWidget(
                    Button.builder(CommonComponents.GUI_CANCEL,
                            b -> { isSigning = false; updateButtonVisibility(); clearDisplayCache(); })
                            .bounds(startX + btnWidth + spacing, bottomY, btnWidth, 20).build()
            );
        } else {
            // 只读模式：仅关闭按钮
            this.doneButton = this.addRenderableWidget(
                    Button.builder(CommonComponents.GUI_OK,
                            b -> onClose())
                            .bounds(startX + (totalWidth - btnWidth) / 2, bottomY, btnWidth, 20).build()
            );
            this.signButton = null;
            this.finalizeButton = null;
            this.cancelButton = null;
        }
        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        this.backButton.visible = currentPage > 0;
        if (editable) {
            this.forwardButton.visible = currentPage < getNumPages() - 1 || getNumPages() < MAX_PAGES;
        } else {
            this.forwardButton.visible = currentPage < getNumPages() - 1;
        }

        if (!editable) {
            if (doneButton != null) doneButton.visible = true;
            return;
        }

        // 编辑模式按钮切换
        doneButton.visible = !isSigning;
        signButton.visible = !isSigning;
        cancelButton.visible = isSigning;
        finalizeButton.visible = isSigning;
        finalizeButton.active = !StringUtil.isBlank(title);
    }

    // ==================== 翻页 ====================

    private void pageForward() {
        if (currentPage < getNumPages() - 1) {
            currentPage++;
        } else if (editable && getNumPages() < MAX_PAGES) {
            stringPages.add("");
            currentPage++;
            isModified = true;
        }
        updateButtonVisibility();
        clearDisplayCacheAfterPageChange();
    }

    private void pageBack() {
        if (currentPage > 0) {
            currentPage--;
            updateButtonVisibility();
            clearDisplayCacheAfterPageChange();
        }
    }

    private void clearDisplayCache() {
        displayCache = null;
        cachedPage = -1;
        cachedPageLines = null;
    }

    private void clearDisplayCacheAfterPageChange() {
        if (editable && pageEdit != null) pageEdit.setCursorToEnd();
        clearDisplayCache();
    }

    // ==================== 保存 ====================

    private void eraseEmptyTrailingPages() {
        if (!editable) return;
        ListIterator<String> it = stringPages.listIterator(stringPages.size());
        while (it.hasPrevious() && it.previous().isEmpty()) {
            it.remove();
        }
    }

    private void saveChanges(boolean sign) {
        if (!editable) return;
        if (!isModified && !sign) return;

        eraseEmptyTrailingPages();

        // 发送网络包（如果有物品和玩家）
        if (book != null && player != null && hand != null) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
            Optional<String> titleOpt = sign ? Optional.of(title.trim()) : Optional.empty();
            minecraft.getConnection().send(new ServerboundEditBookPacket(slot, stringPages, titleOpt));
        }
    }

    // ==================== 渲染 ====================

    @Override
    public void tick() {
        super.tick();
        frameTick++;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.blit(NEWSPAPER_TEXTURE,
                newspaperX, newspaperY, newspaperWidth, newspaperHeight,
                0, 0, 192, 192, 192, 192);

        if (editable && isSigning) {
            renderSigning(guiGraphics);
        } else {
            renderContent(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
    }

    private void renderSigning(GuiGraphics guiGraphics) {
        boolean blink = (frameTick / 6) % 2 == 0;
        FormattedCharSequence titleSeq = FormattedCharSequence.composite(
                FormattedCharSequence.forward(this.title, Style.EMPTY),
                blink ? BLACK_CURSOR : GRAY_CURSOR
        );
        int centerX = newspaperX + newspaperWidth / 2;

        guiGraphics.drawString(font, EDIT_TITLE_LABEL,
                centerX - font.width(EDIT_TITLE_LABEL) / 2, newspaperY + 30, 0, false);
        guiGraphics.drawString(font, titleSeq,
                centerX - font.width(titleSeq) / 2, newspaperY + 46, 0, false);
        Component authorText = (player != null) ?
                Component.translatable("book.byAuthor", player.getName()).withStyle(ChatFormatting.DARK_GRAY) :
                CommonComponents.EMPTY;
        guiGraphics.drawString(font, authorText,
                centerX - font.width(authorText) / 2, newspaperY + 58, 0, false);
        guiGraphics.drawWordWrap(font, FINALIZE_WARNING_LABEL,
                newspaperX + PADDING, newspaperY + 76, textWidth, 0);
    }

    private void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 页码
        String pageIndicator = (currentPage + 1) + "/" + Math.max(getNumPages(), 1);
        Component msg = Component.literal(pageIndicator);
        guiGraphics.drawString(font, msg,
                newspaperX + newspaperWidth - PADDING - font.width(msg),
                newspaperY + PADDING - 10, 0, false);

        if (editable) {
            // ---- 编辑模式渲染 ----
            DisplayCache cache = getDisplayCache();
            if (cache != null) {
                for (LineInfo line : cache.lines) {
                    guiGraphics.drawString(font, line.asComponent, line.x, line.y, 0x000000, false);
                }
                renderHighlight(guiGraphics, cache.selection);
                renderCursor(guiGraphics, cache.cursor, cache.cursorAtEnd);
            }
        } else {
            // ---- 只读模式渲染 ----
            List<FormattedCharSequence> lines = getCachedPageLines();
            for (int i = 0; i < lines.size(); i++) {
                FormattedCharSequence seq = lines.get(i);
                guiGraphics.drawString(font, seq, textX, textY + i * 9, 0x000000, false);
            }
            // 悬停效果
            Style hoverStyle = getClickedComponentStyleAt(mouseX, mouseY);
            if (hoverStyle != null) {
                guiGraphics.renderComponentHoverEffect(font, hoverStyle, mouseX, mouseY);
            }
        }
    }

    // ==================== 光标与高亮（编辑模式） ====================

    private void renderCursor(GuiGraphics guiGraphics, Pos2i localPos, boolean atEnd) {
        if ((frameTick / 6) % 2 == 0) {
            Pos2i screenPos = convertLocalToScreen(localPos);
            if (!atEnd) {
                guiGraphics.fill(screenPos.x, screenPos.y - 1,
                        screenPos.x + 1, screenPos.y + 9, 0xFF000000);
            } else {
                guiGraphics.drawString(font, "_", screenPos.x, screenPos.y, 0, false);
            }
        }
    }

    private void renderHighlight(GuiGraphics guiGraphics, Rect2i[] highlights) {
        for (Rect2i rect : highlights) {
            guiGraphics.fill(rect.getX(), rect.getY(),
                    rect.getX() + rect.getWidth(), rect.getY() + rect.getHeight(),
                    0xFF0000FF);
        }
    }

    // ==================== 显示缓存（编辑模式） ====================

    private DisplayCache getDisplayCache() {
        if (displayCache == null) {
            displayCache = rebuildDisplayCache();
        }
        return displayCache;
    }

    private DisplayCache rebuildDisplayCache() {
        String text = getCurrentPageText();
        if (text.isEmpty()) return DisplayCache.EMPTY;

        int cursorPos = 0, selectionPos = 0;
        if (pageEdit != null) {
            cursorPos = pageEdit.getCursorPos();
            selectionPos = pageEdit.getSelectionPos();
        }

        IntArrayList lineStarts = new IntArrayList();
        List<LineInfo> lines = new ArrayList<>();
        MutableInt lineIndex = new MutableInt();
        MutableBoolean endsWithNewline = new MutableBoolean();

        StringSplitter splitter = font.getSplitter();
        splitter.splitLines(text, textWidth, Style.EMPTY, true,
                (style, start, end) -> {
                    int idx = lineIndex.getAndIncrement();
                    String segment = text.substring(start, end);
                    endsWithNewline.setValue(segment.endsWith("\n"));
                    String trimmed = StringUtils.stripEnd(segment, " \n");
                    int y = textY + idx * 9;
                    lineStarts.add(start);
                    lines.add(new LineInfo(style, trimmed, textX, y));
                });

        int[] starts = lineStarts.toIntArray();
        boolean cursorAtEnd = cursorPos == text.length();
        Pos2i cursorLocal;
        if (cursorAtEnd && endsWithNewline.isTrue()) {
            cursorLocal = new Pos2i(0, lines.size() * 9);
        } else {
            int lineIdx = findLineFromPos(starts, cursorPos);
            int lineStart = starts[lineIdx];
            String beforeCursor = text.substring(lineStart, cursorPos);
            int xOff = font.width(beforeCursor);
            cursorLocal = new Pos2i(xOff, lineIdx * 9);
        }

        List<Rect2i> highlightRects = new ArrayList<>();
        if (cursorPos != selectionPos) {
            int min = Math.min(cursorPos, selectionPos);
            int max = Math.max(cursorPos, selectionPos);
            int startLine = findLineFromPos(starts, min);
            int endLine = findLineFromPos(starts, max);

            if (startLine == endLine) {
                highlightRects.add(createPartialLineSelection(text, splitter, min, max,
                        startLine * 9, starts[startLine]));
            } else {
                int firstEnd = (startLine + 1 < starts.length) ? starts[startLine + 1] : text.length();
                highlightRects.add(createPartialLineSelection(text, splitter, min, firstEnd,
                        startLine * 9, starts[startLine]));
                for (int i = startLine + 1; i < endLine; i++) {
                    String content = text.substring(starts[i], starts[i + 1]);
                    int width = (int) splitter.stringWidth(content);
                    highlightRects.add(createSelection(new Pos2i(0, i * 9),
                            new Pos2i(width, i * 9 + 9)));
                }
                highlightRects.add(createPartialLineSelection(text, splitter, starts[endLine], max,
                        endLine * 9, starts[endLine]));
            }
        }

        return new DisplayCache(text, cursorLocal, cursorAtEnd, starts,
                lines.toArray(new LineInfo[0]), highlightRects.toArray(new Rect2i[0]));
    }

    private Rect2i createPartialLineSelection(String text, StringSplitter splitter,
                                              int from, int to, int yOffset, int lineStart) {
        String before = text.substring(lineStart, from);
        String between = text.substring(lineStart, to);
        int xStart = (int) splitter.stringWidth(before);
        int xEnd = (int) splitter.stringWidth(between);
        return createSelection(new Pos2i(xStart, yOffset), new Pos2i(xEnd, yOffset + 9));
    }

    private Rect2i createSelection(Pos2i localStart, Pos2i localEnd) {
        Pos2i screenStart = convertLocalToScreen(localStart);
        Pos2i screenEnd = convertLocalToScreen(localEnd);
        int x1 = Math.min(screenStart.x, screenEnd.x);
        int x2 = Math.max(screenStart.x, screenEnd.x);
        int y1 = Math.min(screenStart.y, screenEnd.y);
        int y2 = Math.max(screenStart.y, screenEnd.y);
        return new Rect2i(x1, y1, x2 - x1, y2 - y1);
    }

    private static int findLineFromPos(int[] lineStarts, int pos) {
        int idx = Arrays.binarySearch(lineStarts, pos);
        return idx < 0 ? -idx - 2 : idx;
    }

    private Pos2i convertLocalToScreen(Pos2i local) {
        return new Pos2i(textX + local.x, textY + local.y);
    }

    private Pos2i convertScreenToLocal(Pos2i screen) {
        return new Pos2i(screen.x - textX, screen.y - textY);
    }

    // ==================== 只读模式缓存与点击检测 ====================

    private List<FormattedCharSequence> getCachedPageLines() {
        if (cachedPage != currentPage || cachedPageLines == null) {
            Component comp = getCurrentPageComponent();
            cachedPageLines = font.split(comp, textWidth);
            cachedPage = currentPage;
        }
        return cachedPageLines;
    }

    @Nullable
    private Style getClickedComponentStyleAt(int mouseX, int mouseY) {
        if (editable) return null;
        List<FormattedCharSequence> lines = getCachedPageLines();
        if (lines.isEmpty()) return null;

        int x = mouseX - textX;
        int y = mouseY - textY;
        if (x < 0 || y < 0) return null;

        int lineIndex = y / 9;
        if (lineIndex >= lines.size()) return null;
        FormattedCharSequence line = lines.get(lineIndex);

        // 检查点击位置是否在行内
        int lineWidth = font.width(line);
        if (x > lineWidth) return null;

        // 获取该位置样式
        return font.getSplitter().componentStyleAtWidth(line, x);
    }

    // ==================== 键盘事件 ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;

        if (keyCode == 266) { backButton.onPress(); return true; }
        if (keyCode == 267) { forwardButton.onPress(); return true; }

        if (!editable) return false;

        if (isSigning) {
            return titleKeyPressed(keyCode);
        }

        if (pageEdit == null) return false;

        if (Screen.isSelectAll(keyCode)) { pageEdit.selectAll(); return true; }
        if (Screen.isCopy(keyCode))     { pageEdit.copy(); return true; }
        if (Screen.isPaste(keyCode))    { pageEdit.paste(); return true; }
        if (Screen.isCut(keyCode))      { pageEdit.cut(); return true; }

        TextFieldHelper.CursorStep step = Screen.hasControlDown() ?
                TextFieldHelper.CursorStep.WORD : TextFieldHelper.CursorStep.CHARACTER;
        switch (keyCode) {
            case 257, 335 -> { pageEdit.insertText("\n"); clearDisplayCache(); return true; }
            case 259     -> { pageEdit.removeFromCursor(-1, step); clearDisplayCache(); return true; }
            case 261     -> { pageEdit.removeFromCursor(1, step); clearDisplayCache(); return true; }
            case 262     -> { pageEdit.moveBy(1, Screen.hasShiftDown(), step); clearDisplayCache(); return true; }
            case 263     -> { pageEdit.moveBy(-1, Screen.hasShiftDown(), step); clearDisplayCache(); return true; }
            case 264     -> { keyDown(); return true; }
            case 265     -> { keyUp(); return true; }
            case 268     -> { keyHome(); return true; }
            case 269     -> { keyEnd(); return true; }
            default -> { return false; }
        }
    }

    private void keyUp()   { changeLine(-1); }
    private void keyDown() { changeLine(1); }

    private void changeLine(int delta) {
        int pos = pageEdit.getCursorPos();
        int newPos = getDisplayCache().changeLine(pos, delta);
        pageEdit.setCursorPos(newPos, Screen.hasShiftDown());
        clearDisplayCache();
    }

    private void keyHome() {
        if (Screen.hasControlDown()) pageEdit.setCursorToStart(Screen.hasShiftDown());
        else {
            int pos = pageEdit.getCursorPos();
            int lineStart = getDisplayCache().findLineStart(pos);
            pageEdit.setCursorPos(lineStart, Screen.hasShiftDown());
        }
        clearDisplayCache();
    }

    private void keyEnd() {
        if (Screen.hasControlDown()) pageEdit.setCursorToEnd(Screen.hasShiftDown());
        else {
            int pos = pageEdit.getCursorPos();
            int lineEnd = getDisplayCache().findLineEnd(pos);
            pageEdit.setCursorPos(lineEnd, Screen.hasShiftDown());
        }
        clearDisplayCache();
    }

    private boolean titleKeyPressed(int keyCode) {
        if (keyCode == 257 || keyCode == 335) {
            if (!StringUtil.isBlank(title)) {
                saveChanges(true);
                onClose();
            }
            return true;
        } else if (keyCode == 259) {
            titleEdit.removeCharsFromCursor(-1);
            updateButtonVisibility();
            isModified = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (super.charTyped(codePoint, modifiers)) return true;
        if (!editable) return false;

        if (isSigning) {
            if (titleEdit.charTyped(codePoint)) {
                updateButtonVisibility();
                isModified = true;
                return true;
            }
            return false;
        }

        if (pageEdit == null) return false;
        if (StringUtil.isAllowedChatCharacter(codePoint)) {
            pageEdit.insertText(Character.toString(codePoint));
            clearDisplayCache();
            return true;
        }
        return false;
    }

    // ==================== 鼠标事件 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // 只读模式处理组件点击
        if (!editable) {
            if (button == 0) {
                Style style = getClickedComponentStyleAt((int) mouseX, (int) mouseY);
                if (style != null && handleComponentClicked(style)) {
                    return true;
                }
            }
            return false;
        }

        // 编辑模式：点击文字区域移动光标
        if (button == 0 && !isSigning && pageEdit != null) {
            Pos2i screen = new Pos2i((int) mouseX, (int) mouseY);
            Pos2i local = convertScreenToLocal(screen);
            if (local.x >= 0 && local.x <= textWidth && local.y >= 0 && local.y <= textHeight) {
                int index = getDisplayCache().getIndexAtPosition(font, local);
                if (index >= 0) {
                    pageEdit.setCursorPos(index, Screen.hasShiftDown());
                    clearDisplayCache();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        if (!editable || isSigning || pageEdit == null) return false;
        if (button == 0) {
            Pos2i screen = new Pos2i((int) mouseX, (int) mouseY);
            Pos2i local = convertScreenToLocal(screen);
            if (local.x >= 0 && local.x <= textWidth && local.y >= 0 && local.y <= textHeight) {
                int index = getDisplayCache().getIndexAtPosition(font, local);
                if (index >= 0) {
                    pageEdit.setCursorPos(index, true);
                    clearDisplayCache();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean handleComponentClicked(Style style) {
        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null) return false;
        if (clickEvent.getAction() == ClickEvent.Action.CHANGE_PAGE) {
            try {
                int page = Integer.parseInt(clickEvent.getValue()) - 1;
                if (page >= 0 && page < getNumPages()) {
                    currentPage = page;
                    updateButtonVisibility();
                    clearDisplayCache();
                    return true;
                }
            } catch (NumberFormatException ignored) {}
            return false;
        }
        return super.handleComponentClicked(style);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 内部辅助类 ====================

    static class Pos2i {
        public final int x, y;
        Pos2i(int x, int y) { this.x = x; this.y = y; }
    }

    static class LineInfo {
        final Style style;
        final String contents;
        final Component asComponent;
        final int x, y;
        LineInfo(Style style, String contents, int x, int y) {
            this.style = style;
            this.contents = contents;
            this.x = x;
            this.y = y;
            this.asComponent = Component.literal(contents).setStyle(style);
        }
    }

    static class DisplayCache {
        static final DisplayCache EMPTY = new DisplayCache("",
                new Pos2i(0, 0), true,
                new int[]{0},
                new LineInfo[]{new LineInfo(Style.EMPTY, "", 0, 0)},
                new Rect2i[0]);

        private final String fullText;
        final Pos2i cursor;
        final boolean cursorAtEnd;
        final int[] lineStarts;
        final LineInfo[] lines;
        final Rect2i[] selection;

        DisplayCache(String fullText, Pos2i cursor, boolean cursorAtEnd,
                     int[] lineStarts, LineInfo[] lines, Rect2i[] selection) {
            this.fullText = fullText;
            this.cursor = cursor;
            this.cursorAtEnd = cursorAtEnd;
            this.lineStarts = lineStarts;
            this.lines = lines;
            this.selection = selection;
        }

        public int getIndexAtPosition(Font font, Pos2i local) {
            int lineIdx = local.y / 9;
            if (lineIdx < 0) return 0;
            if (lineIdx >= lines.length) return fullText.length();
            LineInfo line = lines[lineIdx];
            int start = lineStarts[lineIdx];
            int x = local.x;
            int indexInLine = font.getSplitter().plainIndexAtWidth(line.contents, x, line.style);
            indexInLine = Math.max(0, Math.min(indexInLine, line.contents.length()));
            return start + indexInLine;
        }

        public int changeLine(int pos, int delta) {
            int lineIdx = findLineFromPos(lineStarts, pos);
            int newLineIdx = lineIdx + delta;
            if (newLineIdx < 0 || newLineIdx >= lines.length) return pos;
            int newLineStart = lineStarts[newLineIdx];
            int offset = pos - lineStarts[lineIdx];
            int newOffset = Math.min(offset, lines[newLineIdx].contents.length());
            return newLineStart + newOffset;
        }

        public int findLineStart(int pos) {
            int idx = findLineFromPos(lineStarts, pos);
            return lineStarts[idx];
        }

        public int findLineEnd(int pos) {
            int idx = findLineFromPos(lineStarts, pos);
            return (idx + 1 < lineStarts.length) ? lineStarts[idx + 1] : fullText.length();
        }
    }
}