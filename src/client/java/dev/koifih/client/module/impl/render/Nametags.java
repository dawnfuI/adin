package dev.koifih.client.module.impl.render;

import dev.koifih.client.event.events.EntityRenderStateEvent;
import dev.koifih.client.event.events.HudRenderEvent;
import dev.koifih.client.module.Module;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Point;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.screen.Projection;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.setting.TargetSettings;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.UiScale;
import dev.koifih.client.ui.Units;
import dev.koifih.client.util.Colors;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Nametags extends Module {
    private static final int HEALTHY = 0xFFB8DDB0;
    private static final int HURT = 0xFFE3C35A;
    private static final int DYING = 0xFFE06B6B;
    private static final float HEIGHT = 15f;
    private static final float PADDING = 5f;
    private static final float GAP = 5f;
    private static final float RADIUS = 4f;
    private static final float TEXT_SIZE = 8f;
    private static final float SMALL_SIZE = 7f;
    private static final float LIFT = 0.45f;

    private record Tag(LivingEntity entity, Point anchor, double distance) {}

    private final SliderSetting scale = add(new SliderSetting("scale", 100, 50, 200, Measure.PERCENT));
    private final SliderSetting range = add(new SliderSetting("distance", 64, 8, 256, Measure.DISTANCE));
    private final BoolSetting health = add(new BoolSetting("health", true));
    private final BoolSetting showDistance = add(new BoolSetting("showDistance", true));
    private final TargetSettings targets = add(new TargetSettings());

    public Nametags() {
        super("nametags");
    }

    @Override
    protected void onEnable() {
        listen(HudRenderEvent.class, this::onHudRender);
        listen(EntityRenderStateEvent.class, this::onEntityRenderState);
    }

    private void onEntityRenderState(EntityRenderStateEvent event) {
        if (event.entity() instanceof LivingEntity living && shows(living, Minecraft.getInstance().gameRenderer.mainCamera())) {
            event.state().nameTag = null;
        }
    }

    private void onHudRender(HudRenderEvent event) {
        if (mc.level == null || mc.player == null) return;
        Theme.update();
        Projection projection = Projection.capture();
        Camera camera = mc.gameRenderer.mainCamera();
        List<Tag> tags = new ArrayList<>();
        double limit = (double) range.get() * range.get();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !shows(living, camera)) continue;
            double distanceSq = entity.distanceToSqr(camera.position());
            if (distanceSq > limit) continue;
            Vec3 head = head(living, event.deltaTracker());
            Point anchor = projection.project(head);
            if (anchor != null) tags.add(new Tag(living, anchor, Math.sqrt(distanceSq)));
        }
        tags.sort(Comparator.comparingDouble(Tag::distance).reversed());
        float base = UiScale.current().factor() * scale.get() / 100f;
        for (Tag tag : tags) draw(event.graphics(), tag, base);
    }

    private void draw(GuiGraphicsExtractor graphics, Tag tag, float scale) {
        float textSize = TEXT_SIZE * scale;
        float smallSize = SMALL_SIZE * scale;
        float padding = PADDING * scale;
        float gap = GAP * scale;
        int height = Math.max(1, Math.round(HEIGHT * scale));
        int radius = Math.round(RADIUS * scale);

        String name = tag.entity().getName().getString();
        String hearts = health.get() ? Integer.toString(Mth.ceil(tag.entity().getHealth() + tag.entity().getAbsorptionAmount())) : "";
        String away = showDistance.get() ? Units.current().distance((int) Math.round(tag.distance())) : "";
        float width = padding + Text.width(name, textSize);
        if (!hearts.isEmpty()) width += gap + Text.width(hearts, textSize);
        if (!away.isEmpty()) width += gap + Text.width(away, smallSize);
        width += padding;

        float x = tag.anchor().x() - width / 2f;
        float y = tag.anchor().y() - height;
        float centerY = y + height / 2f;
        Draw.rect(graphics, x, y, width, height, radius, Theme.SIDEBAR);
        float cursor = x + padding;
        Text.drawCentered(graphics, name, cursor, centerY, textSize, Theme.TEXT);
        cursor += Text.width(name, textSize);
        if (!hearts.isEmpty()) {
            cursor += gap;
            Text.drawCentered(graphics, hearts, cursor, centerY, textSize, healthColor(tag.entity()));
            cursor += Text.width(hearts, textSize);
        }
        if (!away.isEmpty()) {
            cursor += gap;
            Text.drawCentered(graphics, away, cursor, centerY, smallSize, Theme.DIM);
        }
    }

    private static int healthColor(LivingEntity entity) {
        float fraction = entity.getMaxHealth() <= 0f ? 1f : Mth.clamp(entity.getHealth() / entity.getMaxHealth(), 0f, 1f);
        return fraction >= 0.5f ? Colors.lerp(HURT, HEALTHY, (fraction - 0.5f) * 2f) : Colors.lerp(DYING, HURT, fraction * 2f);
    }

    private static Vec3 head(LivingEntity entity, DeltaTracker deltaTracker) {
        float partial = deltaTracker.getGameTimeDeltaPartialTick(!mc.level.tickRateManager().isEntityFrozen(entity));
        double x = Mth.lerp(partial, entity.xOld, entity.getX());
        double y = Mth.lerp(partial, entity.yOld, entity.getY());
        double z = Mth.lerp(partial, entity.zOld, entity.getZ());
        return new Vec3(x, y + entity.getBbHeight() + LIFT, z);
    }

    private boolean shows(LivingEntity entity, Camera camera) {
        if (entity == camera.entity() && !camera.isDetached()) return false;
        return targets.accepts(Minecraft.getInstance().player, entity);
    }
}
