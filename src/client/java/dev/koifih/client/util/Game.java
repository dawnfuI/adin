package dev.koifih.client.util;

import dev.koifih.client.mixin.accessor.MinecraftAccessor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.EntityHitResult;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Game {
    public static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    public static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    public static boolean playing() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.gui.screen() == null && client.mouseHandler.isMouseGrabbed();
    }

    public static EntityHitResult crosshair() {
        Minecraft client = Minecraft.getInstance();
        ((MinecraftAccessor) client).adin$pick(1f);
        return client.hitResult instanceof EntityHitResult hit ? hit : null;
    }
}
