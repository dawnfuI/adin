package dev.koifih.client.module.impl.hud.watermark;

import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Opacity;
import dev.koifih.client.render.SignalBars;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.Transform;
import dev.koifih.client.ui.RollingText;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition.Easing;
import dev.koifih.client.util.Time;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

final class Stats {
    private static final float START = 7f;
    private static final float END = 9f;
    private static final float LOGO = 12.5f;
    private static final float GAP = 8f;
    private static final float DIVIDER = 9f;
    private static final float ICON = 9f;
    private static final float ICON_GAP = 3.5f;
    private static final float VALUE = 7f;
    private static final float SUFFIX = 6f;
    private static final float SUFFIX_GAP = 1.5f;
    private static final float SUFFIX_DROP = 0.4f;
    private static final float SLIDE = 3f;
    private static final int BACK = 0xFFA3A3A3;
    private static final int FRONT = 0xFFEAEAEA;
    private static final float STAR_X = 0.4875f;
    private static final float STAR_Y = 0.495f;
    private static final float NEEDLE_Y = 13.5f / 24f;
    private static final float HEAD_Y = 9.6f / 24f;
    private static final float GAUGE_SWEEP = (float) Math.toRadians(240);
    private static final float GAUGE_FPS = 240f;
    private static final int[] PING_STEPS = {60, 120, 200};
    private static final float NOD_AMPLITUDE = 0.9f / 24f;
    private static final float NOD_FREQUENCY = 6f;
    private static final float NOD_DAMPING = 2.5f;

    private final Minecraft mc = Minecraft.getInstance();
    private final Time.Stopwatch shown = new Time.Stopwatch();
    private final RollingText fps = new RollingText();
    private final RollingText ping = new RollingText();
    private int sampledFps = -1;
    private int sampledPing = -1;

    void restart() {
        shown.reset();
    }

    void sample() {
        int currentFps = mc.getFps();
        if (currentFps != sampledFps) {
            sampledFps = currentFps;
            fps.set(Integer.toString(currentFps));
        }
        int currentPing = latency();
        if (currentPing != sampledPing) {
            sampledPing = currentPing;
            ping.set(Integer.toString(currentPing));
        }
    }

    private int latency() {
        if (mc.player == null || mc.getConnection() == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    float width(float scale) {
        return (START + LOGO + END + 3 * (GAP * 2 + 1f)) * scale + fpsWidth(scale) + pingWidth(scale) + userWidth(scale);
    }

    private float fpsWidth(float scale) {
        return (ICON + ICON_GAP + SUFFIX_GAP) * scale + Text.width(fps.value(), VALUE * scale) + Text.width("fps", SUFFIX * scale);
    }

    private float pingWidth(float scale) {
        return (ICON + ICON_GAP + SUFFIX_GAP) * scale + Text.width(ping.value(), VALUE * scale) + Text.width("ms", SUFFIX * scale);
    }

    private float userWidth(float scale) {
        return (ICON + ICON_GAP) * scale + Text.width(mc.getUser().getName(), VALUE * scale);
    }

    void draw(GuiGraphicsExtractor graphics, float left, float centerY, float scale, Text.ColorAt hero) {
        float elapsed = shown.seconds();
        float x = left + START * scale;
        float logoX = x;
        Opacity.with(appear(elapsed, 0), () -> drawLogo(graphics, logoX, centerY, scale, hero, elapsed));
        x += LOGO * scale;
        float[] widths = {fpsWidth(scale), pingWidth(scale), userWidth(scale)};
        for (int i = 0; i < widths.length; i++) {
            float visible = appear(elapsed, i + 1);
            float dividerX = x + GAP * scale;
            float partX = dividerX + (1f + GAP) * scale + (1f - visible) * SLIDE * scale;
            int part = i;
            Opacity.with(visible, () -> {
                Draw.rect(graphics, dividerX, centerY - DIVIDER * scale * 0.5f, scale, Math.round(DIVIDER * scale), 0, Theme.CONTROL);
                switch (part) {
                    case 0 -> drawFps(graphics, partX, centerY, scale, hero, elapsed);
                    case 1 -> drawPing(graphics, partX, centerY, scale, hero, elapsed);
                    default -> drawUser(graphics, partX, centerY, scale, hero, elapsed);
                }
            });
            x = dividerX + (1f + GAP) * scale + widths[i];
        }
    }

    private void drawLogo(GuiGraphicsExtractor graphics, float x, float centerY, float scale, Text.ColorAt hero, float elapsed) {
        float logo = LOGO * scale;
        float y = centerY - logo * 0.5f;
        float spin = -(1f - Easing.EASE_OUT_CUBIC.over(elapsed, 0.15f, 0.9f)) * (float) (Math.PI * 2);
        float pop = Math.max(0f, Easing.EASE_OUT_BACK.over(elapsed, 0.15f, 0.55f));
        float starX = x + logo * STAR_X;
        float starY = y + logo * STAR_Y;
        int star = hero.at(starX);
        Draw.logo(graphics, x, y, logo, BACK, 0, 0);
        Transform.rotatedAbout(graphics, spin, starX, starY, () -> Transform.scaledAbout(graphics, starX, starY, pop,
                () -> Draw.logo(graphics, x, y, logo, 0, star, 0)));
        Draw.logo(graphics, x, y, logo, 0, 0, FRONT);
    }

    private void drawFps(GuiGraphicsExtractor graphics, float x, float centerY, float scale, Text.ColorAt hero, float elapsed) {
        float icon = ICON * scale;
        float y = centerY - icon * 0.5f;
        float level = Math.min(parse(fps.value()) / GAUGE_FPS, 1f) * Easing.EASE_OUT_BACK.over(elapsed, 0.35f, 0.7f);
        float wobble = 0.012f * (float) Math.sin(elapsed * 9f) + 0.008f * (float) Math.sin(elapsed * 23f);
        float pivotX = x + icon * 0.5f;
        float pivotY = y + icon * NEEDLE_Y;
        int color = hero.at(pivotX);
        Draw.icon(graphics, AdinIcon.FPS, x, y, icon, Theme.DIM, 0, Theme.TEXT);
        Transform.rotatedAbout(graphics, (level + wobble - 0.5f) * GAUGE_SWEEP, pivotX, pivotY,
                () -> Draw.icon(graphics, AdinIcon.FPS, x, y, icon, 0, color, 0));
        suffix(graphics, fps.draw(graphics, x + icon + ICON_GAP * scale, centerY, VALUE * scale, Theme.TEXT), "fps", centerY, scale);
    }

    private void drawPing(GuiGraphicsExtractor graphics, float x, float centerY, float scale, Text.ColorAt hero, float elapsed) {
        float icon = ICON * scale;
        int quality = SignalBars.COUNT;
        for (int step : PING_STEPS) if (parse(ping.value()) >= step) quality--;
        float scan = Math.min(Easing.EASE_OUT_CUBIC.over(elapsed, 0.5f, 0.6f),
                Easing.EASE_OUT_CUBIC.over(ping.secondsSinceChange(), 0f, 0.5f));
        SignalBars.draw(graphics, x, centerY - icon * 0.5f, icon, quality * scan, Theme.DIM, hero.at(x + icon * 0.5f));
        suffix(graphics, ping.draw(graphics, x + icon + ICON_GAP * scale, centerY, VALUE * scale, Theme.TEXT), "ms", centerY, scale);
    }

    private void drawUser(GuiGraphicsExtractor graphics, float x, float centerY, float scale, Text.ColorAt hero, float elapsed) {
        float icon = ICON * scale;
        float y = centerY - icon * 0.5f;
        float pop = Math.max(0f, Easing.EASE_OUT_BACK.over(elapsed, 0.6f, 0.45f));
        float since = Math.max(0f, elapsed - 1f);
        float nod = NOD_AMPLITUDE * icon * (float) (Math.sin(since * NOD_FREQUENCY) * Math.exp(-since * NOD_DAMPING));
        float headX = x + icon * 0.5f;
        float headY = y + icon * HEAD_Y;
        int color = hero.at(headX);
        Draw.icon(graphics, AdinIcon.USER, x, y, icon, Theme.DIM, 0, Theme.TEXT);
        Transform.translated(graphics, 0f, -nod, () -> Transform.scaledAbout(graphics, headX, headY, pop,
                () -> Draw.icon(graphics, AdinIcon.USER, x, y, icon, 0, color, 0)));
        Text.drawCentered(graphics, mc.getUser().getName(), x + icon + ICON_GAP * scale, centerY, VALUE * scale, Theme.TEXT);
    }

    private static void suffix(GuiGraphicsExtractor graphics, float x, String unit, float centerY, float scale) {
        Text.drawCentered(graphics, unit, x + SUFFIX_GAP * scale, centerY + SUFFIX_DROP * scale, SUFFIX * scale, Theme.DIM);
    }

    private static float appear(float elapsed, int index) {
        return Easing.EASE_OUT_CUBIC.over(elapsed, 0.22f + index * 0.07f, 0.35f);
    }

    private static int parse(String value) {
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }
}
