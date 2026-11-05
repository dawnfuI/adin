package dev.koifih.client.mixin.render;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.koifih.client.render.entity.EntityFill;
import dev.koifih.client.render.entity.EntityOutline;
import dev.koifih.client.render.entity.EntityOutlines;
import dev.koifih.client.render.entity.Filled;
import dev.koifih.client.render.entity.Ghost;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureRendererMixin<T extends PictureInPictureRenderState> {
    @Shadow
    private GpuTexture texture;
    @Shadow
    private GpuTextureView textureView;

    @Inject(method = "prepare", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"))
    private void adin$inkOutline(T state, GuiRenderState gui, FeatureRenderDispatcher dispatcher, int guiScale, CallbackInfo info) {
        if (!(state instanceof GuiEntityRenderState entity) || !(entity.renderState() instanceof Filled filled)) return;
        EntityFill fill = filled.adin$fill();
        if (fill != null && fill.ghost()) {
            Ghost.shadePreview(textureView, texture.getWidth(0), texture.getHeight(0));
            return;
        }
        EntityOutline outline = filled.adin$outline();
        if (outline == null) return;
        EntityOutlines.PREVIEW.ink(textureView, texture.getWidth(0), texture.getHeight(0), textureView, null, outline);
    }
}
