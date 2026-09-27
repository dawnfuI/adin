package dev.koifih.client.module.impl.hud.watermark;

import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Time;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class AlertCard implements Card {
    private static final long SHOW_MILLIS = 2600L;
    private static final float END = 12f;

    private final String title;
    private final String message;
    private final Time.Stopwatch opened = new Time.Stopwatch();

    AlertCard(String title, String message) {
        this.title = title;
        this.message = message;
    }

    @Override
    public float width(float scale) {
        float text = Math.max(Text.width(title, TITLE * scale), Text.width(message, DETAIL * scale));
        return Math.max(ToggleCard.WIDTH * scale, (CHIP_START + CHIP + TEXT_GAP + END) * scale + text);
    }

    @Override
    public boolean expired() {
        return opened.elapsed(SHOW_MILLIS);
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, float x, float centerY, float width, float scale, Text.ColorAt hero) {
        float textX = Card.chip(graphics, AdinIcon.ALERT, x, centerY, scale, opened.seconds(), hero);
        float room = x + width - END * scale - textX;
        Text.drawCentered(graphics, Text.fit(title, room, TITLE * scale), textX,
                centerY - TITLE_RISE * scale, TITLE * scale, Theme.TEXT);
        Text.drawCentered(graphics, Text.fit(message, room, DETAIL * scale), textX,
                centerY + DETAIL_DROP * scale, DETAIL * scale, Theme.ACCENT);
    }
}
