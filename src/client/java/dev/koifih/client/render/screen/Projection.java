package dev.koifih.client.render.screen;

import lombok.Getter;
import lombok.experimental.Accessors;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.koifih.client.mixin.accessor.GameRendererAccessor;
import dev.koifih.client.render.Corners;
import dev.koifih.client.render.Point;
import dev.koifih.client.render.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

@Accessors(fluent = true)
public final class Projection implements Projector {
    public static final float NEAR = 0.05f;

    private final Matrix4f viewProjection;
    private final Vec3 origin;
    private final float width;
    private final float height;
    @Getter
    private final float pixel;

    public Projection(Matrix4fc viewProjection, Vec3 origin, float width, float height, float pixel) {
        this.viewProjection = new Matrix4f(viewProjection);
        this.origin = origin;
        this.width = width;
        this.height = height;
        this.pixel = pixel;
    }

    public static Projection capture() {
        GameRenderer renderer = Minecraft.getInstance().gameRenderer;
        GameRenderState state = renderer.gameRenderState();
        CameraRenderState camera = state.levelRenderState.cameraRenderState;
        WindowRenderState window = state.windowRenderState;
        float pixel = 1f / Math.max(1, window.guiScale);
        return new Projection(viewProjection((GameRendererAccessor) renderer, camera, state.optionsRenderState), camera.pos,
                window.width * pixel, window.height * pixel, pixel);
    }

    @Override
    public Point project(double x, double y, double z) {
        Vector4f clip = clip(x, y, z, new Vector4f());
        return inFront(clip) ? toScreen(clip) : null;
    }

    public Rect bounds(AABB box) {
        Vector4f[] corners = new Vector4f[Corners.COUNT];
        for (int i = 0; i < corners.length; i++) {
            corners[i] = clip(Corners.x(box, i), Corners.y(box, i), Corners.z(box, i), new Vector4f());
        }
        Extent extent = new Extent();
        for (Vector4f corner : corners) {
            if (inFront(corner)) extent.add(toScreen(corner));
        }
        for (int i = 0; i < Corners.EDGES.length; i += 2) {
            Vector4f a = corners[Corners.EDGES[i]];
            Vector4f b = corners[Corners.EDGES[i + 1]];
            if (inFront(a) != inFront(b)) extent.add(toScreen(nearIntersection(a, b)));
        }
        return extent.toRect();
    }

    public int framebufferX(float x) {
        return Math.round(x / pixel);
    }

    public int framebufferY(float y) {
        return Math.round((height - y) / pixel);
    }

    private Vector4f clip(double x, double y, double z, Vector4f dest) {
        return viewProjection.transform((float) (x - origin.x), (float) (y - origin.y), (float) (z - origin.z), 1f, dest);
    }

    private Point toScreen(Vector4fc clip) {
        float inverse = 1f / clip.w();
        return new Point((clip.x() * inverse + 1f) * 0.5f * width, (1f - clip.y() * inverse) * 0.5f * height, clip.w());
    }

    private static boolean inFront(Vector4fc clip) {
        return clip.w() > NEAR;
    }

    private static Vector4f nearIntersection(Vector4f a, Vector4f b) {
        return new Vector4f(a).lerp(b, (NEAR - a.w) / (b.w - a.w));
    }

    private static Matrix4f viewProjection(GameRendererAccessor renderer, CameraRenderState camera, OptionsRenderState options) {
        PoseStack bob = new PoseStack();
        renderer.adin$bobHurt(camera, bob);
        if (options.bobView) renderer.adin$bobView(camera, bob);
        return new Matrix4f(camera.projectionMatrix).mul(bob.last().pose()).mul(camera.viewRotationMatrix);
    }
}
