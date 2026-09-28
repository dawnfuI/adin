package dev.koifih.client.ui.clickgui;

import dev.koifih.client.input.Keybinds;
import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.Transform;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Tooltips;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.ui.UiScale;
import dev.koifih.client.ui.Units;
import dev.koifih.client.ui.component.popup.AccentPicker;
import dev.koifih.client.ui.component.Bool;
import dev.koifih.client.ui.component.Button;
import dev.koifih.client.ui.component.Control;
import dev.koifih.client.ui.component.popup.Dropdown;
import dev.koifih.client.ui.component.Keybind;
import dev.koifih.client.ui.component.popup.Popup;
import dev.koifih.client.ui.component.Segmented;
import dev.koifih.client.util.Clicks;
import dev.koifih.client.util.Lang;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

@RequiredArgsConstructor
public final class SettingsMenu {
    private static final int REVEAL_MILLIS = 180;
    private static final int WIDTH = 164;
    private static final int PADDING = 10;
    private static final int TITLE_HEIGHT = 18;
    private static final int ROW_STRIDE = 22;
    private static final int ROW_COUNT = 8;
    private static final int TOGGLE_WIDTH = 24;
    private static final int TOGGLE_HEIGHT = 12;
    private static final int GEAR_GAP = 6;
    private static final int GEAR_SIZE = 16;
    private static final int RADIUS = 6;
    private static final int CLOSE_ICON = 0xe5cd;
    private static final int BIND_WIDTH = 56;
    private static final int FIELD_LABEL_INSET = 23;
    private static final int LEFT_INSET = 2;
    private static final int BIND_HEIGHT = 16;
    private static final Theme.Mode[] MODES = {Theme.Mode.LIGHT, Theme.Mode.DARK};

    private final ClickGui gui;
    private final Transition reveal = new Transition(0f, REVEAL_MILLIS, 110, Transition.Easing.EASE_OUT_SETTLE, Transition.Easing.EASE_IN_CUBIC);
    private final List<Control> controls = new ArrayList<>();
    private PanelLayout layout;
    private boolean open;
    private Keybind bind;

    public void init(PanelLayout layout) {
        this.layout = layout;
        controls.clear();
        float scale = layout.scale();
        int rowHeight = layout.atLeastOne(ROW_STRIDE);
        int inner = layout.scaled(PADDING);

        int closeSize = layout.atLeastOne(12);
        Button close = gui.add(Button.icon(right() - inner - closeSize,
                y() + inner + (layout.scaled(TITLE_HEIGHT) - closeSize) / 2, closeSize, scale, CLOSE_ICON,
                Component.literal("Close client settings"), this::close));

        int switchHeight = layout.atLeastOne(16);
        Segmented theme = gui.add(new Segmented(x() + inner, rowY(0) + (rowHeight - switchHeight) / 2,
                width() - 2 * inner, switchHeight, scale, Component.literal("Theme"),
                new Segmented.Segment[] {
                        Segmented.Segment.of(AdinIcon.LIGHT, Lang.get("theme.light")),
                        Segmented.Segment.of(AdinIcon.DARK, Lang.get("theme.dark"))},
                () -> Theme.mode() == Theme.Mode.LIGHT ? 0 : 1, index -> Theme.setMode(MODES[index])));

        Dropdown language = choice(1, "language", AdinIcon.LANGUAGE, names(Lang.Language.ALL, Lang.Language::displayName),
                () -> Lang.current().ordinal(), index -> {
                    Lang.set(Lang.Language.ALL[index]);
                    gui.requestRebuild();
                });
        Dropdown size = choice(2, "size", AdinIcon.SIZE, names(UiScale.ALL, UiScale::displayName),
                () -> UiScale.current().ordinal(), index -> UiScale.set(UiScale.ALL[index]));
        Dropdown units = choice(3, "units", AdinIcon.UNITS, names(Units.ALL, Units::displayName),
                () -> Units.current().ordinal(), index -> Units.set(Units.ALL[index]));

        int accentHeight = layout.atLeastOne(14);
        AccentPicker accent = new AccentPicker(0, rowY(5) + (rowHeight - accentHeight) / 2, accentHeight, scale,
                Theme::accentRgb, Theme::setAccent);
        accent.setX(right() - inner - accent.getWidth());
        gui.add(accent);
        accent.setBottomLimit(layout.bottom());

        Dropdown tooltips = choice(4, "tooltips", AdinIcon.TOOLTIPS, names(Tooltips.ALL, Tooltips::displayName),
                () -> Tooltips.current().ordinal(), index -> {
                    Tooltips.set(Tooltips.ALL[index]);
                    gui.requestRebuild();
                });

        int bindWidth = layout.atLeastOne(BIND_WIDTH);
        int bindHeight = layout.atLeastOne(BIND_HEIGHT);
        bind = gui.add(new Keybind(right() - inner - bindWidth, rowY(6) + (rowHeight - bindHeight) / 2,
                bindWidth, bindHeight, scale, Component.literal(Lang.get("clickgui_bind")),
                Keybinds::clickGuiKey, Keybinds::setClickGuiKey));
        bind.setClearable(false);

        int toggleWidth = layout.atLeastOne(TOGGLE_WIDTH);
        int toggleHeight = layout.atLeastOne(TOGGLE_HEIGHT);
        Bool clicks = gui.add(new Bool(right() - inner - toggleWidth, rowY(7) + (rowHeight - toggleHeight) / 2,
                toggleWidth, toggleHeight, scale, Component.literal(Lang.get("click_simulation")),
                () -> Clicks.simulate, value -> Clicks.simulate = value));

        controls.addAll(List.of(close, theme, language, size, units, tooltips, accent, bind, clicks));
    }

    private Dropdown choice(int row, String key, AdinIcon icon, String[] names, IntSupplier get, IntConsumer set) {
        int inner = layout.scaled(PADDING);
        int height = layout.atLeastOne(18);
        Dropdown dropdown = gui.add(Dropdown.single(x() + inner, rowY(row) + (layout.atLeastOne(ROW_STRIDE) - height) / 2,
                width() - 2 * inner, height, layout.scale(), Component.literal(Lang.get(key)), names, get, set));
        dropdown.setIcon(icon);
        dropdown.setBottomLimit(layout.bottom());
        return dropdown;
    }

    private static <T> String[] names(T[] values, Function<T, String> name) {
        String[] names = new String[values.length];
        for (int i = 0; i < names.length; i++) names[i] = name.apply(values[i]);
        return names;
    }

    public void relayout(PanelLayout layout) {
        this.layout = layout;
    }

    public int width() {
        return Math.min(layout.width() - 2 * layout.padding(), layout.scaled(WIDTH));
    }

    public int height() {
        return 2 * layout.scaled(PADDING) + layout.scaled(TITLE_HEIGHT) + ROW_COUNT * layout.atLeastOne(ROW_STRIDE);
    }

    public int x() {
        return layout.x() + layout.atLeastOne(LEFT_INSET);
    }

    private float labelX() {
        return x() + layout.scaled(PADDING) + FIELD_LABEL_INSET * layout.scale();
    }

    public int right() {
        return x() + width();
    }

    public int y() {
        return layout.bottom() - layout.sidebarInset() - layout.atLeastOne(GEAR_SIZE) - layout.scaled(GEAR_GAP) - height();
    }

    private int rowY(int row) {
        return y() + layout.scaled(PADDING) + layout.scaled(TITLE_HEIGHT) + row * layout.atLeastOne(ROW_STRIDE);
    }

    public boolean contains(double pointX, double pointY) {
        return pointX >= x() && pointX < right() && pointY >= y() && pointY < y() + height();
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isHidden() {
        return !open && reveal.value() == 0f;
    }

    public List<Popup> popups() {
        List<Popup> popups = new ArrayList<>();
        for (Control control : controls) if (control instanceof Popup popup) popups.add(popup);
        return popups;
    }

    public boolean captureMouse(MouseButtonEvent event) {
        return bind.captureMouse(event);
    }

    public void open() {
        open = true;
        reveal.set(1f);
        gui.dropFocus();
    }

    public void close() {
        for (Control control : controls) control.dismiss();
        open = false;
        reveal.set(0f);
        gui.dropFocus();
    }

    public void updateStates() {
        boolean ready = open && reveal.value() == 1f;
        for (Control control : controls) {
            control.active = ready;
            control.visible = reveal.value() > 0f;
        }
    }

    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal.value();
        if (shown <= 0f) return;
        graphics.nextStratum();
        float scale = layout.scale();
        float anchorX = x() + layout.scaled(8);
        float anchorY = y() + height();
        Transform.popIn(graphics, anchorX, anchorY, shown, 0.94f, () -> Transform.translated(graphics, 0f, 4 * scale * (1f - shown),
                () -> drawBody(graphics, mouseX, mouseY, delta)));
    }

    private void drawBody(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float scale = layout.scale();
        int radius = layout.atLeastOne(RADIUS);
        Draw.bordered(graphics, x(), y(), width(), height(), radius, Theme.OVERLAY, Theme.POPUP_BORDER);
        float titleX = x() + PADDING * scale;
        float textX = labelX();
        Text.drawCentered(graphics, Lang.get("settings"), titleX,
                y() + (PADDING + TITLE_HEIGHT * 0.5f) * scale, 8 * scale, Theme.TEXT);
        Text.drawCentered(graphics, Lang.get("accent"), textX, rowY(5) + layout.atLeastOne(ROW_STRIDE) * 0.5f, 7 * scale, Theme.TEXT);
        Text.drawCentered(graphics, Lang.get("clickgui_bind"), textX, rowY(6) + layout.atLeastOne(ROW_STRIDE) * 0.5f, 7 * scale, Theme.TEXT);
        Text.drawCentered(graphics, Lang.get("click_simulation"), textX, rowY(7) + layout.atLeastOne(ROW_STRIDE) * 0.5f, 7 * scale, Theme.TEXT);
        for (Control control : controls) control.extractRenderState(graphics, mouseX, mouseY, delta);
        for (Control control : controls) if (control instanceof Popup popup) popup.renderPopup(graphics, mouseX, mouseY);
    }
}
