package dev.koifih.client.module.impl.combat;

import dev.koifih.client.AdinClient;
import dev.koifih.client.backtrack.Backtracker;
import dev.koifih.client.backtrack.TrackedEntity;
import dev.koifih.client.event.events.AttackEvent;
import dev.koifih.client.event.events.PacketProcessEvent;
import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.event.events.WorldRenderEvent;
import dev.koifih.client.module.Module;
import dev.koifih.client.render.entity.Ghost;
import dev.koifih.client.setting.BacktrackPreview;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.EnumSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.setting.TargetSettings;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Backtrack extends Module {
    private static final String[] MODES = {"Delay", "Freeze"};
    private static final int MODE_DELAY = 0;
    private static final int MODE_FREEZE = 1;
    private static final long TRACKING_BUFFER_MILLIS = 500L;
    private static final long ATTACK_WINDOW_MILLIS = 1000L;
    private static final int MIN_TICKS = 10;
    private static final int HURT_TIME = 3;
    private static final float HALF_LIFE_TICKS = 2f;
    private static final double SETTLED_DISTANCE_SQR = 1.0E-4;

    private final EnumSetting mode = add(new EnumSetting("mode", MODE_DELAY, MODES));
    private final SliderSetting delay = add(new SliderSetting("delay", 120, 0, 1000, Measure.MILLIS));
    private final BoolSetting pauseOnHurt = add(new BoolSetting("pauseOnHurt", false));
    private final TargetSettings targets = add(new TargetSettings());
    private final BoolSetting visualize = add(new BoolSetting("visualize", true));
    private final BacktrackPreview preview = add(new BacktrackPreview("preview", delay::get, this::frozen));

    private final TrackedEntity tracked = new TrackedEntity();
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
        release();
    }

    private boolean frozen() {
        return mode.get() == MODE_FREEZE;
    }

    private void onPacketsProcessed(PacketProcessEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            target = null;
            Backtracker.drop();
            return;
        }
        if (target != null) scan(player);
        if (!updateHold(player)) release();
        else if (!frozen()) Backtracker.release(delay.get());
    }

    private void scan(LocalPlayer player) {
        for (Backtracker.Held held : Backtracker.held()) {
            if (held.scanned()) continue;
            held.markScanned();
            Packet<?> packet = held.packet();
            if (Backtracker.forcesRelease(packet) || Backtracker.removes(packet, target)) {
                release();
                return;
            }
            Vec3 real = tracked.handle(packet, target);
            if (real != null && !frozen() && approaching(player, real)) Backtracker.releaseThrough(held);
        }
    }

    private void onAttack(AttackEvent event) {
        attackedAt = System.currentTimeMillis();
        if (event.target() instanceof LivingEntity living) acquire(mc.player, living);
        updateHold(mc.player);
    }

    private void onTick(PreTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        LivingEntity enemy = assisted(player);
        if (enemy != null) acquire(player, enemy);
        if (target != null) tracked.tick(target);
        updateHold(player);
    }

    private void acquire(LocalPlayer player, LivingEntity enemy) {
        if (enemy == target || (frozen() && target != null) || !shouldBacktrack(player, enemy)) return;
        release();
        tracked.syncTo(enemy);
        target = enemy;
    }

    private boolean updateHold(LocalPlayer player) {
        boolean holding = target != null && target.isAlive() && target.level() == mc.level
                && (frozen() || shouldBacktrack(player, target));
        Backtracker.hold(holding);
        return holding;
    }

    private void release() {
        Backtracker.releaseAll();
        target = null;
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
        return player.isWithinEntityInteractionRange(entity.getBoundingBox().inflate(entity.getPickRadius()), 0);
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

    private void onWorldRender(WorldRenderEvent event) {
        boolean lagging = target != null && Backtracker.isLagging();
        LivingEntity entity = lagging ? target : shownFor;
        if (!visualize.get() || entity == null || !entity.isAlive() || entity.level() != event.level()) {
            shownFor = null;
            return;
        }
        float partialTick = event.deltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 displayed = entity.getPosition(partialTick);
        Vec3 goal = lagging ? tracked.position() : displayed;
        if (entity != shownFor) {
            shownFor = entity;
            shown = displayed;
        }
        shown = shown.lerp(goal, 1.0 - Math.pow(0.5, event.deltaTracker().getRealtimeDeltaTicks() / HALF_LIFE_TICKS));
        if (!lagging && shown.distanceToSqr(displayed) < SETTLED_DISTANCE_SQR) {
            shownFor = null;
            return;
        }
        EntityRenderState ghost = mc.getEntityRenderDispatcher().extractEntity(entity, partialTick);
        ghost.x = shown.x;
        ghost.y = shown.y;
        ghost.z = shown.z;
        if (lagging) tracked.pose(ghost, partialTick);
        Ghost.queue(ghost);
    }
}
