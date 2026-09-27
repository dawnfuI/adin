package dev.koifih.client.module.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import dev.koifih.client.event.events.AttackEvent;
import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.mixin.accessor.KeyMappingAccessor;
import dev.koifih.client.module.Module;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.EnumSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.util.Game;
import dev.koifih.client.util.Time;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

public final class SprintReset extends Module {
    private static final String[] MODES = {"W Tap", "S Tap"};
    private static final int W_TAP = 0;

    private final EnumSetting mode = add(new EnumSetting("mode", W_TAP, MODES));
    private final SliderSetting interval = add(new SliderSetting("interval", 100, 50, 500, Measure.MILLIS));
    private final BoolSetting groundOnly = add(new BoolSetting("groundOnly", true));
    private final BoolSetting sprintOnly = add(new BoolSetting("sprintOnly", true));
    private final Time.Stopwatch stopwatch = new Time.Stopwatch();
    private boolean resetting;

    public SprintReset() {
        super("sprintReset");
    }

    @Override
    public String info() {
        return mode.selected();
    }

    @Override
    protected void onEnable() {
        listen(AttackEvent.class, this::onAttack);
        listen(PreTickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        stop();
    }

    private void onAttack(AttackEvent event) {
        LocalPlayer player = mc.player;
        if (event.player() != player) return;
        if (groundOnly.get() && !player.onGround()) return;
        if (sprintOnly.get() && !player.isSprinting()) return;
        resetting = true;
        stopwatch.reset();
    }

    private void onTick(PreTickEvent event) {
        if (!resetting) return;
        if (!Game.playing(event.client()) || stopwatch.elapsed(interval.get())) {
            stop();
            return;
        }
        if (mode.get() == W_TAP) mc.options.keyUp.setDown(false);
        else mc.options.keyDown.setDown(true);
    }

    private void stop() {
        if (!resetting) return;
        resetting = false;
        restore(mc.options.keyUp);
        restore(mc.options.keyDown);
    }

    private static void restore(KeyMapping mapping) {
        InputConstants.Key key = ((KeyMappingAccessor) mapping).adin$getKey();
        long window = mc.getWindow().handle();
        boolean held = switch (key.getType()) {
            case KEYSYM -> GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
        mapping.setDown(held && mc.gui.screen() == null);
    }
}
