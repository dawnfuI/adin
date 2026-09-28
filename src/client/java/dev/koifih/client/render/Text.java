package dev.koifih.client.render;

import dev.koifih.client.render.Opacity;
import dev.koifih.client.render.font.Font;
import dev.koifih.client.render.font.Fonts;
import dev.koifih.client.render.state.Submit;
import dev.koifih.client.render.state.TextState;
import dev.koifih.client.util.Colors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Text {
    private static final Font FONT = Fonts.COMFORTAA_BOLD;
    private static final String ELLIPSIS = "...";
    private static final int WRAP_CACHE = 64;
    private static final Map<Wrap, List<String>> WRAPPED = new LinkedHashMap<>(WRAP_CACHE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Wrap, List<String>> eldest) {
            return size() > WRAP_CACHE;
        }
    };

    private record Wrap(String text, float width, float size, int maxLines) {}

    public record Span(String text, int color) {}

    @FunctionalInterface
    public interface ColorAt {
        int at(float x);
    }

    public static float width(String text, float size) {
        float advance = 0;
        int previous = -1;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            i += Character.charCount(codepoint);
            Font.Glyph glyph = FONT.glyph(codepoint);
            advance += FONT.kerning(previous, glyph.unicode()) + glyph.advance();
            previous = glyph.unicode();
        }
        return advance * size;
    }

    private static final Font.Bounds CAP_BOUNDS = FONT.glyph('H').planeBounds();

    public static float centeredBaseline(String text, float size, float centerY) {
        return centerY - (CAP_BOUNDS.top() + CAP_BOUNDS.bottom()) * size * 0.5f;
    }

    public static void draw(GuiGraphicsExtractor graphics, String text, float x, float baseline, float size, int color) {
        drawColored(graphics, text, x, baseline, size, color, null);
    }

    public static void drawCentered(GuiGraphicsExtractor graphics, String text, float x, float centerY,
                                    float size, int color) {
        draw(graphics, text, x, centeredBaseline(text, size, centerY), size, color);
    }

    public static void drawCentered(GuiGraphicsExtractor graphics, String text, float x, float centerY,
                                    float size, ColorAt color) {
        drawColored(graphics, text, x, centeredBaseline(text, size, centerY), size, 0xFFFFFFFF, color);
    }

    public static void drawCenteredX(GuiGraphicsExtractor graphics, String text, float centerX, float centerY,
                                     float size, int color) {
        drawCentered(graphics, text, centerX - width(text, size) / 2, centerY, size, color);
    }

    public static String fit(String text, float width, float size) {
        if (width(text, size) <= width) return text;
        float room = width / size - width(ELLIPSIS, 1f);
        float advance = 0;
        int previous = -1;
        int end = 0;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            Font.Glyph glyph = FONT.glyph(codepoint);
            advance += FONT.kerning(previous, glyph.unicode()) + glyph.advance();
            if (advance > room) break;
            previous = glyph.unicode();
            i += Character.charCount(codepoint);
            end = i;
        }
        return text.substring(0, end) + ELLIPSIS;
    }

    public static List<String> wrap(String text, float width, float size, int maxLines) {
        return WRAPPED.computeIfAbsent(new Wrap(text, width, size, maxLines),
                key -> List.copyOf(lines(key.text(), key.width(), key.size(), key.maxLines())));
    }

    private static List<String> lines(String text, float width, float size, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (line.isEmpty() || width(candidate, size) <= width) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            lines.add(line.toString());
            line.setLength(0);
            line.append(word);
            if (lines.size() == maxLines - 1) break;
        }
        if (lines.size() == maxLines - 1) {
            String rest = text.substring(Math.min(text.length(), String.join(" ", lines).length() + 1));
            lines.add(fit(rest, width, size));
        } else if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    public static void drawFaded(GuiGraphicsExtractor graphics, String text, float x, float baseline, float size,
                                 int color, float transparentX, float opaqueX) {
        float fadeWidth = opaqueX - transparentX;
        if (fadeWidth == 0f) fadeWidth = 0.001f;
        float width = fadeWidth;
        drawColored(graphics, text, x, baseline, size, color,
                glyphX -> Colors.withAlpha(color, Math.clamp((glyphX - transparentX) / width, 0f, 1f)));
    }

    public static void draw(GuiGraphicsExtractor graphics, List<Span> spans, float x, float baseline, float size) {
        if (!Float.isFinite(size) || size <= 0) return;
        TextState.Glyphs glyphs = new TextState.Glyphs(length(spans));
        float cursor = x;
        int previous = -1;
        for (Span span : spans) {
            cursor = append(glyphs, span.text(), span.color(), cursor, previous, baseline, size, null);
            if (!span.text().isEmpty()) previous = FONT.glyph(span.text().codePointBefore(span.text().length())).unicode();
        }
        submit(graphics, glyphs);
    }

    private static void drawColored(GuiGraphicsExtractor graphics, String text, float x, float baseline, float size,
                                    int color, ColorAt colorAt) {
        if (!Float.isFinite(size) || size <= 0) return;
        TextState.Glyphs glyphs = new TextState.Glyphs(text.length());
        append(glyphs, text, color, x, -1, baseline, size, colorAt);
        submit(graphics, glyphs);
    }

    private static float append(TextState.Glyphs glyphs, String text, int spanColor, float cursor, int previous,
                                float baseline, float size, ColorAt colorAt) {
        int color = Opacity.apply(spanColor);
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            i += Character.charCount(codepoint);
            Font.Glyph glyph = FONT.glyph(codepoint);
            cursor += FONT.kerning(previous, glyph.unicode()) * size;
            Font.Bounds plane = glyph.planeBounds();
            if (plane != null && glyph.atlasBounds() != null && !Colors.transparent(color)) {
                Font.Bounds uv = FONT.uv(glyph);
                float left = cursor + plane.left() * size;
                float right = cursor + plane.right() * size;
                int leftColor = colorAt == null ? color : Opacity.apply(colorAt.at(left));
                int rightColor = colorAt == null ? color : Opacity.apply(colorAt.at(right));
                if (!Colors.transparent(leftColor) || !Colors.transparent(rightColor)) {
                    glyphs.add(left, baseline + plane.top() * size, right, baseline + plane.bottom() * size,
                            uv.left(), uv.top(), uv.right(), uv.bottom(), leftColor, rightColor);
                }
            }
            cursor += glyph.advance() * size;
            previous = glyph.unicode();
        }
        return cursor;
    }

    private static void submit(GuiGraphicsExtractor graphics, TextState.Glyphs glyphs) {
        if (!glyphs.isEmpty()) Submit.submit(graphics, TextState.of(graphics, FONT.texture(), glyphs));
    }

    private static int length(List<Span> spans) {
        int length = 0;
        for (Span span : spans) length += span.text().length();
        return length;
    }
}
