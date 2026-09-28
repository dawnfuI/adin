package dev.koifih.client.util;

import dev.koifih.client.AdinClient;
import dev.koifih.client.event.Priority;
import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.mixin.accessor.KeyMappingAccessor;
import dev.koifih.client.mixin.accessor.KeyboardHandlerAccessor;
import dev.koifih.client.mixin.accessor.MouseHandlerAccessor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Clicks {
    public static boolean simulate;
    private static final boolean[] held = new boolean[2];

    public static void init() {
        AdinClient.EVENTS.subscribe(PreTickEvent.class, Priority.HIGHEST, event -> release(event.client()));
    }

    public static boolean left(Minecraft client, Entity target) {
        return client.hitResult instanceof EntityHitResult hit && hit.getEntity() == target && press(client, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    public static boolean right(Minecraft client, BlockHitResult target) {
        return client.hitResult instanceof BlockHitResult hit && hit.getDirection() == target.getDirection() && right(client, target.getBlockPos());
    }

    public static boolean right(Minecraft client, BlockPos target) {
        return client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(target) && press(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }

    public static boolean right(Minecraft client) {
        return client.hitResult != null && client.hitResult.getType() == HitResult.Type.MISS && press(client, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }

    public static boolean hotbar(Minecraft client, int slot) {
        if (!simulate || client.gui.screen() == null) return false;
        InputConstants.Key key = ((KeyMappingAccessor) client.options.keyHotbarSlots[slot]).adin$getKey();
        KeyEvent event = switch (key.getType()) {
            case KEYSYM -> new KeyEvent(key.getValue(), 0, 0);
            case SCANCODE -> new KeyEvent(InputConstants.UNKNOWN.getValue(), key.getValue(), 0);
            default -> null;
        };
        if (event == null) return false;
        KeyboardHandlerAccessor keyboard = (KeyboardHandlerAccessor) client.keyboardHandler;
        long window = client.getWindow().handle();
        keyboard.adin$keyPress(window, GLFW.GLFW_PRESS, event);
        keyboard.adin$keyPress(window, GLFW.GLFW_RELEASE, event);
        return true;
    }

    public static boolean simulating(int button) {
        return button >= 0 && button < held.length && held[button];
    }

    private static boolean press(Minecraft client, int button) {
        if (!simulate || client.gui.screen() != null || !client.mouseHandler.isMouseGrabbed()) return false;
        if (held[button]) send(client, button, GLFW.GLFW_RELEASE);
        held[button] = true;
        send(client, button, GLFW.GLFW_PRESS);
        return true;
    }

    private static void release(Minecraft client) {
        for (int button = 0; button < held.length; button++) {
            if (!held[button]) continue;
            held[button] = false;
            send(client, button, GLFW.GLFW_RELEASE);
        }
    }

    private static void send(Minecraft client, int button, int action) {
        ((MouseHandlerAccessor) client.mouseHandler).adin$onButton(client.getWindow().handle(), new MouseButtonInfo(button, 0), action);
    }
}
