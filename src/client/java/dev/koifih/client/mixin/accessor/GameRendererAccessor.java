package dev.koifih.client.mixin.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("hudProjection")
    Projection adin$getHudProjection();

    @Invoker("bobHurt")
    void adin$bobHurt(CameraRenderState cameraState, PoseStack poseStack);

    @Invoker("bobView")
    void adin$bobView(CameraRenderState cameraState, PoseStack poseStack);
}
