package dev.koifih.client.rotation;

import dev.koifih.client.AdinClient;
import dev.koifih.client.event.Priority;
import dev.koifih.client.event.events.EntityRenderStateEvent;
import dev.koifih.client.event.events.TickEvent;
import dev.koifih.client.event.events.TurnEvent;
import dev.koifih.client.module.impl.movement.MoveFix;
import dev.koifih.client.util.Entities;
import dev.koifih.client.util.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class RotationManager {
    private static final float TURN_FACTOR = 0.15f;

    private record Request(RotationTarget target, int priority, RotationConfig config) {}

    private Request request;
    private Request visible;
    private Rotation current;
    private Rotation previous;
    private RotationConfig config;
    private Smoothing smoothing;
    private Rotator rotator;
    private Rotation lastSent = new Rotation(0f, 0f);

    public void init() {
        AdinClient.EVENTS.subscribe(TickEvent.class, Priority.LOWEST, this::onTick);
        AdinClient.EVENTS.subscribe(TurnEvent.class, this::onTurn);
        AdinClient.EVENTS.subscribe(EntityRenderStateEvent.class, this::onRenderState);
    }

    public void aim(RotationTarget target, int priority, RotationConfig config) {
        if (request == null || priority > request.priority()) request = new Request(target, priority, config);
    }

    public void aim(Rotation target, int priority, RotationConfig config) {
        aim(RotationTarget.fixed(target), priority, config);
    }

    public void face(LocalPlayer player, Rotation target, int priority, RotationConfig config) {
        if (request != null && priority < request.priority()) return;
        request = new Request(RotationTarget.fixed(target), priority, config);
        if (config.silent()) {
            current = distinct(quantized(lastSent.moved(lastSent.yawTo(target), lastSent.pitchTo(target))));
            return;
        }
        Rotation actual = Rotation.of(player);
        player.turn(actual.yawTo(target) / TURN_FACTOR, actual.pitchTo(target) / TURN_FACTOR);
    }

    public boolean active() {
        return current != null;
    }

    public Rotation rotation(LocalPlayer player) {
        return current != null ? current : Rotation.of(player);
    }

    public Rotation rendered(float partialTick) {
        if (current == null) return null;
        Rotation from = previous != null ? previous : current;
        return new Rotation(Mth.rotLerp(partialTick, from.yaw(), current.yaw()), Mth.lerp(partialTick, from.pitch(), current.pitch()));
    }

    public float sentYaw(float actual) {
        return current != null ? current.yaw() : actual;
    }

    public float sentPitch(float actual) {
        return current != null ? current.pitch() : actual;
    }

    public void sent(Rotation rotation) {
        if (!rotation.equals(lastSent)) Placement.rotated(Math.abs(rotation.yaw() - lastSent.yaw()));
        lastSent = rotation;
    }

    public Rotation lastSent() {
        return lastSent;
    }

    public Vec3 viewVector(Entity entity) {
        return current != null && Entities.isLocal(entity) ? current.direction() : null;
    }

    public float movementYaw(Entity entity, float actual) {
        return correctsMovement(entity) ? current.yaw() : actual;
    }

    public float movementPitch(Entity entity, float actual) {
        return correctsMovement(entity) ? current.pitch() : actual;
    }

    public Vec3 movementLook(Entity entity, Vec3 actual) {
        return correctsMovement(entity) ? current.direction() : actual;
    }

    private boolean correctsMovement(Entity entity) {
        return current != null && moveFix().corrects() && Entities.isLocal(entity);
    }

    public Vec2 correctInput(Vec2 input, float actualYaw) {
        if (current == null || !moveFix().correctsInput() || input.lengthSquared() < 1.0E-8f) return input;
        double theta = Math.toRadians(actualYaw - current.yaw());
        float sin = (float) Math.sin(theta);
        float cos = (float) Math.cos(theta);
        return new Vec2(snap(input.x * cos - input.y * sin), snap(input.y * cos + input.x * sin)).normalized();
    }

    public void sync(Rotation rotation) {
        lastSent = rotation;
        if (current == null) return;
        current = rotation;
        previous = rotation;
        rotator = null;
    }

    private static MoveFix moveFix() {
        return AdinClient.MODULES.get(MoveFix.class);
    }

    private Rotation distinct(Rotation rotation) {
        return Placement.duplicates(Math.abs(rotation.yaw() - lastSent.yaw())) ? rotation.moved(gcd(), 0f) : rotation;
    }

    private Rotation quantized(Rotation rotation) {
        return new Rotation(quantized(rotation.yaw(), lastSent.yaw()),
                Mth.clamp(quantized(rotation.pitch(), lastSent.pitch()), -90f, 90f));
    }

    private static float quantized(float value, float base) {
        float gcd = gcd();
        return base + Math.round((value - base) / gcd) * gcd;
    }

    private static float snap(float value) {
        return Math.signum(value) * Math.round(Math.abs(value));
    }

    private void onTick(TickEvent event) {
        LocalPlayer player = event.client().player;
        Request request = this.request;
        this.request = null;
        visible = null;
        previous = current;
        if (player == null) {
            current = null;
            previous = null;
            rotator = null;
            return;
        }
        Rotation actual = Rotation.of(player);
        Rotation from = current != null ? current : actual;
        if (request != null) {
            config = request.config();
            if (config.silent()) {
                current = distinct(quantized(step(from, request.target().at(1f), 1f)));
            } else {
                current = null;
                visible = request;
            }
        } else if (current != null) {
            Rotation next = quantized(step(from, actual, 1f));
            current = next.distanceTo(actual) < gcd() ? null : next;
        } else {
            rotator = null;
        }
    }

    private void onTurn(TurnEvent event) {
        LocalPlayer player = event.client().player;
        if (visible == null || player == null) return;
        float partialTick = event.client().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Rotation actual = Rotation.of(player);
        Rotation next = step(actual, visible.target().at(partialTick), event.deltaTicks());
        player.turn(actual.yawTo(next) / TURN_FACTOR, actual.pitchTo(next) / TURN_FACTOR);
    }

    private void onRenderState(EntityRenderStateEvent event) {
        if (!(event.state() instanceof LivingEntityRenderState state) || !Entities.isLocal(event.entity())) return;
        Rotation rotation = rendered(event.partialTicks());
        if (rotation == null) return;
        state.bodyRot = rotation.yaw();
        state.yRot = 0f;
        state.xRot = rotation.pitch();
    }

    private Rotation step(Rotation from, Rotation target, float deltaTicks) {
        if (rotator == null || smoothing != config.smoothing()) {
            smoothing = config.smoothing();
            rotator = smoothing.create();
        }
        return rotator.step(from, target, config, deltaTicks);
    }

    private static float gcd() {
        float factor = (float) (Minecraft.getInstance().options.sensitivity().get() * 0.6 + 0.2);
        return factor * factor * factor * 8f * TURN_FACTOR;
    }
}
