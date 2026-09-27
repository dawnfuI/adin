package dev.koifih.client.mixin.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.koifih.client.AdinClient;
import dev.koifih.client.event.events.PacketProcessEvent;
import dev.koifih.client.module.impl.combat.AimAssist;
import dev.koifih.client.util.Clicks;
import dev.koifih.client.util.Hotbar;
import dev.koifih.client.util.Players;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow public HitResult hitResult;
    @Shadow public MultiPlayerGameMode gameMode;
    @Shadow public LocalPlayer player;
    @Unique private boolean adin$modulesActed;
    @Unique private boolean adin$attacked;
    @Unique private boolean adin$used;

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketProcessor;processQueuedPackets()V", shift = At.Shift.AFTER))
    private void adin$packetsProcessed(boolean renderLevel, CallbackInfo info) {
        AdinClient.EVENTS.post(new PacketProcessEvent((Minecraft) (Object) this));
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void adin$handleKeybinds(CallbackInfo info) {
        adin$modulesActed = Hotbar.acted();
        adin$attacked = false;
        adin$used = false;
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void adin$handledKeybinds(CallbackInfo info) {
        adin$modulesActed = false;
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void adin$startAttack(CallbackInfoReturnable<Boolean> info) {
        boolean repeated = adin$attacked && Clicks.simulating(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        adin$attacked = true;
        if (adin$modulesActed || repeated || adin$blocksBreaking() || adin$activates(GLFW.GLFW_MOUSE_BUTTON_LEFT)) info.setReturnValue(false);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void adin$startUseItem(CallbackInfo info) {
        boolean repeated = adin$used && Clicks.simulating(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        adin$used = true;
        if (adin$modulesActed || repeated || (adin$activates(GLFW.GLFW_MOUSE_BUTTON_RIGHT) && !Players.consuming(player))) info.cancel();
    }

    @Unique
    private static boolean adin$activates(int button) {
        return !Clicks.simulating(button) && AdinClient.MODULES.activates(InputConstants.Type.MOUSE.getOrCreate(button));
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void adin$continueAttack(boolean leftClick, CallbackInfo info) {
        if (adin$modulesActed || adin$blocksBreaking()) info.cancel();
    }

    @Unique
    private boolean adin$blocksBreaking() {
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) return false;
        if (!AdinClient.MODULES.get(AimAssist.class).blocksBreaking()) return false;
        if (gameMode != null) gameMode.stopDestroyBlock();
        return true;
    }
}
