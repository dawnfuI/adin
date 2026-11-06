package dev.koifih.client.module;

import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.util.Lang;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Accessors(fluent = true)
public enum Category {
    COMBAT("combat", AdinIcon.COMBAT),
    MOVEMENT("movement", AdinIcon.MOVEMENT),
    RENDER("render", AdinIcon.RENDER),
    HUD("hud", AdinIcon.HUD),
    PLAYER("player", AdinIcon.PLAYER),
    MACROS("macros", AdinIcon.MACROS),
    MISC("misc", AdinIcon.MISC);

    @Getter
    private final String id;
    @Getter
    private final AdinIcon icon;

    public String label() {
        return Lang.get("category." + id);
    }
}
