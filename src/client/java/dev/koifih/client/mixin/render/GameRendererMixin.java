package dev.koifih.client.mixin.render;

import dev.koifih.client.render.entity.EntityOutlines;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V"))
    private void adin$inkOutline(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo info) {
        if (!EntityOutlines.active()) return;
        GameRenderer renderer = (GameRenderer) (Object) this;
        if (!renderer.gameRenderState().levelRenderState.shouldShowEntityOutlines) return;
        EntityOutlines.render(EntityOutlines.mask(),renderer.mainRenderTarget());
    }
}
