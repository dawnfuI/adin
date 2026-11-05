package dev.koifih.client.mixin.render;

import dev.koifih.client.render.entity.EntityOutlines;
import dev.koifih.client.render.entity.Ghost;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
    private void adin$shadeGhost(DeltaTracker deltaTracker, CallbackInfo info) {
        Ghost.shade((GameRenderer) (Object) this);
    }

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V"))
    private void adin$inkOutline(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo info) {
        if (!EntityOutlines.active()) return;
        GameRenderer renderer = (GameRenderer) (Object) this;
        if (!renderer.gameRenderState().levelRenderState.shouldShowEntityOutlines) return;
        EntityOutlines.render(EntityOutlines.mask(),renderer.mainRenderTarget());
    }
}
