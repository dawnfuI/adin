package dev.koifih.client.render;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.koifih.client.render.font.Font;
import dev.koifih.client.render.font.Fonts;
import dev.koifih.client.render.state.FadedTextureState;
import dev.koifih.client.render.state.Pipelines;
import dev.koifih.client.render.state.RectState;
import dev.koifih.client.render.state.StairsState;
import dev.koifih.client.render.state.Submit;
import dev.koifih.client.render.state.TextState;
import dev.koifih.client.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Draw {
    public static final int TOP_LEFT = 1;
    public static final int TOP_RIGHT = 2;
    public static final int BOTTOM_RIGHT = 4;
    public static final int BOTTOM_LEFT = 8;
    public static final int ALL_CORNERS = 15;
    public static final int TILED = 32;

    private static final int FILLET = 16;
    private static final float SMALL_ATLAS_PIXELS = 40f;
    private static final int LOGO_STAR = 1;
    private static final int LOGO_BAND_BACK = 2;
    private static final int LOGO_BAND_FRONT = 3;
    private static final int LOGO_BACK_COLOR = 0xFFA3A3A3;
    private static final int LOGO_FRONT_COLOR = 0xFFEAEAEA;
    private static final int ICON_LAYERS = 3;

    public static void rect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        rect(graphics, x, y, width, height, radius, ALL_CORNERS, color, Pipelines.RECT);
    }

    public static void rect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
                            int corners, int color) {
        rect(graphics, x, y, width, height, radius, corners, color, Pipelines.RECT);
    }

    public static void rect(GuiGraphicsExtractor graphics, float x, float y, float width, int height,
                            int radius, int color) {
        if (width <= 0 || height <= 0) return;
        graphics.pose().pushMatrix();
        try {
            int vertexWidth = (int) Math.ceil(width);
            graphics.pose().translate(x, y).scale(width / vertexWidth, 1f);
            rect(graphics, 0, 0, vertexWidth, height, radius, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static void stairs(GuiGraphicsExtractor graphics, StairsState.Row row, int color) {
        color = Opacity.apply(color);
        if (row.width() <= 0 || row.height() <= 0 || Colors.transparent(color)) return;
        Submit.submit(graphics, new StairsState(new Matrix3x2f(graphics.pose()), row, color));
    }

    public static void gradient(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
                                int corners, int top, int bottom) {
        rect(graphics, x, y, width, height, radius, corners, top, bottom, Pipelines.RECT);
    }

    public static void bordered(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                                float radius, int fill, int border) {
        int r = Math.max(0, Math.round(radius));
        int h = Math.max(1, Math.round(height));
        rect(graphics, x - 1, y - 1, width + 2, h + 2, r + 1, border);
        rect(graphics, x, y, width, h, r, fill);
    }

    public static void hueBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius) {
        rect(graphics, x, y, width, height, radius, ALL_CORNERS, 0xFFFFFFFF, Pipelines.HUE_BAR);
    }

    public static void saturationValue(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                       int radius, int hueColor) {
        rect(graphics, x, y, width, height, radius, ALL_CORNERS, Colors.opaque(hueColor), Pipelines.SATURATION_VALUE);
    }

    public static void icon(GuiGraphicsExtractor graphics, int codepoint, float x, float y, float size, int color) {
        color = Opacity.apply(color);
        if (size <= 0 || !Float.isFinite(size) || Colors.transparent(color)) return;
        Font font = Fonts.MATERIAL_ICONS;
        Font.Glyph glyph = font.glyph(codepoint);
        Font.Bounds plane = glyph.planeBounds();
        if (plane == null || glyph.atlasBounds() == null) return;
        float centerX = x + size * 0.5f;
        float centerY = y + size * 0.5f;
        float halfWidth = (plane.right() - plane.left()) * size * 0.5f;
        float halfHeight = (plane.bottom() - plane.top()) * size * 0.5f;
        Font.Bounds uv = font.uv(glyph);
        TextState.Glyphs glyphs = new TextState.Glyphs(1);
        glyphs.add(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight,
                uv.left(), uv.top(), uv.right(), uv.bottom(), color);
        Submit.submit(graphics, TextState.of(graphics, font.texture(), glyphs));
    }

    public static void fadedTexture(GuiGraphicsExtractor graphics, Identifier texture, float x, float y, float width,
                                    float height, float u0, float fade) {
        int color = Opacity.apply(0xFFFFFFFF);
        if (width <= 0 || height <= 0 || Colors.transparent(color)) return;
        Matrix3x2f pose = new Matrix3x2f(graphics.pose()).translate(x, y).scale(width, height);
        Submit.submit(graphics, new FadedTextureState(pose, TextState.texture(texture), u0, fade, color, Scissor.current()));
    }

    public static void logo(GuiGraphicsExtractor graphics, float x, float y, float size, int starColor) {
        logo(graphics, x, y, size, LOGO_BACK_COLOR, starColor, LOGO_FRONT_COLOR);
    }

    public static void logo(GuiGraphicsExtractor graphics, float x, float y, float size, int back, int star, int front) {
        Font font = atlas(size, Fonts.LOGO_SMALL, Fonts.LOGO);
        TextState.Glyphs glyphs = layers(font, LOGO_BAND_BACK, LOGO_STAR, LOGO_BAND_FRONT, back, star, front, x, y, size);
        if (!glyphs.isEmpty()) Submit.submit(graphics, TextState.of(graphics, font.texture(), glyphs));
    }

    public static void icon(GuiGraphicsExtractor graphics, AdinIcon icon, float x, float y, float size,
                            int back, int hero, int front, int backdrop) {
        Font font = atlas(size, Fonts.ADIN_ICONS_SMALL, Fonts.ADIN_ICONS);
        TextState.Glyphs glyphs = iconLayers(font, icon, x, y, size, back, hero, front);
        if (!glyphs.isEmpty()) Submit.submit(graphics, TextState.icon(graphics, font.texture(), glyphs, backdrop));
    }

    public static void icon(GuiGraphicsExtractor graphics, AdinIcon icon, float x, float y, float size,
                            int back, int hero, int front) {
        Font font = atlas(size, Fonts.ADIN_ICONS_SMALL, Fonts.ADIN_ICONS);
        TextState.Glyphs glyphs = iconLayers(font, icon, x, y, size, back, hero, front);
        if (!glyphs.isEmpty()) Submit.submit(graphics, TextState.of(graphics, font.texture(), glyphs));
    }

    private static TextState.Glyphs iconLayers(Font font, AdinIcon icon, float x, float y, float size,
                                               int back, int hero, int front) {
        int first = icon.ordinal() * ICON_LAYERS + 1;
        return layers(font, first, first + 1, first + 2, back, hero, front, x, y, size);
    }

    private static Font atlas(float size, Font small, Font large) {
        return size * Minecraft.getInstance().getWindow().getGuiScale() <= SMALL_ATLAS_PIXELS ? small : large;
    }

    private static TextState.Glyphs layers(Font font, int back, int middle, int front, int backColor, int middleColor,
                                           int frontColor, float x, float y, float size) {
        TextState.Glyphs glyphs = new TextState.Glyphs(ICON_LAYERS);
        if (size <= 0 || !Float.isFinite(size)) return glyphs;
        layer(glyphs, font, back, x, y, size, backColor);
        layer(glyphs, font, middle, x, y, size, middleColor);
        layer(glyphs, font, front, x, y, size, frontColor);
        return glyphs;
    }

    private static void layer(TextState.Glyphs glyphs, Font font, int codepoint, float x, float y,
                              float size, int color) {
        color = Opacity.apply(color);
        if (Colors.transparent(color)) return;
        Font.Glyph glyph = font.glyph(codepoint);
        Font.Bounds plane = glyph.planeBounds();
        Font.Bounds uv = font.uv(glyph);
        glyphs.add(x + plane.left() * size, y + plane.top() * size, x + plane.right() * size, y + plane.bottom() * size,
                uv.left(), uv.top(), uv.right(), uv.bottom(), color);
    }

    private static void rect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
                             int shape, int color, RenderPipeline pipeline) {
        rect(graphics, x, y, width, height, radius, shape, color, color, pipeline);
    }

    private static void rect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
                             int shape, int color, int bottomColor, RenderPipeline pipeline) {
        color = Opacity.apply(color);
        bottomColor = Opacity.apply(bottomColor);
        if (width <= 0 || height <= 0 || (Colors.transparent(color) && Colors.transparent(bottomColor))) return;
        if (width > Short.MAX_VALUE || height > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Rectangle dimensions exceed the vertex format's range");
        }
        int limit = (shape & FILLET) != 0 ? Math.max(width, height) : Math.min(width, height) / 2;
        Submit.submit(graphics, new RectState(new Matrix3x2f(graphics.pose()), x, y, width, height,
                Math.clamp(radius, 0, limit), shape, color, bottomColor, pipeline));
    }
}
