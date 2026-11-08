package dev.koifih.client.ui.clickgui.page;

import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Opacity;
import dev.koifih.client.render.Scissor;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.cape.Cape;
import dev.koifih.client.render.cape.CapeCatalog;
import dev.koifih.client.render.cape.CapeImport;
import dev.koifih.client.render.cape.CapeLayout;
import dev.koifih.client.render.cape.FileCape;
import dev.koifih.client.setting.CapePreview;
import dev.koifih.client.setting.CapeSetting;
import dev.koifih.client.ui.Scroller;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.ui.clickgui.ClickGui;
import dev.koifih.client.ui.clickgui.Page;
import dev.koifih.client.ui.clickgui.PanelLayout;
import dev.koifih.client.ui.component.Button;
import dev.koifih.client.ui.component.TextInput;
import dev.koifih.client.util.Colors;
import dev.koifih.client.util.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CapeCatalogPage implements Page {
    private static final int BACK_SIZE = 16;
    private static final int CARD_WIDTH = 84;
    private static final int CARD_HEIGHT = 106;
    private static final int CARD_GAP = 8;
    private static final int CARD_RADIUS = 6;
    private static final int MINI_HEIGHT = 64;
    private static final int MINI_RADIUS = 4;
    private static final int MINI_BACKGROUND = 0xFF0F0F0F;
    private static final int BACK_ICON = 0xe5c4;
    private static final int ADD_ICON = 0xe145;
    private static final int BROKEN_ICON = 0xe5cd;
    private static final int EDIT_ICON = 0xe3c9;
    private static final int DELETE_ICON = 0xe872;
    private static final int TOOL_SIZE = 12;
    private static final int NAME_MAX_LENGTH = 32;
    private static final int SCROLL_MILLIS = 150;
    private static final int BORDER_MARGIN = 3;

    private record Card(int x, int y, int width, int height) {}

    private static final String ADD_KEY = "add";

    private static final class CardAnim {
        final Transition x;
        final Transition y;
        final Transition hover = new Transition(0f, 120);
        final Transition selected = new Transition(0f, 150);
        final Transition shown;

        CardAnim(float x, float y, boolean fresh) {
            this.x = new Transition(x, 150, Transition.Easing.SMOOTHSTEP);
            this.y = new Transition(y, 150, Transition.Easing.SMOOTHSTEP);
            shown = new Transition(fresh ? 1f : 0f, 150);
            shown.set(1f);
        }
    }

    private final ClickGui gui;
    private final CapePreview previewSetting = new CapePreview(this::previewCape);
    private final Scroller scroller = new Scroller(SCROLL_MILLIS);
    private final Map<Object, CardAnim> anims = new HashMap<>();
    private boolean freshOpen;
    private boolean snapLayout;
    private PanelLayout layout;
    private Button back;
    private TextInput renameInput;
    private CapeSetting setting;
    private Cape hovered;
    private FileCape renaming;
    private String renameBuffer = "";
    private boolean shown;
    private boolean interactive;

    public CapeCatalogPage(ClickGui gui) {
        this.gui = gui;
        previewSetting.attach("capes");
    }

    public void open(CapeSetting setting) {
        this.setting = setting;
        scroller.reset();
        anims.clear();
        freshOpen = true;
    }

    public CapePreview previewSetting() {
        return previewSetting;
    }

    private Cape previewCape() {
        Cape selected = setting == null ? CapeCatalog.ADIN : CapeCatalog.get(setting.get());
        return hovered != null ? hovered : selected;
    }

    @Override
    public void init(PanelLayout layout) {
        this.layout = layout;
        back = gui.add(Button.icon(layout.contentX() + layout.padding(), layout.contentY() + layout.padding(),
                layout.atLeastOne(BACK_SIZE), layout.scale(), BACK_ICON,
                Component.literal(Lang.get("capes.back")), gui::closeCapeCatalog));
        renameInput = gui.add(new TextInput(0, 0, layout.scaled(CARD_WIDTH), layout.atLeastOne(14), layout.scale(),
                Component.literal(Lang.get("capes.rename")), NAME_MAX_LENGTH,
                () -> renameBuffer, value -> renameBuffer = value));
        renaming = null;
        updateStates();
    }

    public boolean cancelRename() {
        if (renaming == null) return false;
        renaming = null;
        gui.dropFocus();
        updateStates();
        return true;
    }

    private void commitRename() {
        if (renaming == null) return;
        String oldId = renaming.id();
        if (renaming.rename(renameBuffer) && setting.get().equals(oldId)) setting.set(renaming.id());
        cancelRename();
    }

    private void startRename(FileCape cape) {
        renaming = cape;
        renameBuffer = cape.label();
        updateStates();
        gui.focusWidget(renameInput);
        renameInput.highlightAll();
    }

    @Override
    public void relayout(PanelLayout layout) {
        this.layout = layout;
        snapLayout = true;
    }

    @Override
    public void setState(boolean shown, boolean interactive) {
        this.shown = shown;
        this.interactive = interactive;
        if (!shown) cancelRename();
        updateStates();
    }

    private void updateStates() {
        if (back == null) return;
        back.visible = shown;
        back.active = shown && interactive;
        renameInput.visible = shown && renaming != null;
        renameInput.active = shown && interactive && renaming != null;
    }

    private int gridTop() {
        return layout.contentY() + 2 * layout.padding() + layout.atLeastOne(BACK_SIZE);
    }

    private int columns() {
        int width = layout.scaled(CARD_WIDTH);
        int gap = layout.scaled(CARD_GAP);
        int span = layout.contentWidth() - 2 * layout.padding();
        return Math.max(1, (span + gap) / (width + gap));
    }

    private int slotX(int index) {
        int width = layout.scaled(CARD_WIDTH);
        int gap = layout.scaled(CARD_GAP);
        return layout.contentX() + layout.padding() + (index % columns()) * (width + gap);
    }

    private int slotY(int index) {
        int height = layout.scaled(CARD_HEIGHT);
        int gap = layout.scaled(CARD_GAP);
        return gridTop() + BORDER_MARGIN + (index / columns()) * (height + gap);
    }

    private CardAnim anim(Object key, int index) {
        CardAnim anim = anims.get(key);
        if (anim == null) {
            anim = new CardAnim(slotX(index), slotY(index), freshOpen);
            anims.put(key, anim);
        }
        if (snapLayout) {
            anim.x.snap(slotX(index));
            anim.y.snap(slotY(index));
        } else {
            anim.x.set(slotX(index));
            anim.y.set(slotY(index));
        }
        return anim;
    }

    private Card card(Object key, int index) {
        CardAnim anim = anim(key, index);
        return new Card(Math.round(anim.x.value()), Math.round(anim.y.value() - scroller.shown()),
                layout.scaled(CARD_WIDTH), layout.scaled(CARD_HEIGHT));
    }

    private int cardCount() {
        return CapeCatalog.all().size() + 1;
    }

    private float maxScroll() {
        int rows = (cardCount() + columns() - 1) / columns();
        int span = BORDER_MARGIN + rows * layout.scaled(CARD_HEIGHT) + (rows - 1) * layout.scaled(CARD_GAP) + layout.padding();
        return Math.max(0f, span - (layout.bottom() - gridTop()));
    }

    private ScreenRectangle gridArea() {
        return new ScreenRectangle(layout.contentX(), gridTop(), layout.contentWidth(), layout.bottom() - gridTop());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (!shown || !interactive || !layout.inContent(x, y) || maxScroll() <= 0f) return false;
        scroller.scroll(-dy * layout.scaled(CARD_HEIGHT / 2), maxScroll());
        return true;
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (setting == null) return;
        scroller.clamp(maxScroll());
        back.extractRenderState(graphics, mouseX, mouseY, delta);
        Text.drawCentered(graphics, Lang.get("capes.title"), back.getX() + back.getWidth() + layout.padding(),
                back.getY() + back.getHeight() * 0.5f, 9 * layout.scale(), Theme.TEXT);
        hovered = null;
        ScreenRectangle area = gridArea();
        boolean inArea = mouseY >= area.top() && mouseY < area.bottom();
        var capes = CapeCatalog.all();
        Scissor.clip(area, () -> {
            graphics.enableScissor(area.left(), area.top(), area.right(), area.bottom());
            for (int i = 0; i < capes.size(); i++) {
                Cape cape = capes.get(i);
                Card card = card(cape, i);
                if (card.y() + card.height() < area.top() || card.y() > area.bottom()) continue;
                boolean over = interactive && inArea && contains(card, mouseX, mouseY);
                if (over) hovered = cape;
                drawCard(graphics, card, cape, over, anims.get(cape));
            }
            Card add = card(ADD_KEY, capes.size());
            drawAddCard(graphics, add, interactive && inArea && contains(add, mouseX, mouseY), anims.get(ADD_KEY));
            graphics.disableScissor();
        });
        Set<Object> live = new HashSet<>(capes);
        live.add(ADD_KEY);
        anims.keySet().retainAll(live);
        freshOpen = false;
        snapLayout = false;
    }

    private static boolean contains(Card card, double x, double y) {
        return x >= card.x() && x < card.x() + card.width() && y >= card.y() && y < card.y() + card.height();
    }

    private void drawCard(GuiGraphicsExtractor graphics, Card card, Cape cape, boolean over, CardAnim anim) {
        float scale = layout.scale();
        int radius = layout.atLeastOne(CARD_RADIUS);
        anim.hover.set(over || renaming == cape ? 1f : 0f);
        anim.selected.set(cape.id().equals(setting.get()) ? 1f : 0f);
        float shown = anim.shown.value();
        Opacity.with(shown, () -> {
            float selection = anim.selected.value();
            if (selection > 0.01f) {
                Draw.rect(graphics, card.x() - 2, card.y() - 2, card.width() + 4, card.height() + 4,
                        radius + 2, Colors.withAlpha(Theme.ACCENT, selection));
            }
            Draw.rect(graphics, card.x(), card.y(), card.width(), card.height(), radius,
                    Colors.lerp(Theme.ROW, Theme.CONTROL, anim.hover.value()));
            int miniHeight = layout.scaled(MINI_HEIGHT);
            int miniWidth = miniHeight * 10 / 16;
            int miniX = card.x() + (card.width() - miniWidth) / 2;
            int miniY = card.y() + layout.scaled(8);
            Draw.rect(graphics, miniX, miniY, miniWidth, miniHeight, layout.atLeastOne(MINI_RADIUS),
                    Draw.TILED | Draw.ALL_CORNERS, MINI_BACKGROUND);
            cape.update();
            if (cape.broken()) {
                float icon = 16 * scale;
                Draw.icon(graphics, BROKEN_ICON, miniX + (miniWidth - icon) / 2f, miniY + (miniHeight - icon) / 2f,
                        icon, Theme.MUTED);
            } else if (shown > 0.5f) {
                int texWidth = cape.textureWidth();
                int texHeight = cape.textureHeight();
                graphics.blit(RenderPipelines.GUI_TEXTURED, cape.textureId(), miniX, miniY,
                        texWidth * CapeLayout.FACE_U0, texHeight * CapeLayout.FACE_V0, miniWidth, miniHeight,
                        Math.round(texWidth * CapeLayout.FACE_U_SIZE), Math.round(texHeight * CapeLayout.FACE_V_SIZE), texWidth, texHeight);
            }
            if (cape.animated()) {
                float badge = 10 * scale;
                Draw.icon(graphics, AdinIcon.PLAY, miniX + miniWidth - badge - 3 * scale, miniY + 3 * scale, badge,
                        Theme.TEXT, Theme.ACCENT, Theme.TEXT);
            }
            if (cape instanceof FileCape file && (anim.hover.value() > 0.01f || renaming == file)) {
                Opacity.with(renaming == file ? 1f : anim.hover.value(), () -> {
                    drawTool(graphics, tool(card, 0), EDIT_ICON);
                    drawTool(graphics, tool(card, 1), DELETE_ICON);
                });
            }
            float labelY = miniY + miniHeight + layout.scaled(12);
            if (renaming == cape) {
                renameInput.setX(card.x() + layout.scaled(4));
                renameInput.setWidth(card.width() - layout.scaled(8));
                renameInput.setY(Math.round(labelY - renameInput.getHeight() * 0.5f));
                renameInput.extractRenderState(graphics, 0, 0, 0);
            } else {
                Text.drawCenteredX(graphics, cape.label(), card.x() + card.width() * 0.5f, labelY, 7 * scale, Theme.TEXT);
            }
        });
    }

    private Card tool(Card card, int index) {
        int size = layout.scaled(TOOL_SIZE);
        int gap = layout.scaled(2);
        return new Card(card.x() + card.width() - layout.scaled(4) - (index + 1) * size - index * gap,
                card.y() + layout.scaled(4), size, size);
    }

    private void drawTool(GuiGraphicsExtractor graphics, Card tool, int icon) {
        Draw.rect(graphics, tool.x(), tool.y(), tool.width(), tool.height(), layout.scaled(3), Theme.CONTROL_ACTIVE);
        float size = tool.width() * 0.75f;
        Draw.icon(graphics, icon, tool.x() + (tool.width() - size) / 2f, tool.y() + (tool.height() - size) / 2f,
                size, Theme.TEXT);
    }

    private void drawAddCard(GuiGraphicsExtractor graphics, Card card, boolean over, CardAnim anim) {
        float scale = layout.scale();
        int radius = layout.atLeastOne(CARD_RADIUS);
        anim.hover.set(over ? 1f : 0f);
        Opacity.with(anim.shown.value(), () -> {
            Draw.rect(graphics, card.x(), card.y(), card.width(), card.height(), radius,
                    Colors.lerp(Theme.ROW, Theme.CONTROL, anim.hover.value()));
            float icon = 22 * scale;
            Draw.icon(graphics, ADD_ICON, card.x() + (card.width() - icon) / 2f,
                    card.y() + card.height() * 0.42f - icon / 2f, icon,
                    Colors.lerp(Theme.DIM, Theme.TEXT, anim.hover.value()));
            Text.drawCenteredX(graphics, Lang.get("capes.add"), card.x() + card.width() * 0.5f,
                    card.y() + card.height() * 0.42f + icon, 7 * scale, Theme.MUTED);
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!shown || !interactive || setting == null || event.button() != 0) return false;
        if (renaming != null && !renameInput.isMouseOver(event.x(), event.y())) commitRename();
        ScreenRectangle area = gridArea();
        if (event.y() < area.top() || event.y() >= area.bottom()) return false;
        var capes = CapeCatalog.all();
        for (int i = 0; i < capes.size(); i++) {
            Cape cape = capes.get(i);
            Card card = card(cape, i);
            if (!contains(card, event.x(), event.y())) continue;
            if (cape instanceof FileCape file) {
                if (contains(tool(card, 0), event.x(), event.y())) {
                    startRename(file);
                    return true;
                }
                if (contains(tool(card, 1), event.x(), event.y())) {
                    if (renaming == file) cancelRename();
                    boolean selected = setting.get().equals(cape.id());
                    if (CapeCatalog.delete(cape) && selected) setting.set(CapeCatalog.ADIN.id());
                    return true;
                }
            }
            setting.set(cape.id());
            return true;
        }
        if (contains(card(ADD_KEY, capes.size()), event.x(), event.y())) {
            CapeImport.pick(cape -> setting.set(cape.id()));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!shown || renaming == null) return false;
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            commitRename();
            return true;
        }
        return false;
    }
}
