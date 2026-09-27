package dev.koifih.client.module.impl.hud;

import dev.koifih.client.AdinClient;
import dev.koifih.client.event.events.HudRenderEvent;
import dev.koifih.client.event.events.ModuleToggleEvent;
import dev.koifih.client.module.Module;
import dev.koifih.client.module.impl.hud.watermark.Watermark;
import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Opacity;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.Transform;
import dev.koifih.client.setting.TextColorSettings;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.ui.UiScale;
import dev.koifih.client.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.List;

public final class Notifications extends Module {
    private static final long SHOW_NANOS = 2_500_000_000L;
    private static final int ENTER_MILLIS = 220;
    private static final int EXIT_MILLIS = 120;
    private static final int DEPTH_MILLIS = 200;
    private static final int STACK = 3;
    private static final float TOP = 6f;
    private static final float HEIGHT = 18f;
    private static final float RADIUS = 5f;
    private static final float PADDING = 6f;
    private static final float ICON_SIZE = 11f;
    private static final float ICON_GAP = 5f;
    private static final float END_PADDING = 8f;
    private static final float TEXT_SIZE = 8f;
    private static final float STRIKE_DROP = 0.9f;
    private static final float STRIKE_OVERHANG = 1.5f;
    private static final float LIFT = 6f;
    private static final float PEEK = 3f;
    private static final float SHRINK = 0.06f;
    private static final float FADE = 0.3f;
    private static final float ALPHA = 0.9f;

    private record Toast(AdinIcon icon, String text, boolean enabled, long shownAt, Transition reveal, Transition depth) {}

    private final TextColorSettings colors = add(new TextColorSettings());
    private final List<Toast> toasts = new ArrayList<>();

    public Notifications() {
        super("notifications");
    }

    @Override
    protected void onEnable() {
        listen(ModuleToggleEvent.class, event -> push(event.module(), event.enabled()));
        listen(HudRenderEvent.class, this::onHudRender);
    }

    @Override
    protected void onDisable() {
        toasts.clear();
    }

    public void alert(Module source, String message) {
        if (!isEnabled()) return;
        Watermark island = AdinClient.MODULES.get(Watermark.class);
        if (island.isEnabled()) island.showAlert(source, message);
        else push(AdinIcon.ALERT, message, true);
    }

    private void push(Module module, boolean enabled) {
        Watermark island = AdinClient.MODULES.get(Watermark.class);
        if (island.isEnabled()) island.showToggle(module, enabled);
        else push(module.category().icon(), module.name(), enabled);
    }

    private void push(AdinIcon icon, String text, boolean enabled) {
        for (Toast toast : toasts) toast.depth().set(toast.depth().target() + 1f);
        Transition reveal = new Transition(0f, ENTER_MILLIS, EXIT_MILLIS,
                Transition.Easing.EASE_OUT_CUBIC, Transition.Easing.EASE_IN_CUBIC);
        reveal.set(1f);
        toasts.add(new Toast(icon, text, enabled, System.nanoTime(), reveal, new Transition(0f, DEPTH_MILLIS)));
    }

    private void onHudRender(HudRenderEvent event) {
        long now = System.nanoTime();
        for (Toast toast : toasts) {
            if (now - toast.shownAt() >= SHOW_NANOS || toast.depth().target() >= STACK) toast.reveal().set(0f);
        }
        toasts.removeIf(toast -> toast.reveal().target() == 0f && toast.reveal().value() <= 0f);
        if (toasts.isEmpty()) return;
        Theme.update();
        float scale = UiScale.current().factor();
        float centerX = Minecraft.getInstance().getWindow().getGuiScaledWidth() * 0.5f;
        Text.ColorAt color = colors.colorAt(scale);
        for (Toast toast : toasts) draw(event.graphics(), toast, centerX, scale, color);
    }

    private void draw(GuiGraphicsExtractor graphics, Toast toast, float centerX, float scale, Text.ColorAt color) {
        float shown = toast.reveal().value();
        if (shown <= 0f) return;
        float depth = toast.depth().value();
        float textSize = TEXT_SIZE * scale;
        int height = Math.max(1, Math.round(HEIGHT * scale));
        float width = (PADDING + ICON_SIZE + ICON_GAP + END_PADDING) * scale + Text.width(toast.text(), textSize);
        float x = centerX - width * 0.5f;
        float y = (TOP - PEEK * depth - LIFT * (1f - shown)) * scale;
        float centerY = y + height * 0.5f;
        float opacity = shown * Math.max(0f, 1f - FADE * depth);
        Opacity.with(opacity, () -> Transform.scaledAbout(graphics, centerX, centerY, 1f - SHRINK * depth, () -> {
            Draw.rect(graphics, x, y, width, height, Math.round(RADIUS * scale), Colors.withAlpha(Theme.MAIN, ALPHA));
            Opacity.with(1f - depth, () -> content(graphics, toast, x, centerY, scale, color, shown));
        }));
    }

    private void content(GuiGraphicsExtractor graphics, Toast toast, float x, float centerY, float scale,
                         Text.ColorAt color, float shown) {
        float icon = ICON_SIZE * scale;
        float iconX = x + PADDING * scale;
        float textX = iconX + icon + ICON_GAP * scale;
        float textSize = TEXT_SIZE * scale;
        String name = toast.text();
        AdinIcon glyph = toast.icon();
        if (toast.enabled()) {
            Draw.icon(graphics, glyph, iconX, centerY - icon * 0.5f, icon,
                    Theme.DIM, color.at(iconX + icon * 0.5f), Theme.TEXT, Theme.MAIN);
            Text.drawCentered(graphics, name, textX, centerY, textSize, Theme.TEXT);
            return;
        }
        Draw.icon(graphics, glyph, iconX, centerY - icon * 0.5f, icon, Theme.MUTED, Theme.MUTED, Theme.MUTED, Theme.MAIN);
        Text.drawCentered(graphics, name, textX, centerY, textSize, Theme.MUTED);
        int thickness = Math.max(1, Math.round(scale));
        float overhang = STRIKE_OVERHANG * scale;
        float strike = (Text.width(name, textSize) + 2f * overhang) * shown;
        Draw.rect(graphics, textX - overhang, centerY + STRIKE_DROP * scale - thickness * 0.5f, strike, thickness, 0, Theme.TEXT);
    }
}
