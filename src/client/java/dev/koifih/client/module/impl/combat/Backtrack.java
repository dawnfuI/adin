package dev.koifih.client.module.impl.combat;

import dev.koifih.client.AdinClient;
import dev.koifih.client.backtrack.Backtracker;
import dev.koifih.client.backtrack.TrackedPosition;
import dev.koifih.client.event.events.AttackEvent;
import dev.koifih.client.event.events.PacketProcessEvent;
import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.event.events.WorldRenderEvent;
import dev.koifih.client.module.Module;
import dev.koifih.client.render.Style;
import dev.koifih.client.setting.BacktrackPreview;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.EnumSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.setting.TargetSettings;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Backtrack extends Module {
    private static final String[] MODES = {"Delay", "Freeze"};
    private static final int MODE_DELAY = 0;
    private static final int MODE_FREEZE = 1;
    private static final String[] VISUALIZE = {"Off", "Box", "Both"};
    private static final int VISUALIZE_OFF = 0;
    private static final int VISUALIZE_BOX = 1;
    private static final int VISUALIZE_BOTH = 2;
    private static final long TRACKING_BUFFER_MILLIS = 500L;
    private static final long ATTACK_WINDOW_MILLIS = 1000L;
    private static final long MAX_FREEZE_MILLIS = 1000L;
    private static final int MIN_TICKS = 10;
    private static final int HURT_TIME = 3;
    private static final float LINE_WIDTH = 2f;
    private static final float OUTLINE_WIDTH = LINE_WIDTH + 2f;
    private static final int OUTLINE_COLOR = 0xFF000000;
    private static final float FILL_OPACITY = 0.2f;
    private static final float HALF_LIFE_TICKS = 2f;
    private static final double SETTLED_DISTANCE_SQR = 1.0E-4;

    private final EnumSetting mode = add(new EnumSetting("mode", MODE_DELAY, MODES));
    private final SliderSetting delay = add(new SliderSetting("delay", 120, 0, 1000, Measure.MILLIS));
    private final BoolSetting pauseOnHurt = add(new BoolSetting("pauseOnHurt", false));
    private final TargetSettings targets = add(new TargetSettings());
    private final EnumSetting visualize = add(new EnumSetting("visualize", VISUALIZE_BOX, VISUALIZE));
    private final BacktrackPreview preview = add(new BacktrackPreview("preview", delay::get, this::frozen, this::style));

    private final TrackedPosition tracked = new TrackedPosition();
    private LivingEntity target;
    private long attackedAt;
    private long inRangeAt;
    private LivingEntity shownFor;
    private Vec3 shown;

    public Backtrack() {
        super("backtrack");
        delay.visibleWhen(() -> !frozen());
        pauseOnHurt.visibleWhen(() -> !frozen());
    }

    @Override
    public String info() {
        return frozen() ? mode.selected() : delay.format(delay.get());
    }

    @Override
    protected void onEnable() {
        listen(PacketProcessEvent.class, this::onPacketsProcessed);
        listen(AttackEvent.class, this::onAttack);
        listen(PreTickEvent.class, this::onTick);
        listen(WorldRenderEvent.class, this::onWorldRender);
    }

    @Override
    protected void onDisable() {
        Backtracker.hold(false);
        reset();
    }

    private boolean frozen() {
        return mode.get() == MODE_FREEZE;
    }

    private void onPacketsProcessed(PacketProcessEvent event) {
        Minecraft client = event.client();
        LocalPlayer player = client.player;
        if (client.level == null || player == null || client.getConnection() == null) {
            Backtracker.drop();
            target = null;
            Backtracker.hold(false);
            return;
        }
        boolean hadHeld = Backtracker.isLagging();
        if (target != null) scan(client, player);
        if (holding(client, player)) {
            if (!frozen()) Backtracker.release(delay.get());
        } else if (hadHeld) {
            reset();
        }
        sync(client, player);
    }

    private void scan(Minecraft client, LocalPlayer player) {
        for (Backtracker.Held held : Backtracker.held()) {
            if (held.scanned()) continue;
            held.markScanned();
            Packet<?> packet = held.packet();
            if (Backtracker.forcesRelease(packet)) {
                reset();
                return;
            }
            Vec3 real = tracked.handle(packet, client.level, target);
            if (real != null && !frozen() && approaching(player, real)) Backtracker.releaseThrough(held);
        }
    }

    private void onAttack(AttackEvent event) {
        attackedAt = System.currentTimeMillis();
        if (event.target() instanceof LivingEntity living) processTarget(mc, living);
        sync(mc, mc.player);
    }

    private void onTick(PreTickEvent event) {
        Minecraft client = event.client();
        LocalPlayer player = client.player;
        if (client.level == null || player == null) return;
        LivingEntity enemy = assisted(player);
        if (enemy != null) processTarget(client, enemy);
        sync(client, player);
    }

    private LivingEntity assisted(LocalPlayer player) {
        LivingEntity aimed = AdinClient.MODULES.get(AimAssist.class).target();
        if (aimed != null && reachable(player, aimed)) return aimed;
        LivingEntity crosshair = AdinClient.MODULES.get(Triggerbot.class).target();
        return crosshair != null && reachable(player, crosshair) ? crosshair : null;
    }

    private static boolean assists(LivingEntity entity) {
        return AdinClient.MODULES.get(AimAssist.class).target() == entity
                || AdinClient.MODULES.get(Triggerbot.class).target() == entity;
    }

    private static boolean reachable(LocalPlayer player, LivingEntity entity) {
        return boxedDistanceSqr(entity, entity.position(), player.getEyePosition()) <= square(player.entityInteractionRange());
    }

    private void processTarget(Minecraft client, LivingEntity enemy) {
        LocalPlayer player = client.player;
        if (player == null || (frozen() && target != null) || !shouldBacktrack(player, enemy)) return;
        if (enemy != target) {
            reset();
            tracked.syncTo(enemy);
        }
        target = enemy;
    }

    private void reset() {
        if (mc.player == null) return;

        Backtracker.releaseAll();
        target = null;
    }

    private void sync(Minecraft client, LocalPlayer player) {
        Backtracker.hold(player != null && holding(client, player));
    }

    private boolean holding(Minecraft client, LocalPlayer player) {
        if (target == null || !target.isAlive() || target.level() != client.level) return false;
        if (!shouldBacktrack(player, target)) return false;
        return !frozen() || freezing(player);
    }

    private boolean freezing(LocalPlayer player) {
        return Backtracker.heldMillis() < MAX_FREEZE_MILLIS
                && boxedDistanceSqr(target, tracked.get(), player.getEyePosition()) <= square(player.entityInteractionRange());
    }

    private boolean shouldBacktrack(LocalPlayer player, LivingEntity entity) {
        long now = System.currentTimeMillis();
        boolean inRange = reachable(player, entity);
        if (inRange) inRangeAt = now;
        return (inRange || now - inRangeAt < TRACKING_BUFFER_MILLIS)
                && targets.accepts(player, entity)
                && player.tickCount > MIN_TICKS
                && (now - attackedAt <= ATTACK_WINDOW_MILLIS || assists(entity))
                && !(pauseOnHurt.get() && entity.hurtTime >= HURT_TIME);
    }

    private boolean approaching(LocalPlayer player, Vec3 real) {
        Vec3 eye = player.getEyePosition();
        return boxedDistanceSqr(target, real, eye) < boxedDistanceSqr(target, target.position(), eye);
    }

    private static double boxedDistanceSqr(Entity entity, Vec3 at, Vec3 from) {
        AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
        return box.move(at.subtract(entity.position())).distanceToSqr(from);
    }

    private static double square(double value) {
        return value * value;
    }

    private Style style() {
        int rgb = Colors.opaque(Theme.ACCENT);
        Style style = Style.stroke(rgb, LINE_WIDTH).withOutline(OUTLINE_COLOR, OUTLINE_WIDTH);
        return visualize.get() == VISUALIZE_BOTH ? style.withFill(Colors.withAlpha(rgb, FILL_OPACITY)) : style;
    }

    private void onWorldRender(WorldRenderEvent event) {
        if (visualize.get() == VISUALIZE_OFF) {
            shownFor = null;
            return;
        }
        boolean lagging = target != null && Backtracker.isLagging();
        LivingEntity entity = lagging ? target : shownFor;
        if (entity == null || !entity.isAlive() || entity.level() != event.level()) {
            shownFor = null;
            return;
        }
        Vec3 displayed = entity.getPosition(event.deltaTracker().getGameTimeDeltaPartialTick(true));
        Vec3 goal = lagging && entity == target ? tracked.get() : displayed;
        if (entity != shownFor) {
            shownFor = entity;
            shown = displayed;
        }
        shown = shown.lerp(goal, 1.0 - Math.pow(0.5, event.deltaTracker().getRealtimeDeltaTicks() / HALF_LIFE_TICKS));
        if (goal == displayed && shown.distanceToSqr(displayed) < SETTLED_DISTANCE_SQR) {
            shownFor = null;
            return;
        }
        event.buffer().box(entity.getBoundingBox().move(shown.subtract(entity.position())), style());
    }
}
