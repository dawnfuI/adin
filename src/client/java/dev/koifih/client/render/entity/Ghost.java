package dev.koifih.client.render.entity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.koifih.client.render.post.GhostComposite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Ghost {
    private static final Vector4fc CLEAR = new Vector4f();
    private static final GhostComposite LEVEL = new GhostComposite("level", 48f, false);
    private static final GhostComposite PREVIEW = new GhostComposite("preview", 20f, true);
    private static final List<EntityRenderState> QUEUED = new ArrayList<>();
    private static final SubmitNodeStorage SUBMITS = new SubmitNodeStorage();
    private static final EntityFill MASK = EntityFill.solid(0xFFFFFFFF, 0);

    private static RenderTarget body;
    private static RenderTarget mask;

    public static void queue(EntityRenderState state) {
        prepare(state);
        QUEUED.add(state);
    }

    public static void prepare(EntityRenderState state) {
        ((Filled) state).adin$setFill(EntityFill.GHOST);
        ((Filled) state).adin$setOutline(null);
        state.outlineColor = EntityRenderState.NO_OUTLINE;
        state.nameTag = null;
        state.scoreText = null;
        state.leashStates = null;
        state.displayFireAnimation = false;
        state.shadowPieces.clear();
        if (state instanceof LivingEntityRenderState living) living.headItem.clear();
        if (state instanceof ArmedEntityRenderState armed) {
            armed.rightHandItemState.clear();
            armed.leftHandItemState.clear();
        }
        if (state instanceof AvatarRenderState avatar) {
            avatar.showCape = false;
            avatar.heldOnHead.clear();
            avatar.parrotOnLeftShoulder = null;
            avatar.parrotOnRightShoulder = null;
            avatar.arrowCount = 0;
            avatar.stingerCount = 0;
        }
    }

    public static void shade(GameRenderer renderer) {
        if (QUEUED.isEmpty()) return;
        RenderTarget main = renderer.mainRenderTarget();
        CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
        mask = fit(mask, "adin ghost mask", main);
        body = fit(body, "adin ghost body", main);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(camera.viewRotationMatrix);
        render(renderer, camera, mask, MASK);
        render(renderer, camera, body, EntityFill.GHOST);
        modelView.popMatrix();
        QUEUED.clear();
        LEVEL.draw(body.getColorTextureView(), mask.getColorTextureView(), main.width, main.height,
                main.getColorTextureView());
    }

    public static void shadePreview(GpuTextureView texture, int width, int height) {
        PREVIEW.draw(texture, texture, width, height, texture);
    }

    private static void render(GameRenderer renderer, CameraRenderState camera, RenderTarget target, EntityFill fill) {
        RenderSystem.outputColorTextureOverride = target.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        PoseStack pose = new PoseStack();
        Vec3 origin = camera.pos;
        for (EntityRenderState state : QUEUED) {
            ((Filled) state).adin$setFill(fill);
            dispatcher.submit(state, camera, state.x - origin.x, state.y - origin.y, state.z - origin.z, pose, SUBMITS);
        }
        renderer.featureRenderDispatcher().renderAllFeatures(SUBMITS);
        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;
    }

    private static RenderTarget fit(RenderTarget target, String label, RenderTarget main) {
        if (target == null) target = new TextureTarget(label, main.width, main.height, true, GpuFormat.RGBA8_UNORM);
        if (target.width != main.width || target.height != main.height) target.resize(main.width, main.height);
        RenderSystem.getDevice().createCommandEncoder()
                .clearColorAndDepthTextures(target.getColorTexture(), CLEAR, target.getDepthTexture(), 0.0);
        target.copyDepthFrom(main);
        return target;
    }
}
