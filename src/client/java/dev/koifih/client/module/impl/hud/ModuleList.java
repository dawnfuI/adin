package dev.koifih.client.module.impl.hud;

import dev.koifih.client.AdinClient;
import dev.koifih.client.event.events.HudRenderEvent;
import dev.koifih.client.module.HudModule;
import dev.koifih.client.module.Module;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.state.StairsState;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.PositionSetting.Anchor;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.setting.TextColorSettings;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.UiScale;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import dev.koifih.client.render.Opacity;
import dev.koifih.client.ui.Transition;
import java.util.HashMap;
import java.util.Map;

public final class ModuleList extends HudModule {
    private static final float MARGIN = 4f;
    private static final float HEIGHT = 12f;
    private static final float PADDING = 5f;
    private static final float EDGE_PADDING = 2f;
    private static final float GAP = 3f;
    private static final float RADIUS = 4f;
    private static final float TEXT_SIZE = 8f;
    private static final float ALPHA = 0.9f;
    private static final int REVEAL_MILLIS = 200;

    private record Row(String name, String info, float width, float shown) {}

    private final BoolSetting prefix = add(new BoolSetting("prefix", true));
    private final SliderSetting size = add(new SliderSetting("scale", 100, 50, 200, Measure.PERCENT));
    private final TextColorSettings colors = add(new TextColorSettings());
    private final Map<Module, Transition> reveals = new HashMap<>();

    public ModuleList() {
        super("arraylist", Anchor.END, Anchor.START, MARGIN, MARGIN);
    }

    @Override
    protected void onEnable() {
        listen(HudRenderEvent.class, this::onHudRender);
    }

    private void onHudRender(HudRenderEvent event) {
        Theme.update();
        GuiGraphicsExtractor graphics = event.graphics();
        float scale = UiScale.current().factor() * size.get() / 100f;
        float textSize = TEXT_SIZE * scale;
        float padding = PADDING * scale;
        float edgePadding = EDGE_PADDING * scale;
        float gap = GAP * scale;
        List<Row> rows = new ArrayList<>();
        for (Module module : AdinClient.MODULES.all()) {
            Transition reveal = reveals.computeIfAbsent(module, key -> new Transition(key.isEnabled() ? 1f : 0f, REVEAL_MILLIS, Transition.Easing.EASE_OUT_EXPO));
            reveal.set(module.isEnabled() ? 1f : 0f);
            float shown = reveal.value();
            if (shown <= 0f) continue;
            String info = prefix.get() ? module.info() : "";
            float width = Text.width(module.name(), textSize) + (info.isEmpty() ? 0f : gap + Text.width(info, textSize));
            rows.add(new Row(module.name(), info, width + padding + edgePadding, shown));
        }
        if (rows.isEmpty()) {
            placed(0f, 0f, 0f, 0f);
            return;
        }
        rows.sort(Comparator.comparingDouble(Row::width).reversed().thenComparing(Row::name));
        var window = mc.getWindow();
        int screenWidth = window.getGuiScaledWidth();
        int height = Math.max(1, Math.round(HEIGHT * scale));
        int radius = Math.round(RADIUS * scale);
        int[] widths = new int[rows.size()];
        int[] heights = new int[rows.size()];
        int[] tops = new int[rows.size()];
        int listHeight = 0;
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Math.round(rows.get(i).width());
            heights[i] = Math.round(height * rows.get(i).shown());
            tops[i] = listHeight;
            listHeight += heights[i];
        }
        int listWidth = widths[0];
        int x = Math.round(left(listWidth, screenWidth));
        int y = Math.round(top(Math.max(1, listHeight), window.getGuiScaledHeight()));
        placed(x, y, listWidth, listHeight);
        boolean rightAligned = position().horizontal() != Anchor.START;
        int edge = rightAligned ? x + listWidth : x;
        boolean flushSide = rightAligned ? edge >= screenWidth : edge <= 0;
        boolean flushTop = y <= 0;
        int background = Colors.withAlpha(Theme.MAIN, ALPHA);
        Text.ColorAt color = colors.colorAt(scale);
        int[] slides = new int[widths.length];
        for (int i = 0; i < widths.length; i++) slides[i] = Math.round((1f - rows.get(i).shown()) * widths[i]);
        int edges = (flushSide ? StairsState.FLUSH_INNER : 0) | (flushTop ? StairsState.FLUSH_TOP : 0);
        for (int i = 0; i < widths.length; i++) {
            if (heights[i] <= 0) continue;
            int above = neighbour(widths, heights, slides, i, -1);
            int below = neighbour(widths, heights, slides, i, 1);
            int innerX = rightAligned ? edge + slides[i] : edge - slides[i];
            StairsState.Row shape = new StairsState.Row(innerX, y + tops[i], widths[i], heights[i], above, below, radius,
                    edges, rightAligned, above == StairsState.NONE, below == StairsState.NONE);
            Row row = rows.get(i);
            int rowX = rightAligned ? innerX - widths[i] : innerX;
            Opacity.with(row.shown(), () -> {
                Draw.stairs(graphics, shape, background);
                float textX = rowX + (rightAligned ? padding : edgePadding);
                float centerY = shape.y() + shape.height() * 0.5f;
                Text.drawCentered(graphics, row.name(), textX, centerY, textSize, color);
                if (!row.info().isEmpty()) {
                    Text.drawCentered(graphics, row.info(), textX + Text.width(row.name(), textSize) + gap, centerY, textSize, Theme.DIM);
                }
            });
        }
    }

    private static int neighbour(int[] widths, int[] heights, int[] slides, int index, int direction) {
        for (int j = index + direction; j >= 0 && j < widths.length; j += direction) {
            if (heights[j] <= 0) continue;
            int extent = widths[j] + slides[index] - slides[j];
            return extent > 0 ? extent : StairsState.NONE;
        }
        return StairsState.NONE;
    }
}
