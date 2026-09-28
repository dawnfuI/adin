package dev.koifih.client.ui.component.popup;

import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Opacity;
import dev.koifih.client.render.Scissor;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.Transform;
import dev.koifih.client.ui.preview.Catalog;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.util.Colors;
import dev.koifih.client.util.Lang;
import dev.koifih.client.util.Time;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class CatalogPicker extends RowPopup {
    private static final int SEARCH_ICON = 0xe8b6;
    private static final float MARGIN = 6f;
    private static final float GROUP_WIDTH = 64f;
    private static final float GROUP_HEIGHT = 16f;
    private static final float PREVIEW_WIDTH = 120f;
    private static final float ROW_HEIGHT = 14f;
    private static final float SEARCH_HEIGHT = 14f;
    private static final int SEARCH_MAX_LENGTH = 24;
    private static final float SPIN_DEGREES_PER_SECOND = 60f;
    private static final long BLINK_MILLIS = 500L;

    private final Catalog catalog;
    private final Supplier<Set<String>> selected;
    private final Transition scrollShown = new Transition(0f, 120);
    private final Transition groupHighlight = new Transition(0f, 150);
    private final Map<String, Transition> checks = new HashMap<>();
    private int windowX;
    private int windowY;
    private int windowWidth;
    private int windowHeight;
    private int groupIndex;
    private String query = "";
    private record Row(Catalog.Entry entry, Catalog.Group header) {}

    private List<Row> rows = List.of();
    private String filteredFor;
    private int filteredGroup = -1;
    private float scroll;
    private Catalog.Entry preview;
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    public CatalogPicker(int x, int y, int width, int height, float scale, Component label, Catalog catalog,
                         Supplier<Set<String>> selected) {
        super(x, y, width, height, scale, label);
        this.catalog = catalog;
        this.selected = selected;
    }

    public void setWindow(int x, int y, int width, int height) {
        windowX = x;
        windowY = y;
        windowWidth = width;
        windowHeight = height;
    }

    @Override
    protected float popupX() {
        return windowX;
    }

    @Override
    protected float popupY() {
        return windowY;
    }

    @Override
    protected float popupWidth() {
        return windowWidth;
    }

    @Override
    protected float popupHeight() {
        return windowHeight;
    }

    private float groupsX() {
        return windowX + px(MARGIN);
    }

    private float columnTop() {
        return windowY + px(MARGIN);
    }

    private float columnHeight() {
        return windowHeight - 2 * px(MARGIN);
    }

    private float listX() {
        return groupsX() + px(GROUP_WIDTH + MARGIN);
    }

    private float listTop() {
        return columnTop() + px(SEARCH_HEIGHT + 4);
    }

    private float listHeight() {
        return columnHeight() - px(SEARCH_HEIGHT + 4);
    }

    private float previewX() {
        return windowX + windowWidth - px(MARGIN + PREVIEW_WIDTH);
    }

    private float previewWidth() {
        return px(PREVIEW_WIDTH);
    }

    private float listWidth() {
        return previewX() - px(MARGIN) - listX();
    }

    private boolean searching() {
        return !query.isBlank();
    }

    private List<Row> rows() {
        if (query.equals(filteredFor) && filteredGroup == groupIndex) return rows;
        filteredFor = query;
        filteredGroup = groupIndex;
        List<Row> result = new ArrayList<>();
        if (!searching()) {
            for (Catalog.Entry entry : catalog.entries(catalog.groups()[groupIndex])) result.add(new Row(entry, null));
        } else {
            String needle = query.trim().toLowerCase(Locale.ROOT);
            for (Catalog.Group group : catalog.groups()) {
                boolean headed = false;
                for (Catalog.Entry entry : catalog.entries(group)) {
                    if (!entry.name().toLowerCase(Locale.ROOT).contains(needle) && !entry.id().contains(needle)) continue;
                    if (!headed) {
                        result.add(new Row(null, group));
                        headed = true;
                    }
                    result.add(new Row(entry, null));
                }
            }
        }
        rows = result;
        return rows;
    }

    private Catalog.Entry firstEntry() {
        for (Row row : rows()) if (row.entry() != null) return row.entry();
        return null;
    }

    private boolean contains(Catalog.Entry entry) {
        for (Row row : rows()) if (row.entry() == entry) return true;
        return false;
    }

    private float maxScroll() {
        return Math.max(0f, rows().size() * px(ROW_HEIGHT) - listHeight());
    }

    private int rowAt(double y) {
        return (int) ((y - listTop() + scroll) / px(ROW_HEIGHT));
    }

    private Transition check(String id, boolean chosen) {
        return checks.computeIfAbsent(id, key -> new Transition(chosen ? 1f : 0f, 120));
    }

    private void setQuery(String value) {
        query = value;
        scroll = 0f;
        if (preview == null || !contains(preview)) preview = firstEntry();
    }

    @Override
    protected void onOpen() {
        scroll = 0f;
        scrollShown.snap(0f);
        if (preview == null) preview = firstEntry();
    }

    @Override
    public void dismiss() {
        super.dismiss();
        query = "";
    }

    @Override
    protected void drawRowValue(GuiGraphicsExtractor graphics, float rightLimit, float available) {
        float size = px(7);
        String label = selected.get().size() + " " + Lang.get("entities.selected");
        text(graphics, label, rightLimit - Text.width(label, size), centerY(), size, Theme.TEXT);
    }

    @Override
    protected void drawPopup(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                             float shown, int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        Transform.popIn(graphics, x + width / 2f, y + height / 2f, shown, 0.96f, () -> drawWindow(graphics, x, y, width, height));
        if (shown >= 1f) drawPreview(graphics);
    }

    private void drawWindow(GuiGraphicsExtractor graphics, float x, float y, float width, float height) {
        Draw.bordered(graphics, x, y, width, height, px(6), Theme.OVERLAY, Theme.POPUP_BORDER);
        drawGroups(graphics);
        drawSearch(graphics);
        drawList(graphics);
        rect(graphics, previewX(), columnTop(), previewWidth(), columnHeight(), px(4), Theme.ROW);
        Catalog.Entry shownEntry = shownEntry();
        if (shownEntry != null) {
            String name = fit(shownEntry.name(), previewWidth() - px(8), px(6.5f));
            Text.drawCenteredX(graphics, name, previewX() + previewWidth() / 2,
                    columnTop() + columnHeight() - px(8), px(6.5f), Theme.MUTED);
            if (!catalog.canPreview(shownEntry)) {
                String[] lines = {Lang.get("preview.unsupported.1"), Lang.get("preview.unsupported.2")};
                float centerX = previewX() + previewWidth() / 2;
                float centerY = columnTop() + columnHeight() / 2 - px(8);
                for (int i = 0; i < lines.length; i++) {
                    String line = fit(lines[i], previewWidth() - px(10), px(6.5f));
                    Text.drawCenteredX(graphics, line, centerX, centerY + i * px(10), px(6.5f),
                            i == 0 ? Theme.TEXT : Theme.MUTED);
                }
            }
        }
    }

    private void drawGroups(GuiGraphicsExtractor graphics) {
        float size = px(7);
        float groupsX = groupsX();
        Catalog.Group[] groups = catalog.groups();
        groupHighlight.set(groupIndex);
        float dim = searching() ? 0.4f : 1f;
        Opacity.with(dim, () -> {
            rect(graphics, groupsX, columnTop() + groupHighlight.value() * px(GROUP_HEIGHT), px(GROUP_WIDTH), px(GROUP_HEIGHT), px(4),
                    Theme.CONTROL_ACTIVE);
            for (int i = 0; i < groups.length; i++) {
                float rowY = columnTop() + i * px(GROUP_HEIGHT);
                float selectedAmount = Math.clamp(1f - Math.abs(groupHighlight.value() - i), 0f, 1f);
                text(graphics, fit(groups[i].label(), px(GROUP_WIDTH - 12), size), groupsX + px(6), rowY + px(GROUP_HEIGHT) / 2, size,
                        Colors.lerp(Theme.DIM, Theme.TEXT, selectedAmount));
            }
        });
    }

    private void drawSearch(GuiGraphicsExtractor graphics) {
        float x = listX();
        float y = columnTop();
        float size = px(6.5f);
        rect(graphics, x, y, listWidth(), px(SEARCH_HEIGHT), px(4), Theme.FIELD);
        float centerY = y + px(SEARCH_HEIGHT) / 2;
        float iconSize = px(8);
        Draw.icon(graphics, SEARCH_ICON, x + px(4), centerY - iconSize / 2, iconSize, Theme.DIM);
        rect(graphics, x + px(16), centerY - px(4), Math.max(0.5f, scale), px(8), 0, Theme.CONTROL);
        float textX = x + px(21);
        if (query.isEmpty()) {
            text(graphics, fit(Lang.get("search"), listWidth() - px(27), size), textX, centerY, size, Theme.MUTED);
        } else {
            text(graphics, fit(query, listWidth() - px(27), size), textX, centerY, size, Theme.TEXT);
        }
        boolean blinkVisible = Time.blink(BLINK_MILLIS);
        if (isOpen() && blinkVisible) {
            float caretX = Math.min(x + listWidth() - px(5), textX + Text.width(query, size));
            rect(graphics, caretX, centerY - px(4), Math.max(0.5f, scale), px(8), 0, Theme.ACCENT);
        }
    }

    private void drawList(GuiGraphicsExtractor graphics) {
        float listX = listX();
        scrollShown.set(scroll);
        float scrollOffset = scrollShown.value();
        rect(graphics, listX, listTop(), listWidth(), listHeight(), px(4), Theme.ROW);
        var listArea = new ScreenRectangle(Math.round(listX), Math.round(listTop()), Math.round(listWidth()), Math.round(listHeight()));
        Scissor.clip(listArea, () -> {
            Set<String> chosen = selected.get();
            List<Row> rows = rows();
            float iconSize = px(7);
            for (int i = 0; i < rows.size(); i++) {
                float rowY = listTop() + i * px(ROW_HEIGHT) - scrollOffset;
                if (rowY + px(ROW_HEIGHT) < listTop() || rowY > listTop() + listHeight()) continue;
                Row row = rows.get(i);
                if (row.header() != null) {
                    float headerCenter = rowY + px(ROW_HEIGHT) / 2;
                    String header = fit(row.header().label(), listWidth() - px(12), px(7));
                    text(graphics, header, listX + px(6), headerCenter, px(7), Theme.TEXT);
                    rect(graphics, listX + px(6) + Text.width(header, px(7)) + px(5), headerCenter,
                            listWidth() - px(17) - Text.width(header, px(7)), Math.max(0.5f, scale), 0, Theme.CONTROL);
                    continue;
                }
                Catalog.Entry entry = row.entry();
                boolean isChosen = chosen.contains(entry.id());
                Transition check = check(entry.id(), isChosen);
                check.set(isChosen ? 1f : 0f);
                float shownCheck = check.value();
                float rowCenter = rowY + px(ROW_HEIGHT) / 2;
                drawCheck(graphics, listX + px(6), rowCenter, iconSize, shownCheck);
                text(graphics, fit(entry.name(), listWidth() - px(24), px(6.5f)), listX + px(17), rowCenter, px(6.5f),
                        Colors.lerp(Theme.DIM, Theme.TEXT, shownCheck));
            }
        });
        if (maxScroll() > 0f) {
            float trackHeight = listHeight() - px(4);
            float thumbHeight = Math.max(px(8), trackHeight * listHeight() / (listHeight() + maxScroll()));
            float thumbY = listTop() + px(2) + (trackHeight - thumbHeight) * (scrollOffset / maxScroll());
            rect(graphics, listX + listWidth() - px(3), thumbY, px(1.5f), thumbHeight, px(0.75f), Theme.DIM);
        }
    }

    private Catalog.Entry shownEntry() {
        Catalog.Entry hovered = entryAt(lastMouseX, lastMouseY);
        return hovered != null ? hovered : preview;
    }

    private void drawPreview(GuiGraphicsExtractor graphics) {
        Catalog.Entry shownEntry = shownEntry();
        if (shownEntry == null || !catalog.canPreview(shownEntry)) return;
        float yaw = (Time.seconds() * SPIN_DEGREES_PER_SECOND) % 360f;
        int x0 = Math.round(previewX() + px(4));
        int y0 = Math.round(columnTop() + px(4));
        int x1 = Math.round(previewX() + previewWidth() - px(4));
        int y1 = Math.round(columnTop() + columnHeight() - px(16));
        catalog.drawPreview(graphics, shownEntry, x0, y0, x1, y1, yaw);
    }

    private Catalog.Entry entryAt(double x, double y) {
        if (x < listX() || x >= listX() + listWidth() || y < listTop() || y >= listTop() + listHeight()) return null;
        int row = rowAt(y);
        return row >= 0 && row < rows().size() ? rows().get(row).entry() : null;
    }

    @Override
    protected void clickPopup(double x, double y) {
        if (x >= groupsX() && x < groupsX() + px(GROUP_WIDTH)) {
            int index = (int) ((y - columnTop()) / px(GROUP_HEIGHT));
            if (index >= 0 && index < catalog.groups().length) {
                groupIndex = index;
                setQuery("");
            }
            return;
        }
        Catalog.Entry entry = entryAt(x, y);
        if (entry == null) return;
        preview = entry;
        Set<String> chosen = selected.get();
        if (!chosen.remove(entry.id())) chosen.add(entry.id());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (!isOpen() || !insidePopup(x, y)) return false;
        scroll = Math.clamp((float) (scroll - dy * px(ROW_HEIGHT) * 2), 0f, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isOpen() && event.key() == GLFW.GLFW_KEY_ESCAPE && searching()) {
            setQuery("");
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void popupKeyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            setQuery(query.substring(0, query.offsetByCodePoints(query.length(), -1)));
        } else if (event.isDown()) {
            scroll = Math.min(maxScroll(), scroll + px(ROW_HEIGHT));
        } else if (event.isUp()) {
            scroll = Math.max(0f, scroll - px(ROW_HEIGHT));
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!isOpen() || !active) return false;
        String typed = event.codepointAsString();
        if (!event.isAllowedChatCharacter() || query.length() >= SEARCH_MAX_LENGTH) return true;
        setQuery(query + typed);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal(getMessage().getString() + ": " + selected.get().size()
                + " " + Lang.get("entities.selected")));
        output.add(NarratedElementType.USAGE, Component.literal("Type to search. Up and down scroll the list."));
    }
}
