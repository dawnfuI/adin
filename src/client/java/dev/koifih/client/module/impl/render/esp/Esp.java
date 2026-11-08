package dev.koifih.client.module.impl.render.esp;

import dev.koifih.client.event.events.EntityRenderStateEvent;
import dev.koifih.client.event.events.FrameEvent;
import dev.koifih.client.event.events.HandRenderEvent;
import dev.koifih.client.event.events.ScreenRenderEvent;
import dev.koifih.client.event.events.WorldRenderEvent;
import dev.koifih.client.module.Module;
import dev.koifih.client.render.Rect;
import dev.koifih.client.render.Style;
import dev.koifih.client.render.entity.EntityOutlines;
import dev.koifih.client.render.entity.Filled;
import dev.koifih.client.render.screen.HealthBar;
import dev.koifih.client.setting.EntitySetting;
import dev.koifih.client.setting.EnumSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.MultiSetting;
import dev.koifih.client.setting.EspPreview;
import dev.koifih.client.setting.Setting;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.util.Entities;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public final class Esp extends Module {
    private static final String[] MODES = {"3D", "2D", "Shader", "Outline"};
    private static final int MODE_3D = 0;
    private static final int MODE_2D = 1;
    private static final int MODE_SHADER = 2;
    private static final int MODE_OUTLINE = 3;
    private static final String[] TARGETS = {"Self", "Players", "Entities", "Hand"};
    private static final int SELF = 0;
    private static final int PLAYERS = 1;
    private static final int ENTITIES = 2;
    private static final int HAND = 3;

    record Target(Entity entity, AABB bounds, double distanceSq) {}

    private final EnumSetting mode = add(new EnumSetting("mode", MODE_3D, MODES));
    private final SliderSetting distance = add(new SliderSetting("distance", 64, 8, 256, Measure.DISTANCE));
    private final MultiSetting targets = add(new MultiSetting("targets", TARGETS, PLAYERS, ENTITIES));
    private final EntitySetting entities = add(new EntitySetting("entities"));
    private final Box box = new Box(this);
    private final Health health = new Health(this, box);
    private final Outline outline = new Outline(this, box);
    private final Shader shader = new Shader(this);
    private final EspPreview preview = add(new EspPreview("preview", this::flat, box::shown, box::screenStyle,
            box::worldStyle, Box::fitPlayer, health::side, shader::shade, outline::spec));

    public Esp() {
        super("esp");
        entities.visibleWhen(() -> targets.get().contains(ENTITIES));
        targets.optionVisibleWhen(option -> option != HAND || shaded() || outlined());
    }

    @Override
    public String info() {
        return mode.selected();
    }

    <S extends Setting<?>> S setting(S setting) {
        return add(setting);
    }

    boolean flat() {
        return mode.get() == MODE_2D;
    }

    boolean boxed() {
        return mode.get() == MODE_3D;
    }

    boolean shaded() {
        return mode.get() == MODE_SHADER;
    }

    boolean outlined() {
        return mode.get() == MODE_OUTLINE;
    }

    boolean handTargeted() {
        return targets.has(HAND);
    }

    boolean outlineFilled() {
        return outline.filled();
    }

    @Override
    protected void onEnable() {
        listen(WorldRenderEvent.class, this::onWorldRender);
        listen(ScreenRenderEvent.class, this::onScreenRender);
        listen(EntityRenderStateEvent.class, this::onEntityRenderState);
        listen(HandRenderEvent.class, this::onHandRender);
    }

    @Override
    protected void onDisable() {
        EntityOutlines.configure(null);
        EntityOutlines.configureHand(null);
    }

    private void onWorldRender(WorldRenderEvent event) {
        outline.endFrame();
        EntityOutlines.configure(outlined() ? outline.spec() : null);
        EntityOutlines.configureHand(outlined() && handTargeted() ? outline.spec() : null);
        if (!boxed()) return;
        Style style = box.worldStyle();
        if (!style.hasFill() && !style.hasStroke()) return;
        for (Target target : targets(event)) {
            event.buffer().box(target.bounds(), style.scaled(Box.perspective(target.distanceSq())));
        }
    }

    private void onScreenRender(ScreenRenderEvent event) {
        boolean boxes = flat() && box.shown();
        HealthBar.Side side = health.side();
        if (!boxes && side == null) return;
        float pixel = event.projection().pixel();
        Style style = box.screenStyle().scaled(pixel);
        float clearance = boxes ? style.widestStroke() * 0.5f : 0f;
        for (Target target : targets(event)) {
            AABB bounds = flat() ? fitted(target.entity(), target.bounds()) : target.bounds();
            Rect rect = event.projection().bounds(bounds);
            if (rect == null) continue;
            if (boxes) event.buffer().rect(rect, style);
            if (side != null) Health.collect(event.buffer(), target.entity(), rect, side, clearance, pixel);
        }
    }

    private void onEntityRenderState(EntityRenderStateEvent event) {
        if (!shaded() && !outlined()) return;
        Camera camera = mc.gameRenderer.mainCamera();
        Entity entity = event.entity();
        double distanceSq = entity.distanceToSqr(camera.position());
        boolean targeted = targeted(entity, camera) && distanceSq <= range();
        if (outlined()) {
            if (targeted) event.state().outlineColor = outline.color();
            if (targeted || entity.isCurrentlyGlowing()) outline.include(event, distanceSq);
            return;
        }
        if (targeted) ((Filled) event.state()).adin$setFill(shader.entityFill(event.renderPosition(), camera, event.state()));
    }

    private void onHandRender(HandRenderEvent event) {
        if (shaded() && handTargeted()) event.setFill(shader.handFill());
    }

    private double range() {
        return (double) distance.get() * distance.get();
    }

    private List<Target> targets(FrameEvent event) {
        Vec3 origin = event.camera().position();
        double range = range();
        List<Target> found = new ArrayList<>();
        for (Entity entity : event.level().entitiesForRendering()) {
            if (!targeted(entity, event.camera())) continue;
            double distanceSq = entity.distanceToSqr(origin);
            if (distanceSq <= range) found.add(new Target(entity, event.interpolatedBounds(entity), distanceSq));
        }
        return found;
    }

    private static AABB fitted(Entity entity, AABB bounds) {
        return entity instanceof Player ? Box.fitPlayer(bounds) : bounds;
    }

    private boolean targeted(Entity entity, Camera camera) {
        if (entity.isRemoved() || entity.isSpectator()) return false;
        if (entity == camera.entity() && !camera.isDetached()) return false;
        if (entity instanceof LocalPlayer) return targets.get().contains(SELF);
        if (entity instanceof Player) return targets.get().contains(PLAYERS);
        return targets.get().contains(ENTITIES) && entities.get().contains(Entities.id(entity));
    }
}
