package dev.koifih.client.module.impl.hud.watermark;

import dev.koifih.client.event.events.HudRenderEvent;
import dev.koifih.client.module.HudModule;
import dev.koifih.client.module.Module;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Opacity;
import dev.koifih.client.render.Scissor;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.Transform;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.PositionSetting.Anchor;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.setting.TextColorSettings;
import dev.koifih.client.ui.Spring;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.ui.Transition.Easing;
import dev.koifih.client.ui.UiScale;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

public final class Watermark extends HudModule {
    private static final float TOP = 6f;
    private static final float IDLE_HEIGHT = 20f;
    private static final float MIN_SIZE = 10f;
    private static final float CORNER = 10f;
    private static final float CORNER_GROWTH = 0.4f;
    private static final float ALPHA = 0.96f;
    private static final float CONTENT_ZOOM = 0.94f;
    private static final float STIFFNESS = 170f;
    private static final float DAMPING = 17f;
    private static final int STATS_RETURN_DELAY = 80;

    private final SliderSetting size = add(new SliderSetting("scale", 100, 50, 200, Measure.PERCENT));
    private final TextColorSettings colors = add(new TextColorSettings());
    private final Spring width = new Spring(0f, STIFFNESS, DAMPING);
    private final Spring height = new Spring(0f, STIFFNESS, DAMPING);
    private final Transition presence = new Transition(0f, 200);
    private final Transition statsShown = new Transition(1f, 300, 160, Easing.EASE_OUT_CUBIC, Easing.EASE_OUT_CUBIC);
    private final Transition cardShown = new Transition(0f, 300, 160, Easing.EASE_OUT_CUBIC, Easing.EASE_OUT_CUBIC);
    private final Stats stats = new Stats();
    private boolean opened;
    private Card card;

    public Watermark() {
        super("watermark", Anchor.CENTER, Anchor.START, 0f, TOP);
    }

    public void showToggle(Module module, boolean enabled) {
        if (module == this) return;
        if (card instanceof ToggleCard toggle && toggle.shows(module)) toggle.update(enabled);
        else show(new ToggleCard(module, enabled));
    }

    public void showAlert(Module source, String message) {
        show(new AlertCard(source.name(), message));
    }

    private void show(Card next) {
        card = next;
        cardShown.set(1f);
        statsShown.set(0f);
    }

    @Override
    protected void onEnable() {
        stats.restart();
        opened = false;
        card = null;
        presence.snap(0f);
        presence.set(1f);
        statsShown.snap(1f);
        cardShown.snap(0f);
        listen(HudRenderEvent.class, this::onHudRender);
    }

    private void onHudRender(HudRenderEvent event) {
        Theme.update();
        float scale = UiScale.current().factor() * size.get() / 100f;
        stats.sample();
        boolean expanded = card != null && !card.expired();
        if (card != null && !expanded && cardShown.target() > 0f) {
            cardShown.set(0f);
            statsShown.set(1f, STATS_RETURN_DELAY);
        }
        float statsWidth = stats.width(scale);
        if (!opened) {
            width.snap(IDLE_HEIGHT * scale);
            height.snap(IDLE_HEIGHT * scale);
            opened = true;
        }
        width.set(expanded ? card.width(scale) : statsWidth);
        height.set((expanded ? Card.HEIGHT : IDLE_HEIGHT) * scale);
        float w = Math.max(width.update(), MIN_SIZE * scale);
        float h = Math.max(height.update(), MIN_SIZE * scale);
        var window = mc.getWindow();
        float x = left(w, window.getGuiScaledWidth());
        float y = top(h, window.getGuiScaledHeight());
        placed(x, y, w, h);
        Text.ColorAt hero = colors.colorAt(scale);
        Opacity.with(presence.value(), () -> drawIsland(event.graphics(), x, y, w, h, scale, statsWidth, hero));
        if (card != null && !expanded && cardShown.value() <= 0f) card = null;
    }

    private void drawIsland(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float scale,
                            float statsWidth, Text.ColorAt hero) {
        float corner = Math.min(Math.min(w, h) * 0.5f, (CORNER + (h / scale - IDLE_HEIGHT) * CORNER_GROWTH) * scale);
        Draw.rect(graphics, x, y, w, Math.round(h), Math.round(corner), Colors.withAlpha(Theme.MAIN, ALPHA));
        float centerX = x + w * 0.5f;
        float centerY = y + h * 0.5f;
        ScreenRectangle area = new ScreenRectangle((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.ceil(w) + 1, (int) Math.ceil(h) + 1);
        Scissor.clip(area, () -> {
            reveal(graphics, statsShown.value(), centerX, centerY,
                    () -> stats.draw(graphics, centerX - statsWidth * 0.5f, centerY, scale, hero));
            Card shown = card;
            if (shown != null) reveal(graphics, cardShown.value(), centerX, centerY,
                    () -> shown.draw(graphics, x, centerY, w, scale, hero));
        });
    }

    private static void reveal(GuiGraphicsExtractor graphics, float amount, float centerX, float centerY, Runnable draw) {
        if (amount <= 0f) return;
        Opacity.with(amount, () -> Transform.scaledAbout(graphics, centerX, centerY,
                CONTENT_ZOOM + (1f - CONTENT_ZOOM) * amount, draw));
    }
}
