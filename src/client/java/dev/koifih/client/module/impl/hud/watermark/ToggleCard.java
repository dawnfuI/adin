package dev.koifih.client.module.impl.hud.watermark;

import dev.koifih.client.module.Module;
import dev.koifih.client.render.Switch;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.util.Lang;
import dev.koifih.client.util.Time;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class ToggleCard implements Card {
    static final float WIDTH = 196f;

    private static final long SHOW_MILLIS = 2200L;
    private static final float SWITCH_WIDTH = 18f;
    private static final float SWITCH_HEIGHT = 10f;
    private static final float SWITCH_END = 10f;
    private static final int FLIP_MILLIS = 300;
    private static final int FLIP_DELAY = 400;

    private final Module module;
    private final Time.Stopwatch opened = new Time.Stopwatch();
    private final Time.Stopwatch touched = new Time.Stopwatch();
    private final Transition flip;
    private boolean enabled;

    ToggleCard(Module module, boolean enabled) {
        this.module = module;
        this.enabled = enabled;
        flip = new Transition(enabled ? 0f : 1f, FLIP_MILLIS, Transition.Easing.SMOOTHSTEP);
        flip.set(enabled ? 1f : 0f, FLIP_DELAY);
    }

    boolean shows(Module other) {
        return module == other;
    }

    void update(boolean enabled) {
        this.enabled = enabled;
        flip.set(enabled ? 1f : 0f);
        touched.reset();
    }

    @Override
    public float width(float scale) {
        return WIDTH * scale;
    }

    @Override
    public boolean expired() {
        return touched.elapsed(SHOW_MILLIS);
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, float x, float centerY, float width, float scale, Text.ColorAt hero) {
        float textX = Card.chip(graphics, module.category().icon(), x, centerY, scale, opened.seconds(), hero);
        float switchX = x + width - (SWITCH_END + SWITCH_WIDTH) * scale;
        float room = switchX - textX - TEXT_GAP * scale;
        Text.drawCentered(graphics, Text.fit(module.name(), room, TITLE * scale), textX,
                centerY - TITLE_RISE * scale, TITLE * scale, Theme.TEXT);
        String state = Lang.get(enabled ? "notifications.enabled" : "notifications.disabled");
        Text.drawCentered(graphics, state, textX, centerY + DETAIL_DROP * scale, DETAIL * scale, enabled ? Theme.ACCENT : Theme.DIM);
        int switchHeight = Math.max(2, Math.round(SWITCH_HEIGHT * scale));
        Switch.draw(graphics, switchX, centerY - switchHeight * 0.5f, SWITCH_WIDTH * scale, switchHeight,
                flip.value(), (float) Math.sin(Math.PI * (1f - flip.flight())));
    }
}
