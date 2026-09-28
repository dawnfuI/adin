package dev.koifih.client.render.font;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Fonts {
    public static final Font COMFORTAA_BOLD = Font.load("fonts/comfortaa-bold", '?');
    public static final Font MATERIAL_ICONS = Font.load("icons/material", 0xe8b8);
    public static final Font ADIN_ICONS = Font.load("icons/adin", 1);
    public static final Font ADIN_ICONS_SMALL = Font.load("icons/adin_small", 1);
    public static final Font LOGO = Font.load("brand/logo", 1);
    public static final Font LOGO_SMALL = Font.load("brand/logo_small", 1);

    public static final float DISTANCE_RANGE = COMFORTAA_BOLD.distanceRange();

    static {
        for (Font font : new Font[] {MATERIAL_ICONS, LOGO, LOGO_SMALL, ADIN_ICONS, ADIN_ICONS_SMALL}) {
            if (font.distanceRange() != DISTANCE_RANGE) {
                throw new IllegalStateException("Every MSDF atlas must share the text pipeline's distance range");
            }
        }
    }
}
