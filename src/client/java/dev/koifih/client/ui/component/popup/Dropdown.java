package dev.koifih.client.ui.component.popup;

import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.Transform;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.util.Colors;
import dev.koifih.client.util.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class Dropdown extends RowPopup {
    private static final float OPTION_HEIGHT = 15f;
    private static final float ART_OPTION_HEIGHT = 19f;
    private static final float IMAGE_ASPECT = 12f;
    private static final float IMAGE_SPAN = 0.72f;
    private static final float IMAGE_FADE = 0.55f;
    private static final float OPTION_MIN_ZOOM = 0.9f;

    private final String[] options;
    private final IntPredicate chosen;
    private final IntConsumer choose;
    private final boolean multi;
    private final Transition[] checks;
    private final Transition[] reveals;
    private IntPredicate optionVisible = option -> true;
    private List<AdinIcon> icons = List.of();
    private List<Identifier> images = List.of();
    private boolean synced;
    private int highlighted;

    private Dropdown(int x, int y, int width, int height, float scale, Component label, String[] options,
                     IntPredicate chosen, IntConsumer choose, boolean multi) {
        super(x, y, width, height, scale, label);
        this.options = options;
        this.chosen = chosen;
        this.choose = choose;
        this.multi = multi;
        this.checks = new Transition[options.length];
        this.reveals = new Transition[options.length];
        for (int i = 0; i < options.length; i++) {
            checks[i] = new Transition(0f, 120);
            reveals[i] = new Transition(1f, 150);
        }
    }

    public static Dropdown single(int x, int y, int width, int height, float scale, Component label,
                                  String[] options, IntSupplier get, IntConsumer set) {
        return new Dropdown(x, y, width, height, scale, label, options, option -> get.getAsInt() == option, set, false);
    }

    public static Dropdown multi(int x, int y, int width, int height, float scale, Component label,
                                 String[] options, Supplier<Set<Integer>> selected) {
        return new Dropdown(x, y, width, height, scale, label, options, option -> selected.get().contains(option), option -> {
            Set<Integer> set = selected.get();
            if (!set.remove(option)) set.add(option);
        }, true);
    }

    public void setOptionVisible(IntPredicate visible) {
        optionVisible = visible;
    }

    public void setArt(List<AdinIcon> icons, List<Identifier> images) {
        this.icons = icons;
        this.images = images;
    }

    private boolean hasArt() {
        return !icons.isEmpty();
    }

    private float optionHeight() {
        return hasArt() ? ART_OPTION_HEIGHT : OPTION_HEIGHT;
    }

    @Override
    protected AdinIcon icon() {
        if (multi || !hasArt()) return super.icon();
        for (int i = 0; i < options.length; i++) if (chosen.test(i)) return icons.get(i);
        return super.icon();
    }

    private List<Integer> visibleOptions() {
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < options.length; i++) if (optionVisible.test(i)) visible.add(i);
        return visible;
    }

    private String value() {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < options.length; i++) {
            if (!chosen.test(i) || !optionVisible.test(i)) continue;
            if (!names.isEmpty()) names.append(", ");
            names.append(options[i]);
        }
        return names.isEmpty() ? Lang.get("none") : names.toString();
    }

    private void select(int option) {
        choose.accept(option);
        if (!multi) dismiss();
    }

    @Override
    protected void onOpen() {
        highlighted = 0;
        if (!multi) for (int i = 0; i < options.length; i++) if (chosen.test(i)) highlighted = i;
    }

    private void syncReveals() {
        for (int i = 0; i < options.length; i++) {
            float target = optionVisible.test(i) ? 1f : 0f;
            if (synced) reveals[i].set(target);
            else reveals[i].snap(target);
        }
    }

    private float rowSpan() {
        float span = 0f;
        for (Transition reveal : reveals) span += reveal.value();
        return span;
    }

    @Override
    protected float popupHeight() {
        return px(4 + rowSpan() * optionHeight());
    }

    private int optionAt(double y) {
        float offset = (float) ((y - popupY() - px(2)) / px(optionHeight()));
        if (offset < 0f) return -1;
        for (int i = 0; i < options.length; i++) {
            float span = reveals[i].value();
            if (offset < span) return span >= 1f && optionVisible.test(i) ? i : -1;
            offset -= span;
        }
        return -1;
    }

    @Override
    protected void drawRowValue(GuiGraphicsExtractor graphics, float rightLimit, float available) {
        float size = px(7);
        String label = value();
        float labelWidth = Text.width(label, size);
        float labelX = rightLimit - labelWidth;
        if (labelWidth <= available) {
            text(graphics, label, labelX, centerY(), size, Theme.TEXT);
        } else {
            float fadeFrom = rightLimit - available;
            Text.drawFaded(graphics, label, labelX, Text.centeredBaseline(label, size, centerY()),
                    size, Theme.TEXT, fadeFrom, fadeFrom + px(18));
        }
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, float x, float top, float width, float height) {
        syncReveals();
        if (!synced) {
            for (int i = 0; i < options.length; i++) checks[i].snap(chosen.test(i) ? 1f : 0f);
            synced = true;
        } else if (isOpen()) {
            for (int i = 0; i < options.length; i++) checks[i].set(chosen.test(i) ? 1f : 0f);
        }
        float offset = 0f;
        for (int i = 0; i < options.length; i++) {
            float shown = reveals[i].value();
            if (shown > 0f) drawOption(graphics, i, x, top + px(2) + px(optionHeight()) * (offset + shown * 0.5f), width, shown);
            offset += shown;
        }
    }

    private void drawOption(GuiGraphicsExtractor graphics, int option, float x, float center, float width, float shown) {
        float iconSize = px(8);
        float checked = checks[option].value();
        Transform.popIn(graphics, x + width * 0.5f, center, shown, OPTION_MIN_ZOOM, () -> {
            if (hasArt()) {
                drawArt(graphics, option, x, center, width, checked);
                return;
            }
            drawCheck(graphics, x + px(9), center, iconSize, checked);
            text(graphics, fit(options[option], width - px(30), px(7)), x + px(22), center, px(7), Colors.lerp(Theme.DIM, Theme.TEXT, checked));
        });
    }

    private void drawArt(GuiGraphicsExtractor graphics, int option, float x, float center, float width, float checked) {
        float imageHeight = px(ART_OPTION_HEIGHT - 5);
        float imageRight = x + width - px(4);
        float imageWidth = Math.min(imageHeight * IMAGE_ASPECT, width * IMAGE_SPAN);
        float imageX = imageRight - imageWidth;
        Draw.fadedTexture(graphics, images.get(option), imageX, center - imageHeight / 2, imageWidth, imageHeight,
                1f - imageWidth / (imageHeight * IMAGE_ASPECT), IMAGE_FADE);
        float iconSize = px(9);
        Draw.icon(graphics, icons.get(option), x + px(8), center - iconSize / 2, iconSize, Theme.DIM,
                Colors.lerp(Theme.DIM, Theme.ACCENT, checked), Colors.lerp(Theme.DIM, Theme.TEXT, checked));
        float textX = x + px(22);
        text(graphics, fit(options[option], Math.max(imageX + imageWidth * IMAGE_FADE * 0.4f, imageX) - textX, px(7)),
                textX, center, px(7), Colors.lerp(Theme.DIM, Theme.TEXT, checked));
    }

    @Override
    protected void clickPopup(double x, double y) {
        int option = optionAt(y);
        if (option < 0) return;
        highlighted = option;
        select(option);
    }

    @Override
    protected void popupKeyPressed(KeyEvent event) {
        List<Integer> visible = visibleOptions();
        if (visible.isEmpty()) return;
        int row = Math.max(0, visible.indexOf(highlighted));
        if (event.isDown()) highlighted = visible.get((row + 1) % visible.size());
        else if (event.isUp()) highlighted = visible.get((row + visible.size() - 1) % visible.size());
        else if (event.isSelection() && optionVisible.test(highlighted)) select(highlighted);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal(getMessage().getString() + ": " + value()));
        output.add(NarratedElementType.USAGE, Component.literal("Enter opens the list. Arrow keys move, Enter "
                + (multi ? "toggles." : "selects.")));
    }
}
