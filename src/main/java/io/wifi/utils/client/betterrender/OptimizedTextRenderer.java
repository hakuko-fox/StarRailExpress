package io.wifi.utils.client.betterrender;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Frame-level text batch renderer with tick-rate computation and optimized batching.
 *
 * <h2>Optimizations:</h2>
 * <ul>
 *   <li>Matrix4f object pooling - reuses matrix instances to reduce GC pressure</li>
 *   <li>Batch rendering - consecutive same-type operations merged into single draw calls</li>
 *   <li>Tick-rate caching - skips recomputation when tick hasn't changed</li>
 *   <li>Pre-allocated lists - avoids list resizing during frame</li>
 * </ul>
 *
 * <h2>Lifecycle (managed by GuiRenderMixin):</h2>
 * <pre>
 *   ClientTickEvent  →  markTickDirty()    ← called every game tick
 *   Gui.render HEAD  →  beginFrame()       ← opens batch window
 *     FakeGuiGraphics →  enqueue(...)      ← only runs if tick is dirty
 *   Gui.render RETURN →  endFrame()        ← submits batched draw calls
 * </pre>
 */
public class OptimizedTextRenderer {

    public static final OptimizedTextRenderer INSTANCE = new OptimizedTextRenderer();

    private OptimizedTextRenderer() {
    }

    // ── Matrix4f Object Pool ───────────────────────────────────────────────────
    
    private static final int MATRIX_POOL_SIZE = 256;
    private final Deque<Matrix4f> matrixPool = new ArrayDeque<>(MATRIX_POOL_SIZE);
    
    private Matrix4f acquireMatrix(Matrix4f source) {
        Matrix4f m = matrixPool.pollFirst();
        if (m == null) {
            m = new Matrix4f(source);
        } else {
            m.set(source);
        }
        return m;
    }
    
    private void releaseMatrix(Matrix4f m) {
        if (matrixPool.size() < MATRIX_POOL_SIZE) {
            matrixPool.offerFirst(m);
        }
    }
    
    private void releaseAllMatrices(List<RenderAction> actions) {
        for (RenderAction action : actions) {
            Matrix4f m = action.getMatrix();
            if (m != null) {
                releaseMatrix(m);
            }
        }
    }

    // ── Tick-rate gate ─────────────────────────────────────────────────────────

    private boolean tickDirty = true;
    private final List<RenderAction> tickCache = new ArrayList<>(128);
    private final List<RenderAction> pending = new ArrayList<>(128);

    private GuiGraphics frameGraphics = null;
    private boolean inFrame = false;

    // ── VBO Cache for static geometry ──────────────────────────────────────────
    
    /** Cached VBO for fill operations - reused when tick hasn't changed */
    private @Nullable VertexBuffer fillVbo = null;
    /** Cached VBO for blit operations grouped by texture */
    private @Nullable VertexBuffer blitVbo = null;
    private @Nullable ResourceLocation cachedBlitTexture = null;
    /** Cached VBO for gradient operations */
    private @Nullable VertexBuffer gradientVbo = null;
    /** Whether VBOs need rebuilding */
    private boolean vboDirty = true;
    
    // Cached geometry data for VBO rebuild
    private final List<FillAction> cachedFills = new ArrayList<>(64);
    private final List<BlitAction> cachedBlits = new ArrayList<>(64);
    private final List<FillGradientAction> cachedGradients = new ArrayList<>(32);

    // ── Tick lifecycle (called by ClientTickMixin) ─────────────────────────────

    public void markTickDirty() {
        tickDirty = true;
        vboDirty = true;
    }

    public boolean isTickDirty() {
        return tickDirty;
    }

    // ── Frame lifecycle (called by GuiRenderMixin) ─────────────────────────────

    public void beginFrame(GuiGraphics graphics) {
        frameGraphics = graphics;
        inFrame = true;
        pending.clear();
    }

    public void endFrame() {
        if (!inFrame)
            return;

        if (tickDirty) {
            // Release matrices from old cache before replacing
            releaseAllMatrices(tickCache);
            tickCache.clear();
            tickCache.addAll(pending);
            tickDirty = false;
            
            // Rebuild geometry caches for VBO
            rebuildGeometryCaches();
        }

        flushCache();

        pending.clear();
        inFrame = false;
        frameGraphics = null;
    }
    
    /**
     * Rebuild geometry caches from tickCache for VBO usage.
     */
    private void rebuildGeometryCaches() {
        cachedFills.clear();
        cachedBlits.clear();
        cachedGradients.clear();
        
        for (RenderAction action : tickCache) {
            if (action instanceof FillAction f) {
                cachedFills.add(f);
            } else if (action instanceof BlitAction b) {
                cachedBlits.add(b);
            } else if (action instanceof FillGradientAction g) {
                cachedGradients.add(g);
            }
        }
        
        vboDirty = true;
    }
    
    /**
     * Build or rebuild VBOs for cached geometry.
     */
    private void rebuildVbosIfNeeded() {
        if (!vboDirty) return;
        
        // Rebuild fill VBO
        if (!cachedFills.isEmpty()) {
            if (fillVbo != null) fillVbo.close();
            fillVbo = buildFillVbo();
        }
        
        // Rebuild gradient VBO
        if (!cachedGradients.isEmpty()) {
            if (gradientVbo != null) gradientVbo.close();
            gradientVbo = buildGradientVbo();
        }
        
        vboDirty = false;
    }
    
    private @Nullable VertexBuffer buildFillVbo() {
        if (cachedFills.isEmpty()) return null;
        
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (FillAction f : cachedFills) {
            Matrix4f m = f.matrix;
            bb.addVertex(m, f.x1, f.y2, f.z).setColor(f.color);
            bb.addVertex(m, f.x2, f.y2, f.z).setColor(f.color);
            bb.addVertex(m, f.x2, f.y1, f.z).setColor(f.color);
            bb.addVertex(m, f.x1, f.y1, f.z).setColor(f.color);
        }
        
        MeshData mesh = bb.build();
        if (mesh == null) return null;
        
        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(mesh);
        VertexBuffer.unbind();
        return vbo;
    }
    
    private @Nullable VertexBuffer buildGradientVbo() {
        if (cachedGradients.isEmpty()) return null;
        
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (FillGradientAction g : cachedGradients) {
            Matrix4f m = g.matrix;
            bb.addVertex(m, g.x2, g.y1, g.z).setColor(g.colorFrom);
            bb.addVertex(m, g.x1, g.y1, g.z).setColor(g.colorFrom);
            bb.addVertex(m, g.x1, g.y2, g.z).setColor(g.colorTo);
            bb.addVertex(m, g.x2, g.y2, g.z).setColor(g.colorTo);
        }
        
        MeshData mesh = bb.build();
        if (mesh == null) return null;
        
        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(mesh);
        VertexBuffer.unbind();
        return vbo;
    }

    private void flushCache() {
        if (frameGraphics == null || tickCache.isEmpty())
            return;

        Font font = Minecraft.getInstance().font;
        MultiBufferSource.BufferSource bufferSource = frameGraphics.bufferSource();
        
        // Rebuild VBOs if geometry changed
        rebuildVbosIfNeeded();
        
        // Track if we've rendered VBO-based geometry
        boolean fillsRendered = false;
        boolean gradientsRendered = false;

        int i = 0;
        int size = tickCache.size();
        while (i < size) {
            RenderAction action = tickCache.get(i);

            // Use VBO for FillActions if available
            if (action instanceof FillAction) {
                if (!fillsRendered && fillVbo != null) {
                    renderFillVbo();
                    fillsRendered = true;
                }
                // Skip all consecutive FillActions
                while (i < size && tickCache.get(i) instanceof FillAction) i++;
                continue;
            }

            // Batch consecutive BlitActions with same texture (no VBO, textures vary)
            if (action instanceof BlitAction) {
                i = flushBatchedBlits(i, size);
                continue;
            }

            // Batch consecutive TextActions
            if (action instanceof TextAction) {
                i = flushBatchedText(i, size, font, bufferSource);
                continue;
            }

            // Use VBO for FillGradientActions if available
            if (action instanceof FillGradientAction) {
                if (!gradientsRendered && gradientVbo != null) {
                    renderGradientVbo();
                    gradientsRendered = true;
                }
                // Skip all consecutive FillGradientActions
                while (i < size && tickCache.get(i) instanceof FillGradientAction) i++;
                continue;
            }

            // Execute other actions individually
            action.execute(frameGraphics, font, bufferSource);
            i++;
        }

        RenderSystem.disableDepthTest();
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
    }
    
    /** Identity matrix for VBO rendering (vertices already transformed) */
    private static final Matrix4f IDENTITY = new Matrix4f();
    
    /**
     * Render cached fill VBO.
     */
    private void renderFillVbo() {
        if (fillVbo == null) return;
        
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        fillVbo.bind();
        fillVbo.drawWithShader(
            IDENTITY,
            RenderSystem.getProjectionMatrix(),
            RenderSystem.getShader()
        );
        VertexBuffer.unbind();
    }
    
    /**
     * Render cached gradient VBO.
     */
    private void renderGradientVbo() {
        if (gradientVbo == null) return;
        
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        gradientVbo.bind();
        gradientVbo.drawWithShader(
            IDENTITY,
            RenderSystem.getProjectionMatrix(),
            RenderSystem.getShader()
        );
        VertexBuffer.unbind();
    }

    /**
     * Batch consecutive BlitActions with the same texture into a single draw call.
     */
    private int flushBatchedBlits(int start, int size) {
        BlitAction first = (BlitAction) tickCache.get(start);
        ResourceLocation tex = first.texture;

        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int i = start;
        while (i < size && tickCache.get(i) instanceof BlitAction b && b.texture.equals(tex)) {
            Matrix4f m = b.matrix;
            bb.addVertex(m, b.x1, b.y2, b.z).setUv(b.u0, b.v1).setColor(b.r, b.g, b.b, b.a);
            bb.addVertex(m, b.x2, b.y2, b.z).setUv(b.u1, b.v1).setColor(b.r, b.g, b.b, b.a);
            bb.addVertex(m, b.x2, b.y1, b.z).setUv(b.u1, b.v0).setColor(b.r, b.g, b.b, b.a);
            bb.addVertex(m, b.x1, b.y1, b.z).setUv(b.u0, b.v0).setColor(b.r, b.g, b.b, b.a);
            i++;
        }

        BufferUploader.drawWithShader(bb.buildOrThrow());
        return i;
    }

    /**
     * Batch consecutive TextActions using shared buffer source.
     */
    private int flushBatchedText(int start, int size, Font font, MultiBufferSource.BufferSource bufferSource) {
        int i = start;
        while (i < size && tickCache.get(i) instanceof TextAction t) {
            if (t.seq != null) {
                font.drawInBatch(t.seq, t.x, t.y, t.color, t.shadow,
                        t.matrix, bufferSource, Font.DisplayMode.NORMAL, 0,
                        LightTexture.FULL_BRIGHT);
            } else if (t.text != null) {
                font.drawInBatch(t.text, t.x, t.y, t.color, t.shadow,
                        t.matrix, bufferSource, Font.DisplayMode.NORMAL, 0,
                        LightTexture.FULL_BRIGHT);
            }
            i++;
        }
        return i;
    }

    // ── Enqueue API (called by FakeGuiGraphics) ────────────────────────────────

    public void enqueue(GuiGraphics graphics, Component text,
            float x, float y, int color, boolean shadow) {
        if (!inFrame) {
            graphics.drawString(Minecraft.getInstance().font, text, (int) x, (int) y, color, shadow);
            return;
        }
        pending.add(new TextAction(null, text, x, y, color, shadow,
                acquireMatrix(graphics.pose().last().pose())));
    }

    public void enqueueSeq(GuiGraphics graphics, FormattedCharSequence seq,
            float x, float y, int color, boolean shadow) {
        if (!inFrame) {
            graphics.drawString(Minecraft.getInstance().font, seq, (int) x, (int) y, color, shadow);
            return;
        }
        pending.add(new TextAction(seq, null, x, y, color, shadow,
                acquireMatrix(graphics.pose().last().pose())));
    }

    public void enqueueBlit(GuiGraphics graphics, ResourceLocation texture,
            int x1, int x2, int y1, int y2, int z,
            float u0, float u1, float v0, float v1,
            float r, float g, float b, float a) {
        if (!inFrame) {
            graphics.innerBlit(texture, x1, x2, y1, y2, z, u0, u1, v0, v1, r, g, b, a);
            return;
        }
        pending.add(new BlitAction(texture,
                x1, x2, y1, y2, z,
                u0, u1, v0, v1,
                r, g, b, a,
                acquireMatrix(graphics.pose().last().pose())));
    }

    public void enqueueFill(GuiGraphics graphics, int x1, int y1, int x2, int y2, int z, int color) {
        if (!inFrame) {
            graphics.fill(x1, y1, x2, y2, z, color);
            return;
        }
        pending.add(new FillAction(x1, y1, x2, y2, z, color,
                acquireMatrix(graphics.pose().last().pose())));
    }

    public void enqueueFillWithRenderType(GuiGraphics graphics, RenderType rt, int x1, int y1, int x2, int y2, int z, int color) {
        if (!inFrame) {
            graphics.fill(rt, x1, y1, x2, y2, z, color);
            return;
        }
        pending.add(new FillRenderTypeAction(rt, x1, y1, x2, y2, z, color));
    }

    public void enqueueFillGradient(GuiGraphics graphics, int x1, int y1, int x2, int y2, int z, int colorFrom, int colorTo) {
        if (!inFrame) {
            graphics.fillGradient(x1, y1, x2, y2, z, colorFrom, colorTo);
            return;
        }
        pending.add(new FillGradientAction(x1, y1, x2, y2, z, colorFrom, colorTo,
                acquireMatrix(graphics.pose().last().pose())));
    }

    public void enqueueFillGradientWithRenderType(GuiGraphics graphics, RenderType rt, int x1, int y1, int x2, int y2, int colorFrom, int colorTo, int z) {
        if (!inFrame) {
            graphics.fillGradient(rt, x1, y1, x2, y2, colorFrom, colorTo, z);
            return;
        }
        pending.add(new FillGradientRenderTypeAction(rt, x1, y1, x2, y2, colorFrom, colorTo, z));
    }

    public void enqueueHLine(GuiGraphics graphics, int x1, int x2, int y, int color) {
        if (!inFrame) {
            graphics.hLine(x1, x2, y, color);
            return;
        }
        pending.add(new HLineAction(null, x1, x2, y, color));
    }

    public void enqueueHLineWithRenderType(GuiGraphics graphics, RenderType rt, int x1, int x2, int y, int color) {
        if (!inFrame) {
            graphics.hLine(rt, x1, x2, y, color);
            return;
        }
        pending.add(new HLineAction(rt, x1, x2, y, color));
    }

    public void enqueueVLine(GuiGraphics graphics, int x, int y1, int y2, int color) {
        if (!inFrame) {
            graphics.vLine(x, y1, y2, color);
            return;
        }
        pending.add(new VLineAction(null, x, y1, y2, color));
    }

    public void enqueueVLineWithRenderType(GuiGraphics graphics, RenderType rt, int x, int y1, int y2, int color) {
        if (!inFrame) {
            graphics.vLine(rt, x, y1, y2, color);
            return;
        }
        pending.add(new VLineAction(rt, x, y1, y2, color));
    }

    public void enqueueRenderOutline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        if (!inFrame) {
            graphics.renderOutline(x, y, w, h, color);
            return;
        }
        pending.add(new RenderOutlineAction(x, y, w, h, color));
    }

    public void enqueueFillRenderType(GuiGraphics graphics, RenderType rt, int x1, int y1, int x2, int y2, int z) {
        if (!inFrame) {
            graphics.fillRenderType(rt, x1, y1, x2, y2, z);
            return;
        }
        pending.add(new FillRenderTypeOnlyAction(rt, x1, y1, x2, y2, z));
    }

    // ── Scissor operations ─────────────────────────────────────────────────────

    public void enqueueEnableScissor(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        if (!inFrame) {
            graphics.enableScissor(x1, y1, x2, y2);
            return;
        }
        pending.add(new EnableScissorAction(x1, y1, x2, y2));
    }

    public void enqueueDisableScissor(GuiGraphics graphics) {
        if (!inFrame) {
            graphics.disableScissor();
            return;
        }
        pending.add(new DisableScissorAction());
    }

    // ── Blit / Sprite operations ───────────────────────────────────────────────

    public void enqueueBlitSprite(GuiGraphics graphics, ResourceLocation loc, int x, int y, int w, int h) {
        if (!inFrame) {
            graphics.blitSprite(loc, x, y, w, h);
            return;
        }
        pending.add(new BlitSpriteAction(loc, x, y, 0, w, h, -1, -1, -1, -1));
    }

    public void enqueueBlitSpriteZ(GuiGraphics graphics, ResourceLocation loc, int x, int y, int z, int w, int h) {
        if (!inFrame) {
            graphics.blitSprite(loc, x, y, z, w, h);
            return;
        }
        pending.add(new BlitSpriteAction(loc, x, y, z, w, h, -1, -1, -1, -1));
    }

    public void enqueueBlitSpriteRegion(GuiGraphics graphics, ResourceLocation loc, int tw, int th, int u, int v, int x, int y, int w, int h) {
        if (!inFrame) {
            graphics.blitSprite(loc, tw, th, u, v, x, y, w, h);
            return;
        }
        pending.add(new BlitSpriteAction(loc, x, y, 0, w, h, tw, th, u, v));
    }

    public void enqueueBlitSpriteRegionZ(GuiGraphics graphics, ResourceLocation loc, int tw, int th, int u, int v, int x, int y, int z, int w, int h) {
        if (!inFrame) {
            graphics.blitSprite(loc, tw, th, u, v, x, y, z, w, h);
            return;
        }
        pending.add(new BlitSpriteAction(loc, x, y, z, w, h, tw, th, u, v));
    }

    public void enqueueBlitTexAtlas(GuiGraphics graphics, int x, int y, int z, int w, int h, TextureAtlasSprite sprite) {
        if (!inFrame) {
            graphics.blit(x, y, z, w, h, sprite);
            return;
        }
        pending.add(new BlitTexAtlasAction(x, y, z, w, h, sprite, 1f, 1f, 1f, 1f));
    }

    public void enqueueBlitTexAtlasColor(GuiGraphics graphics, int x, int y, int z, int w, int h, TextureAtlasSprite sprite, float r, float g, float b, float a) {
        if (!inFrame) {
            graphics.blit(x, y, z, w, h, sprite, r, g, b, a);
            return;
        }
        pending.add(new BlitTexAtlasAction(x, y, z, w, h, sprite, r, g, b, a));
    }

    public void enqueueBlitResource(GuiGraphics graphics, ResourceLocation loc, int x, int y, int z, float u, float v, int w, int h, int tw, int th) {
        if (!inFrame) {
            graphics.blit(loc, x, y, z, u, v, w, h, tw, th);
            return;
        }
        pending.add(new BlitResourceAction(loc, x, y, z, u, v, w, h, w, h, tw, th));
    }

    public void enqueueBlitResourceRegion(GuiGraphics graphics, ResourceLocation loc, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw, int th) {
        if (!inFrame) {
            graphics.blit(loc, x, y, w, h, u, v, rw, rh, tw, th);
            return;
        }
        pending.add(new BlitResourceAction(loc, x, y, 0, u, v, w, h, rw, rh, tw, th));
    }

    public void enqueueBlitResourceSimple(GuiGraphics graphics, ResourceLocation loc, int x, int y, float u, float v, int w, int h, int tw, int th) {
        if (!inFrame) {
            graphics.blit(loc, x, y, u, v, w, h, tw, th);
            return;
        }
        pending.add(new BlitResourceAction(loc, x, y, 0, u, v, w, h, w, h, tw, th));
    }

    // ── Item rendering operations ──────────────────────────────────────────────

    public void enqueueRenderItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (!inFrame) {
            graphics.renderItem(stack, x, y);
            return;
        }
        pending.add(new RenderItemAction(stack.copy(), x, y, 0, 0, null));
    }

    public void enqueueRenderItemSeed(GuiGraphics graphics, ItemStack stack, int x, int y, int seed) {
        if (!inFrame) {
            graphics.renderItem(stack, x, y, seed);
            return;
        }
        pending.add(new RenderItemAction(stack.copy(), x, y, seed, 0, null));
    }

    public void enqueueRenderItemSeedZ(GuiGraphics graphics, ItemStack stack, int x, int y, int seed, int z) {
        if (!inFrame) {
            graphics.renderItem(stack, x, y, seed, z);
            return;
        }
        pending.add(new RenderItemAction(stack.copy(), x, y, seed, z, null));
    }

    public void enqueueRenderItemEntity(GuiGraphics graphics, LivingEntity entity, ItemStack stack, int x, int y, int seed) {
        // Item rendering with entity context cannot be easily cached due to entity state
        // Fall back to direct rendering
        graphics.renderItem(entity, stack, x, y, seed);
    }

    public void enqueueRenderFakeItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (!inFrame) {
            graphics.renderFakeItem(stack, x, y);
            return;
        }
        pending.add(new RenderFakeItemAction(stack.copy(), x, y, 0));
    }

    public void enqueueRenderFakeItemSeed(GuiGraphics graphics, ItemStack stack, int x, int y, int seed) {
        if (!inFrame) {
            graphics.renderFakeItem(stack, x, y, seed);
            return;
        }
        pending.add(new RenderFakeItemAction(stack.copy(), x, y, seed));
    }

    public void enqueueRenderItemDecorations(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, @Nullable String label) {
        if (!inFrame) {
            graphics.renderItemDecorations(font, stack, x, y, label);
            return;
        }
        pending.add(new RenderItemDecorationsAction(stack.copy(), x, y, label));
    }

    // ── Tooltip rendering operations ───────────────────────────────────────────

    public void enqueueRenderTooltipItem(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!inFrame) {
            graphics.renderTooltip(font, stack, x, y);
            return;
        }
        pending.add(new RenderTooltipItemAction(stack.copy(), x, y));
    }

    public void enqueueRenderTooltipLines(GuiGraphics graphics, Font font, List<Component> lines, Optional<TooltipComponent> image, int x, int y) {
        if (!inFrame) {
            graphics.renderTooltip(font, lines, image, x, y);
            return;
        }
        pending.add(new RenderTooltipLinesAction(new ArrayList<>(lines), image, x, y));
    }

    public void enqueueRenderTooltipComponent(GuiGraphics graphics, Font font, Component component, int x, int y) {
        if (!inFrame) {
            graphics.renderTooltip(font, component, x, y);
            return;
        }
        pending.add(new RenderTooltipComponentAction(component, x, y));
    }

    public void enqueueRenderComponentTooltip(GuiGraphics graphics, Font font, List<Component> lines, int x, int y) {
        if (!inFrame) {
            graphics.renderComponentTooltip(font, lines, x, y);
            return;
        }
        pending.add(new RenderComponentTooltipAction(new ArrayList<>(lines), x, y));
    }

    public void enqueueRenderTooltipSeq(GuiGraphics graphics, Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        if (!inFrame) {
            graphics.renderTooltip(font, lines, x, y);
            return;
        }
        pending.add(new RenderTooltipSeqAction(new ArrayList<>(lines), x, y));
    }

    public void enqueueRenderComponentHoverEffect(GuiGraphics graphics, Font font, @Nullable Style style, int x, int y) {
        if (!inFrame) {
            graphics.renderComponentHoverEffect(font, style, x, y);
            return;
        }
        pending.add(new RenderComponentHoverEffectAction(style, x, y));
    }

    public void enqueueSetColor(GuiGraphics graphics, float r, float g, float b, float a) {
        if (!inFrame) {
            graphics.setColor(r, g, b, a);
            return;
        }
        pending.add(new SetColorAction(r, g, b, a));
    }

    @SuppressWarnings("deprecation")
    public void enqueueDrawManaged(GuiGraphics graphics, Runnable runnable) {
        // Managed drawing cannot be cached as it may contain arbitrary state changes
        // Execute directly
        graphics.drawManaged(runnable);
    }

    // ── RenderAction interface ─────────────────────────────────────────────────

    private interface RenderAction {
        void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource);
        /** Returns the matrix for pooling, or null if this action doesn't use a pooled matrix. */
        default @Nullable Matrix4f getMatrix() { return null; }
    }

    // ── Text rendering action ──────────────────────────────────────────────────

    private static final class TextAction implements RenderAction {
        final @Nullable FormattedCharSequence seq;
        final @Nullable Component text;
        final float x, y;
        final int color;
        final boolean shadow;
        final Matrix4f matrix;
        
        TextAction(@Nullable FormattedCharSequence seq, @Nullable Component text,
                   float x, float y, int color, boolean shadow, Matrix4f matrix) {
            this.seq = seq;
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.shadow = shadow;
            this.matrix = matrix;
        }
        
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            // Handled by flushBatchedText
        }
        
        @Override
        public @Nullable Matrix4f getMatrix() { return matrix; }
    }

    // ── Fill action ────────────────────────────────────────────────────────────

    private static final class FillAction implements RenderAction {
        final int x1, y1, x2, y2, z, color;
        final Matrix4f matrix;
        
        FillAction(int x1, int y1, int x2, int y2, int z, int color, Matrix4f matrix) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.z = z;
            this.color = color;
            this.matrix = matrix;
        }
        
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            // Handled by flushBatchedFills
        }
        
        @Override
        public @Nullable Matrix4f getMatrix() { return matrix; }
    }

    private record FillRenderTypeAction(
            RenderType rt, int x1, int y1, int x2, int y2, int z, int color) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.fill(rt, x1, y1, x2, y2, z, color);
        }
    }

    private record FillRenderTypeOnlyAction(
            RenderType rt, int x1, int y1, int x2, int y2, int z) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.fillRenderType(rt, x1, y1, x2, y2, z);
        }
    }

    // ── Fill gradient action ───────────────────────────────────────────────────

    private static final class FillGradientAction implements RenderAction {
        final int x1, y1, x2, y2, z, colorFrom, colorTo;
        final Matrix4f matrix;
        
        FillGradientAction(int x1, int y1, int x2, int y2, int z, int colorFrom, int colorTo, Matrix4f matrix) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.z = z;
            this.colorFrom = colorFrom;
            this.colorTo = colorTo;
            this.matrix = matrix;
        }
        
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            // Handled by flushBatchedGradients
        }
        
        @Override
        public @Nullable Matrix4f getMatrix() { return matrix; }
    }

    private record FillGradientRenderTypeAction(
            RenderType rt, int x1, int y1, int x2, int y2, int colorFrom, int colorTo, int z) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.fillGradient(rt, x1, y1, x2, y2, colorFrom, colorTo, z);
        }
    }

    // ── Line actions ───────────────────────────────────────────────────────────

    private record HLineAction(
            @Nullable RenderType rt, int x1, int x2, int y, int color) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (rt != null) {
                graphics.hLine(rt, x1, x2, y, color);
            } else {
                graphics.hLine(x1, x2, y, color);
            }
        }
    }

    private record VLineAction(
            @Nullable RenderType rt, int x, int y1, int y2, int color) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (rt != null) {
                graphics.vLine(rt, x, y1, y2, color);
            } else {
                graphics.vLine(x, y1, y2, color);
            }
        }
    }

    private record RenderOutlineAction(
            int x, int y, int w, int h, int color) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderOutline(x, y, w, h, color);
        }
    }

    // ── Blit action ────────────────────────────────────────────────────────────

    private static final class BlitAction implements RenderAction {
        final ResourceLocation texture;
        final int x1, x2, y1, y2, z;
        final float u0, u1, v0, v1;
        final float r, g, b, a;
        final Matrix4f matrix;
        
        BlitAction(ResourceLocation texture, int x1, int x2, int y1, int y2, int z,
                   float u0, float u1, float v0, float v1,
                   float r, float g, float b, float a, Matrix4f matrix) {
            this.texture = texture;
            this.x1 = x1;
            this.x2 = x2;
            this.y1 = y1;
            this.y2 = y2;
            this.z = z;
            this.u0 = u0;
            this.u1 = u1;
            this.v0 = v0;
            this.v1 = v1;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.matrix = matrix;
        }
        
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            // Handled by flushBatchedBlits
        }
        
        @Override
        public @Nullable Matrix4f getMatrix() { return matrix; }
    }

    // ── BlitSprite action ──────────────────────────────────────────────────────

    private record BlitSpriteAction(
            ResourceLocation loc, int x, int y, int z, int w, int h,
            int tw, int th, int u, int v) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (tw < 0) {
                if (z != 0) {
                    graphics.blitSprite(loc, x, y, z, w, h);
                } else {
                    graphics.blitSprite(loc, x, y, w, h);
                }
            } else {
                if (z != 0) {
                    graphics.blitSprite(loc, tw, th, u, v, x, y, z, w, h);
                } else {
                    graphics.blitSprite(loc, tw, th, u, v, x, y, w, h);
                }
            }
        }
    }

    // ── BlitTexAtlas action ────────────────────────────────────────────────────

    private record BlitTexAtlasAction(
            int x, int y, int z, int w, int h, TextureAtlasSprite sprite,
            float r, float g, float b, float a) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (r == 1f && g == 1f && b == 1f && a == 1f) {
                graphics.blit(x, y, z, w, h, sprite);
            } else {
                graphics.blit(x, y, z, w, h, sprite, r, g, b, a);
            }
        }
    }

    // ── BlitResource action ────────────────────────────────────────────────────

    private record BlitResourceAction(
            ResourceLocation loc, int x, int y, int z, float u, float v,
            int w, int h, int rw, int rh, int tw, int th) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (z != 0) {
                graphics.blit(loc, x, y, z, u, v, w, h, tw, th);
            } else if (w != rw || h != rh) {
                graphics.blit(loc, x, y, w, h, u, v, rw, rh, tw, th);
            } else {
                graphics.blit(loc, x, y, u, v, w, h, tw, th);
            }
        }
    }

    // ── Scissor actions ────────────────────────────────────────────────────────

    private record EnableScissorAction(int x1, int y1, int x2, int y2) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.enableScissor(x1, y1, x2, y2);
        }
    }

    private record DisableScissorAction() implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.disableScissor();
        }
    }

    // ── Item rendering actions ─────────────────────────────────────────────────

    private record RenderItemAction(
            ItemStack stack, int x, int y, int seed, int z,
            @Nullable LivingEntity entity) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (z != 0) {
                graphics.renderItem(stack, x, y, seed, z);
            } else if (seed != 0) {
                graphics.renderItem(stack, x, y, seed);
            } else {
                graphics.renderItem(stack, x, y);
            }
        }
    }

    private record RenderFakeItemAction(
            ItemStack stack, int x, int y, int seed) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (seed != 0) {
                graphics.renderFakeItem(stack, x, y, seed);
            } else {
                graphics.renderFakeItem(stack, x, y);
            }
        }
    }

    private record RenderItemDecorationsAction(
            ItemStack stack, int x, int y, @Nullable String label) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (label != null) {
                graphics.renderItemDecorations(font, stack, x, y, label);
            } else {
                graphics.renderItemDecorations(font, stack, x, y);
            }
        }
    }

    // ── Tooltip rendering actions ──────────────────────────────────────────────

    private record RenderTooltipItemAction(ItemStack stack, int x, int y) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderTooltip(font, stack, x, y);
        }
    }

    private record RenderTooltipLinesAction(
            List<Component> lines, Optional<TooltipComponent> image, int x, int y) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderTooltip(font, lines, image, x, y);
        }
    }

    private record RenderTooltipComponentAction(Component component, int x, int y) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderTooltip(font, component, x, y);
        }
    }

    private record RenderComponentTooltipAction(List<Component> lines, int x, int y) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderComponentTooltip(font, lines, x, y);
        }
    }

    private record RenderTooltipSeqAction(List<FormattedCharSequence> lines, int x, int y) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderTooltip(font, lines, x, y);
        }
    }

    private record RenderComponentHoverEffectAction(@Nullable Style style, int x, int y) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderComponentHoverEffect(font, style, x, y);
        }
    }

    // ── Set color action ───────────────────────────────────────────────────────

    private record SetColorAction(float r, float g, float b, float a) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.setColor(r, g, b, a);
        }
    }
}