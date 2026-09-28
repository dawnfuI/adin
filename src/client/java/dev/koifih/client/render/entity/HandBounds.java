package dev.koifih.client.render.entity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.koifih.client.mixin.accessor.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HandBounds {
    private static final float NEAR = 1e-4f;
    private static final int BLUR_SLACK = 2;

    private static final Matrix4f clip = new Matrix4f();
    private static final Matrix4f local = new Matrix4f();
    private static final Vector4f point = new Vector4f();
    private static float minX;
    private static float minY;
    private static float maxX;
    private static float maxY;
    private static boolean any;
    private static boolean behind;

    public static void begin() {
        ((GameRendererAccessor) Minecraft.getInstance().gameRenderer).adin$getHudProjection().getMatrix(clip)
                .mul(RenderSystem.getModelViewStack());
        minX = minY = Float.POSITIVE_INFINITY;
        maxX = maxY = Float.NEGATIVE_INFINITY;
        any = false;
        behind = false;
    }

    public static void include(Model<?> model, PoseStack pose) {
        model.root().getExtentsForGui(pose, corner -> add(clip, corner.x(), corner.y(), corner.z()));
    }

    public static void include(List<BakedQuad> quads, PoseStack pose) {
        local.set(clip).mul(pose.last().pose());
        for (BakedQuad quad : quads) {
            for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
                var position = quad.position(i);
                add(local, position.x(), position.y(), position.z());
            }
        }
    }

    public static void end(EntityOutline outline) {
        if (!any) return;
        RenderTarget mask = EntityOutlines.mask();
        if (behind) {
            EntityOutlines.include(0, 0, mask.width, mask.height);
            return;
        }
        int slack = (int) Math.ceil(BLUR_SLACK * outline.reach()) + BLUR_SLACK;
        EntityOutlines.include((int) Math.floor((minX * 0.5f + 0.5f) * mask.width) - slack,
                (int) Math.floor((minY * 0.5f + 0.5f) * mask.height) - slack,
                (int) Math.ceil((maxX * 0.5f + 0.5f) * mask.width) + slack,
                (int) Math.ceil((maxY * 0.5f + 0.5f) * mask.height) + slack);
    }

    private static void add(Matrix4f transform, float x, float y, float z) {
        transform.transform(point.set(x, y, z, 1f));
        any = true;
        if (point.w <= NEAR) {
            behind = true;
            return;
        }
        float ndcX = point.x / point.w;
        float ndcY = point.y / point.w;
        minX = Math.min(minX, ndcX);
        minY = Math.min(minY, ndcY);
        maxX = Math.max(maxX, ndcX);
        maxY = Math.max(maxY, ndcY);
    }
}
