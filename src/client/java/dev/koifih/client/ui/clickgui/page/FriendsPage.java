package dev.koifih.client.ui.clickgui.page;

import dev.koifih.client.friends.FriendList;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Scissor;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Scroller;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.clickgui.ClickGui;
import dev.koifih.client.ui.clickgui.Page;
import dev.koifih.client.ui.clickgui.PanelLayout;
import dev.koifih.client.ui.component.Bool;
import dev.koifih.client.ui.component.TextInput;
import dev.koifih.client.ui.preview.SkinCache;
import dev.koifih.client.util.Colors;
import dev.koifih.client.util.Lang;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@RequiredArgsConstructor
public final class FriendsPage implements Page {
    private static final int CARD_HEIGHT = 28;
    private static final int CARD_STRIDE = 32;
    private static final int CARD_RADIUS = 6;
    private static final int FACE_SIZE = 16;
    private static final int INPUT_HEIGHT = 18;
    private static final int TOGGLE_WIDTH = 24;
    private static final int TOGGLE_HEIGHT = 12;
    private static final int SEARCH_MAX_LENGTH = 16;
    private static final int SCROLL_MILLIS = 150;
    private static final int FADE_HEIGHT = 24;
    private static final long REFRESH_MILLIS = 1000L;

    private record Row(String name, PlayerSkin skin, boolean online, Bool toggle) {}

    private final ClickGui gui;
    private final Scroller scroller = new Scroller(SCROLL_MILLIS);
    private final List<Row> rows = new ArrayList<>();
    private final List<Row> visible = new ArrayList<>();
    private PanelLayout layout;
    private TextInput searchInput;
    private String query = "";
    private String lastQuery = "";
    private long lastSync;
    private int toggleX;
    private int toggleWidth;
    private int toggleHeight;
    private boolean shown;
    private boolean interactive;

    @Override
    public void init(PanelLayout layout) {
        this.layout = layout;
        rows.clear();
        scroller.reset();
        lastSync = System.currentTimeMillis();
        float scale = layout.scale();
        searchInput = gui.add(new TextInput(layout.rowX(), inputY(), layout.rowWidth(), layout.atLeastOne(INPUT_HEIGHT), scale,
                Component.literal(Lang.get("friends.search")), SEARCH_MAX_LENGTH, () -> query, value -> query = value));
        searchInput.setPlaceholder(Lang.get("friends.search"));
        toggleWidth = layout.atLeastOne(TOGGLE_WIDTH);
        toggleHeight = layout.atLeastOne(TOGGLE_HEIGHT);
        toggleX = layout.rowX() + layout.rowWidth() - layout.padding() - toggleWidth;
        for (Map.Entry<String, PlayerInfo> entry : roster().entrySet()) rows.add(createRow(entry.getKey(), entry.getValue()));
        filter();
        updateStates();
    }

    private Row createRow(String name, PlayerInfo info) {
        if (info == null) SkinCache.request(name);
        Bool toggle = gui.add(new Bool(toggleX, 0, toggleWidth, toggleHeight, layout.scale(), Component.literal(name),
                () -> FriendList.contains(name), friend -> {
                    if (friend) FriendList.add(name);
                    else FriendList.remove(name);
                }));
        return new Row(name, info == null ? null : info.getSkin(), info != null, toggle);
    }

    private Map<String, PlayerInfo> roster() {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener connection = client.getConnection();
        String self = client.player == null ? null : client.player.getGameProfile().name();
        Map<String, PlayerInfo> players = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                String name = info.getProfile().name();
                if (!name.equals(self)) players.put(name, info);
            }
        }
        for (String friend : FriendList.all()) players.putIfAbsent(friend, null);
        return players;
    }

    private void sync() {
        long now = System.currentTimeMillis();
        if (now - lastSync < REFRESH_MILLIS) return;
        lastSync = now;
        Map<String, PlayerInfo> roster = roster();
        Map<String, Row> existing = new LinkedHashMap<>();
        boolean changed = false;
        for (Row row : rows) {
            if (roster.containsKey(row.name())) {
                existing.put(row.name(), row);
            } else {
                gui.remove(row.toggle());
                changed = true;
            }
        }
        rows.clear();
        for (Map.Entry<String, PlayerInfo> entry : roster.entrySet()) {
            PlayerInfo info = entry.getValue();
            Row row = existing.get(entry.getKey());
            if (row == null) {
                row = createRow(entry.getKey(), info);
                changed = true;
            } else if (row.online() != (info != null)) {
                row = new Row(row.name(), info == null ? null : info.getSkin(), info != null, row.toggle());
                changed = true;
            }
            rows.add(row);
        }
        if (changed) filter();
    }

    private void filter() {
        lastQuery = query;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (Row row : rows) if (needle.isEmpty() || row.name().toLowerCase(Locale.ROOT).contains(needle)) visible.add(row);
        scroller.clamp(maxScroll());
    }

    private int inputY() {
        return layout.contentY() + layout.padding();
    }

    private int listTop() {
        return inputY() + layout.atLeastOne(INPUT_HEIGHT) + layout.scaled(PanelLayout.GAP);
    }

    private int cardHeight() {
        return layout.atLeastOne(CARD_HEIGHT);
    }

    private int cardY(int index) {
        return listTop() + index * layout.atLeastOne(CARD_STRIDE) - Math.round(scroller.shown());
    }

    private int listSpan() {
        if (visible.isEmpty()) return 0;
        return (visible.size() - 1) * layout.atLeastOne(CARD_STRIDE) + cardHeight() + layout.padding();
    }

    private float maxScroll() {
        return Math.max(0f, listSpan() - (layout.bottom() - listTop()));
    }

    private ScreenRectangle listArea() {
        return new ScreenRectangle(layout.contentX(), listTop(), layout.contentWidth(), layout.bottom() - listTop());
    }

    private void layoutRows() {
        for (int i = 0; i < visible.size(); i++) {
            Bool toggle = visible.get(i).toggle();
            toggle.setY(cardY(i) + (cardHeight() - toggle.getHeight()) / 2);
        }
    }

    private void updateStates() {
        boolean ready = shown && interactive;
        searchInput.visible = shown;
        searchInput.active = ready;
        ScreenRectangle list = listArea();
        for (Row row : rows) {
            boolean listed = visible.contains(row);
            Bool toggle = row.toggle();
            toggle.visible = shown && listed;
            toggle.active = ready && listed && toggle.getY() >= list.top() && toggle.getY() + toggle.getHeight() <= list.bottom();
        }
    }

    @Override
    public void relayout(PanelLayout layout) {
        this.layout = layout;
    }

    @Override
    public void setState(boolean shown, boolean interactive) {
        this.shown = shown;
        this.interactive = interactive;
        updateStates();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (!shown || !interactive || !layout.inContent(x, y) || maxScroll() <= 0f) return false;
        scroller.scroll(-dy * layout.atLeastOne(CARD_STRIDE), maxScroll());
        return true;
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (shown) sync();
        if (!query.equals(lastQuery)) filter();
        layoutRows();
        updateStates();
        searchInput.extractRenderState(graphics, mouseX, mouseY, delta);
        if (visible.isEmpty()) {
            drawEmpty(graphics);
            return;
        }
        ScreenRectangle list = listArea();
        Scissor.clip(list, () -> {
            drawRows(graphics, list);
            for (Row row : visible) row.toggle().extractRenderState(graphics, mouseX, mouseY, delta);
            drawFade(graphics);
        });
    }

    private void drawEmpty(GuiGraphicsExtractor graphics) {
        float scale = layout.scale();
        float centerX = layout.contentX() + layout.contentWidth() * 0.5f;
        float centerY = listTop() + (layout.bottom() - listTop()) * 0.5f;
        String[] lines = rows.isEmpty()
                ? new String[] {Lang.get("friends.empty.1"), Lang.get("friends.empty.2")}
                : new String[] {Lang.get("friends.noMatch")};
        for (int i = 0; i < lines.length; i++) {
            Text.drawCenteredX(graphics, lines[i], centerX, centerY - (6 - i * 12) * scale, 7.5f * scale, i == 0 ? Theme.TEXT : Theme.MUTED);
        }
    }

    private void drawRows(GuiGraphicsExtractor graphics, ScreenRectangle list) {
        float scale = layout.scale();
        int radius = layout.atLeastOne(CARD_RADIUS);
        int face = layout.atLeastOne(FACE_SIZE);
        int height = cardHeight();
        String offline = Lang.get("friends.offline");
        graphics.enableScissor(list.left(), list.top(), list.right(), list.bottom());
        for (int i = 0; i < visible.size(); i++) {
            int y = cardY(i);
            if (y + height < list.top() || y > list.bottom()) continue;
            Row row = visible.get(i);
            Draw.rect(graphics, layout.rowX(), y, layout.rowWidth(), height, radius, Theme.ROW);
            int faceX = layout.rowX() + layout.padding();
            int faceY = y + (height - face) / 2;
            PlayerSkin skin = row.skin() != null ? row.skin() : SkinCache.skin(row.name());
            if (skin != null) PlayerFaceExtractor.extractRenderState(graphics, skin, faceX, faceY, face);
            else Draw.rect(graphics, faceX, faceY, face, face, layout.scaled(2), Theme.CONTROL);
            float textX = faceX + face + layout.padding();
            float centerY = y + height * 0.5f;
            Text.drawCentered(graphics, row.name(), textX, centerY, 8 * scale, row.online() ? Theme.TEXT : Theme.MUTED);
            if (!row.online()) {
                Text.drawCentered(graphics, offline, textX + Text.width(row.name(), 8 * scale) + layout.scaled(PanelLayout.GAP), centerY, 6.5f * scale, Theme.MUTED);
            }
        }
        graphics.disableScissor();
    }

    private void drawFade(GuiGraphicsExtractor graphics) {
        int height = Math.min(layout.scaled(FADE_HEIGHT), layout.bottom() - listTop());
        float remaining = maxScroll() - scroller.shown();
        if (remaining <= 0f || height <= 0) return;
        int bottom = Colors.withAlpha(Theme.MAIN, Math.clamp(remaining / height, 0f, 1f));
        Draw.gradient(graphics, layout.contentX(), layout.bottom() - height, layout.contentWidth(), height,
                layout.atLeastOne(PanelLayout.RADIUS), Draw.BOTTOM_RIGHT, Colors.withAlpha(Theme.MAIN, 0f), bottom);
    }
}
