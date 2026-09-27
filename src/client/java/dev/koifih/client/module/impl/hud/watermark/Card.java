package dev.koifih.client.module.impl.hud.watermark;

import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.Transform;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;

interface Card {
    float HEIGHT = 36f;
    float CHIP = 24f;
    float CHIP_START = 6f;
    float CHIP_ICON = 13f;
    float TEXT_GAP = 7f;
    float TITLE = 7.5f;
    float TITLE_RISE = 4.6f;
    float DETAIL = 6f;
    float DETAIL_DROP = 5.2f;

    float width(float scale);

    boolean expired();

    void draw(GuiGraphicsExtractor graphics, float x, float centerY, float width, float scale, Text.ColorAt hero);

    static float chip(GuiGraphicsExtractor graphics, AdinIcon icon, float x, float centerY, float scale, float since,
                      Text.ColorAt hero) {
        float chip = CHIP * scale;
        float chipX = x + CHIP_START * scale;
        float chipCenterX = chipX + chip * 0.5f;
        Draw.rect(graphics, chipX, centerY - chip * 0.5f, chip, Math.round(chip), Math.round(chip * 0.5f), Theme.ROW);
        float size = CHIP_ICON * scale;
        float pop = Math.max(0f, Easing.EASE_OUT_BACK.over(since, 0.2f, 0.45f));
        int color = hero.at(chipCenterX);
        Transform.scaledAbout(graphics, chipCenterX, centerY, pop, () -> Draw.icon(graphics, icon,
                chipCenterX - size * 0.5f, centerY - size * 0.5f, size, Theme.DIM, color, Theme.TEXT));
        return chipX + chip + TEXT_GAP * scale;
    }
}
