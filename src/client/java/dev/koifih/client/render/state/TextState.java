package dev.koifih.client.render.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.koifih.client.render.Scissor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public record TextState(Matrix3x2fc pose, TextureSetup textureSetup, Glyphs glyphs, RenderPipeline pipeline,
                        int backdrop, ScreenRectangle bounds, ScreenRectangle scissorArea) implements GuiElementRenderState {
    private static final Map<AbstractTexture, TextureSetup> SETUPS = new HashMap<>();

    public static final class Glyphs {
        private static final int FLOATS = 8;
        private static final int COLORS = 2;

        private float[] geometry;
        private int[] colors;
        private int count;
        private float left = Float.POSITIVE_INFINITY;
        private float top = Float.POSITIVE_INFINITY;
        private float right = Float.NEGATIVE_INFINITY;
        private float bottom = Float.NEGATIVE_INFINITY;

        public Glyphs(int capacity) {
            geometry = new float[Math.max(1, capacity) * FLOATS];
            colors = new int[Math.max(1, capacity) * COLORS];
        }

        public void add(float left, float top, float right, float bottom, float u0, float v0, float u1, float v1, int color) {
            add(left, top, right, bottom, u0, v0, u1, v1, color, color);
        }

        public void add(float left, float top, float right, float bottom, float u0, float v0, float u1, float v1,
                        int leftColor, int rightColor) {
            if (count * FLOATS == geometry.length) {
                geometry = Arrays.copyOf(geometry, geometry.length * 2);
                colors = Arrays.copyOf(colors, colors.length * 2);
            }
            int at = count * FLOATS;
            geometry[at] = left;
            geometry[at + 1] = top;
            geometry[at + 2] = right;
            geometry[at + 3] = bottom;
            geometry[at + 4] = u0;
            geometry[at + 5] = v0;
            geometry[at + 6] = u1;
            geometry[at + 7] = v1;
            colors[count * COLORS] = leftColor;
            colors[count * COLORS + 1] = rightColor;
            count++;
            this.left = Math.min(this.left, left);
            this.top = Math.min(this.top, top);
            this.right = Math.max(this.right, right);
            this.bottom = Math.max(this.bottom, bottom);
        }

        public boolean isEmpty() {
            return count == 0;
        }
    }

    public static TextState of(GuiGraphicsExtractor graphics, Identifier atlas, Glyphs glyphs) {
        return of(graphics, atlas, glyphs, Pipelines.TEXT, 0);
    }

    public static TextState icon(GuiGraphicsExtractor graphics, Identifier atlas, Glyphs glyphs, int backdrop) {
        return of(graphics, atlas, glyphs, Pipelines.ICON, backdrop);
    }

    private static TextState of(GuiGraphicsExtractor graphics, Identifier atlas, Glyphs glyphs,
                                RenderPipeline pipeline, int backdrop) {
        var pose = new Matrix3x2f(graphics.pose());
        var bounds = new ScreenRectangle((int) Math.floor(glyphs.left), (int) Math.floor(glyphs.top),
                (int) Math.ceil(glyphs.right) - (int) Math.floor(glyphs.left),
                (int) Math.ceil(glyphs.bottom) - (int) Math.floor(glyphs.top)).transformMaxBounds(pose);
        return new TextState(pose, texture(atlas), glyphs, pipeline, backdrop, bounds, Scissor.current());
    }

    public static TextureSetup texture(Identifier id) {
        return SETUPS.computeIfAbsent(Minecraft.getInstance().getTextureManager().getTexture(id), texture ->
                TextureSetup.singleTexture(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
    }

    @Override
    public void buildVertices(VertexConsumer vertices) {
        float[] geometry = glyphs.geometry;
        int[] colors = glyphs.colors;
        for (int i = 0; i < glyphs.count; i++) {
            int at = i * Glyphs.FLOATS;
            int leftColor = colors[i * Glyphs.COLORS];
            int rightColor = colors[i * Glyphs.COLORS + 1];
            vertex(vertices, geometry[at], geometry[at + 1], geometry[at + 4], geometry[at + 5], leftColor);
            vertex(vertices, geometry[at], geometry[at + 3], geometry[at + 4], geometry[at + 7], leftColor);
            vertex(vertices, geometry[at + 2], geometry[at + 3], geometry[at + 6], geometry[at + 7], rightColor);
            vertex(vertices, geometry[at + 2], geometry[at + 1], geometry[at + 6], geometry[at + 5], rightColor);
        }
    }

    private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int color) {
        vertices.addVertexWith2DPose(pose, x, y).setUv(u, v).setColor(color)
                .setUv1(backdrop >> 16 & 0xFF, backdrop >> 8 & 0xFF).setUv2(backdrop & 0xFF, 0);
    }
}
