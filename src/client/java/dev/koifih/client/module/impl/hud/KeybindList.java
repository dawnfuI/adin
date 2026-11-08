package dev.koifih.client.module.impl.hud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.koifih.client.AdinClient;
import dev.koifih.client.event.events.HudRenderEvent;
import dev.koifih.client.module.HudModule;
import dev.koifih.client.module.Module;
import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.state.StairsState;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.PositionSetting.Anchor;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.UiScale;
import dev.koifih.client.util.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class KeybindList extends HudModule {
    private static final Map<InputConstants.Key, String> KEY_NAMES = new HashMap<>();
    private static final float DEFAULT_TOP = 22f;
    private static final float TAB_HEIGHT = 17f;
    private static final float LINE = 15f;
    private static final float BODY_TOP = 2f;
    private static final float BODY_BOTTOM = 3f;
    private static final float RADIUS = 4f;
    private static final float INSET = 4f;
    private static final float TAB_INSET = 6f;
    private static final float TITLE_GAP = 5f;
    private static final float TAB_END = 7f;
    private static final float TEXT_SIZE = 8f;
    private static final float HEADER_ICON = 11f;
    private static final float KEY_HEIGHT = 11f;
    private static final float KEY_MIN_WIDTH = 14f;
    private static final float KEY_PADDING = 4f;
    private static final float KEY_RADIUS = 3f;
    private static final float KEY_TEXT_SIZE = 7f;
    private static final float NAME_GAP = 6f;
    private static final float MODE_GAP = 10f;
    private static final float MODE_ICON = 9f;
    private static final float END = 5f;

    private record Layout(float scale, int x, int y, int width, int tabWidth, int tabHeight, int bodyHeight,
                          float keyColumn, boolean rightAligned) {}

    private final SliderSetting size = add(new SliderSetting("scale", 100, 50, 200, Measure.PERCENT));

    public KeybindList() {
        super("keybinds", Anchor.START, Anchor.START, 0f, DEFAULT_TOP);
    }

    @Override
    protected void onEnable() {
        listen(HudRenderEvent.class, this::onHudRender);
    }

    private void onHudRender(HudRenderEvent event) {
        List<Module> bound = new ArrayList<>();
        for (Module module : AdinClient.MODULES.all()) {
            if (module.key() != InputConstants.UNKNOWN) bound.add(module);
        }
        if (bound.isEmpty()) {
            placed(0f, 0f, 0f, 0f);
            return;
        }
        bound.sort(Comparator.comparing(Module::name));
        Theme.update();
        Layout layout = layout(bound);
        placed(layout.x(), layout.y(), layout.width(), layout.tabHeight() + layout.bodyHeight());
        GuiGraphicsExtractor graphics = event.graphics();
        drawShape(graphics, layout);
        drawHeader(graphics, layout);
        float rowY = layout.y() + layout.tabHeight() + BODY_TOP * layout.scale();
        for (Module module : bound) {
            drawRow(graphics, layout, module, rowY + LINE * layout.scale() * 0.5f);
            rowY += LINE * layout.scale();
        }
    }

    private Layout layout(List<Module> bound) {
        float scale = UiScale.current().factor() * size.get() / 100f;
        float names = 0f;
        float keys = KEY_MIN_WIDTH * scale;
        for (Module module : bound) {
            names = Math.max(names, Text.width(module.name(), TEXT_SIZE * scale));
            keys = Math.max(keys, keyWidth(module, scale));
        }
        int radius = Math.round(RADIUS * scale);
        int tabWidth = Math.round((TAB_INSET + HEADER_ICON + TITLE_GAP + TAB_END) * scale + Text.width(Lang.get("keybinds"), TEXT_SIZE * scale));
        int bodyWidth = Math.round((INSET + NAME_GAP + MODE_GAP + MODE_ICON + END) * scale + keys + names);
        int width = Math.max(bodyWidth, tabWidth + 2 * radius);
        int tabHeight = Math.round(TAB_HEIGHT * scale);
        int bodyHeight = Math.round((BODY_TOP + BODY_BOTTOM + LINE * bound.size()) * scale);
        var window = mc.getWindow();
        int x = Math.round(left(width, window.getGuiScaledWidth()));
        int y = Math.round(top(tabHeight + bodyHeight, window.getGuiScaledHeight()));
        return new Layout(scale, x, y, width, tabWidth, tabHeight, bodyHeight, keys, position().horizontal() == Anchor.END);
    }

    private void drawShape(GuiGraphicsExtractor graphics, Layout layout) {
        var window = mc.getWindow();
        int radius = Math.round(RADIUS * layout.scale());
        boolean flushSide = layout.rightAligned() ? layout.x() + layout.width() >= window.getGuiScaledWidth() : layout.x() <= 0;
        int edges = (flushSide ? StairsState.FLUSH_INNER : 0) | (layout.y() <= 0 ? StairsState.FLUSH_TOP : 0);
        int innerX = layout.rightAligned() ? layout.x() + layout.width() : layout.x();
        Draw.stairs(graphics, new StairsState.Row(innerX, layout.y(), layout.tabWidth(), layout.tabHeight(), StairsState.NONE,
                layout.width(), radius, edges, layout.rightAligned(), true, false), Theme.SIDEBAR);
        Draw.stairs(graphics, new StairsState.Row(innerX, layout.y() + layout.tabHeight(), layout.width(), layout.bodyHeight(),
                layout.tabWidth(), StairsState.NONE, radius, edges, layout.rightAligned(), false, true), Theme.SIDEBAR);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, Layout layout) {
        float scale = layout.scale();
        float tabX = layout.rightAligned() ? layout.x() + layout.width() - layout.tabWidth() : layout.x();
        float centerY = layout.y() + layout.tabHeight() * 0.5f;
        float icon = HEADER_ICON * scale;
        Draw.icon(graphics, AdinIcon.KEYBINDS, tabX + TAB_INSET * scale, centerY - icon * 0.5f, icon,
                Theme.DIM, Theme.ACCENT, Theme.TEXT, Theme.SIDEBAR);
        Text.drawCentered(graphics, Lang.get("keybinds"), tabX + (TAB_INSET + HEADER_ICON + TITLE_GAP) * scale, centerY,
                TEXT_SIZE * scale, Theme.TEXT);
    }

    private void drawRow(GuiGraphicsExtractor graphics, Layout layout, Module module, float centerY) {
        float scale = layout.scale();
        boolean enabled = module.isEnabled();
        String key = keyName(module);
        float keyWidth = keyWidth(module, scale);
        float keyHeight = KEY_HEIGHT * scale;
        float keyX = layout.x() + INSET * scale + (layout.keyColumn() - keyWidth) * 0.5f;
        Draw.rect(graphics, keyX, Math.round(centerY - keyHeight * 0.5f), keyWidth, Math.round(keyHeight),
                Math.round(KEY_RADIUS * scale), enabled ? Theme.ACCENT : Theme.CONTROL);
        float keyText = KEY_TEXT_SIZE * scale;
        Text.drawCentered(graphics, key, keyX + (keyWidth - Text.width(key, keyText)) * 0.5f, centerY, keyText,
                enabled ? Theme.ON_ACCENT : Theme.TEXT);
        float nameX = layout.x() + INSET * scale + layout.keyColumn() + NAME_GAP * scale;
        Text.drawCentered(graphics, module.name(), nameX, centerY, TEXT_SIZE * scale, enabled ? Theme.TEXT : Theme.DIM);
        if (module.activatable()) return;
        float icon = MODE_ICON * scale;
        Draw.icon(graphics, module.hold() ? AdinIcon.HOLD : AdinIcon.TOGGLE, layout.x() + layout.width() - (END + MODE_ICON) * scale,
                centerY - icon * 0.5f, icon, Theme.DIM, Theme.ACCENT, Theme.TEXT, Theme.SIDEBAR);
    }

    private static float keyWidth(Module module, float scale) {
        float text = Text.width(keyName(module), KEY_TEXT_SIZE * scale);
        return Math.max(KEY_MIN_WIDTH * scale, text + 2f * KEY_PADDING * scale);
    }

    private static String keyName(Module module) {
        return KEY_NAMES.computeIfAbsent(module.key(), key -> key.getType() == InputConstants.Type.MOUSE
                ? "M" + (key.getValue() + 1) : key.getDisplayName().getString());
    }
}
