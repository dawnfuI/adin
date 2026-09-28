package dev.koifih.client.render.entity;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.koifih.Adin;
import dev.koifih.client.mixin.accessor.LevelRendererAccessor;
import dev.koifih.client.render.post.KawaseBlur;
import dev.koifih.client.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import java.util.Optional;
import java.util.OptionalDouble;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class EntityOutlines {
    public static final EntityOutlines LEVEL = new EntityOutlines("level", true);
    public static final EntityOutlines PREVIEW = new EntityOutlines("preview", false);

    private static final int CONFIG_SIZE = 48;
    private static final int SCALE_STEPS = 63;
    private static final int SCALE_BITS = 0x030303;
    private static final BindGroupLayout CONFIG = BindGroupLayout.builder()
            .withUniform("OutlineConfig", UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout GLOW_SAMPLERS = BindGroupLayout.builder()
            .withSampler("InSampler")
            .withSampler("MaskSampler")
            .build();
    private static final RenderPipeline SCAN = pipeline("outline_scan", ColorTargetState.DEFAULT, BindGroupLayouts.IN_SAMPLER, CONFIG);
    private static final RenderPipeline INK = pipeline("outline", new ColorTargetState(BlendFunction.TRANSLUCENT), BindGroupLayouts.IN_SAMPLER, CONFIG);
    private static final BlendFunction SCREEN = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_COLOR);
    private static final RenderPipeline GLOW = pipeline("glow", new ColorTargetState(SCREEN), GLOW_SAMPLERS, CONFIG);

    private static EntityOutline level;
    private static EntityOutline hand;
    private static Area region;

    private record Area(int x0, int y0, int x1, int y1) {
        Area union(int x0, int y0, int x1, int y1) {
            return new Area(Math.min(this.x0, x0), Math.min(this.y0, y0), Math.max(this.x1, x1), Math.max(this.y1, y1));
        }

        Area pad(int x, int y, int width, int height) {
            return new Area(Math.max(0, x0 - x), Math.max(0, y0 - y), Math.min(width, x1 + x), Math.min(height, y1 + y));
        }

        Area half() {
            return new Area(x0 / 2, y0 / 2, (x1 + 1) / 2, (y1 + 1) / 2);
        }

        ScreenRectangle rectangle() {
            return new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
        }

        boolean isEmpty() {
            return x1 <= x0 || y1 <= y0;
        }
    }

    private record Binding(String name, GpuTextureView view, GpuSampler sampler) {}

    private final String name;
    private final boolean scaled;
    private GpuBuffer config;
    private RenderTarget scratch;
    private KawaseBlur blur;
    private EntityOutline uploaded;

    public static boolean active() {
        return level != null;
    }

    public static void configure(EntityOutline outline) {
        level = outline;
        if (outline == null) region = null;
    }

    public static int scaled(int color, float scale) {
        int code = Math.round((1f - Math.clamp(scale, 0f, 1f)) * SCALE_STEPS);
        return color & ~SCALE_BITS | (code >> 4 & 3) << 16 | (code >> 2 & 3) << 8 | code & 3;
    }

    public static void include(int x0, int y0, int x1, int y1) {
        region = region == null ? new Area(x0, y0, x1, y1) : region.union(x0, y0, x1, y1);
    }

    public static void configureHand(EntityOutline outline) {
        hand = outline;
    }

    public static EntityOutline hand() {
        return hand;
    }

    public static RenderTarget mask() {
        return ((LevelRendererAccessor) Minecraft.getInstance().levelRenderer).adin$getEntityOutlineTarget();
    }

    public static void render(RenderTarget mask, RenderTarget main) {
        Area area = region;
        region = null;
        if (area == null) return;
        int margin = (int) Math.ceil(level.reach()) + 1;
        Area ink = area.pad(margin, margin, mask.width, mask.height);
        if (ink.isEmpty()) return;
        LEVEL.ink(mask.getColorTextureView(), mask.width, mask.height, main.getColorTextureView(),
                main.getDepthTextureView(), level.withColor(0), ink, ink.pad(margin, margin, mask.width, mask.height));
    }

    public void ink(GpuTextureView mask, int width, int height, GpuTextureView target, GpuTextureView depth,
                    EntityOutline outline) {
        ink(mask, width, height, target, depth, outline, null, null);
    }

    private void ink(GpuTextureView mask, int width, int height, GpuTextureView target, GpuTextureView depth,
                     EntityOutline outline, Area inkArea, Area readArea) {
        RenderSystem.assertOnRenderThread();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        upload(encoder, outline);
        if (outline.glow() > 0f) glow(encoder, mask, width, height, target, depth, outline, inkArea, readArea);
        scratch = target(scratch, "scratch", width, height);
        pass(encoder, "scan", SCAN, scratch.getColorTextureView(), null, readArea,
                new Binding("InSampler", mask, nearest()));
        pass(encoder, "ink", INK, target, depth, inkArea,
                new Binding("InSampler", scratch.getColorTextureView(), nearest()));
    }

    private void glow(CommandEncoder encoder, GpuTextureView mask, int width, int height, GpuTextureView target,
                      GpuTextureView depth, EntityOutline outline, Area inkArea, Area readArea) {
        if (blur == null) blur = new KawaseBlur("outline " + name);
        ScreenRectangle half = readArea == null ? null : readArea.half().rectangle();
        GpuTextureView blurred = blur.blur(encoder, mask, width, height, outline.radius(), half);
        pass(encoder, "glow", GLOW, target, depth, inkArea,
                new Binding("InSampler", blurred, linear()), new Binding("MaskSampler", blur.downsampled(), linear()));
    }

    private void pass(CommandEncoder encoder, String label, RenderPipeline pipeline, GpuTextureView color,
                      GpuTextureView depth, Area area, Binding... textures) {
        try (RenderPass pass = encoder.createRenderPass(() -> "adin outline " + name + " " + label, color,
                Optional.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            if (area != null) pass.enableScissor(area.x0(), area.y0(), area.x1() - area.x0(), area.y1() - area.y0());
            RenderSystem.bindDefaultUniforms(pass);
            for (Binding texture : textures) pass.bindTexture(texture.name(), texture.view(), texture.sampler());
            pass.setUniform("OutlineConfig", config.slice());
            pass.draw(3, 1, 0, 0);
        }
    }

    private void upload(CommandEncoder encoder, EntityOutline outline) {
        if (config == null) config = buffer("config", CONFIG_SIZE);
        if (outline.equals(uploaded)) return;
        int color = outline.color();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            encoder.writeToBuffer(config.slice(), Std140Builder.onStack(stack, CONFIG_SIZE)
                    .putFloat(outline.width())
                    .align(16)
                    .putVec4(Colors.red(color) / 255f, Colors.green(color) / 255f, Colors.blue(color) / 255f,
                            Colors.alpha(color) / 255f)
                    .putFloat(outline.fill()).putFloat(outline.glow()).putFloat(scaled ? 1f : 0f)
                    .get());
        }
        uploaded = outline;
    }

    private GpuBuffer buffer(String label, int size) {
        return RenderSystem.getDevice().createBuffer(() -> "adin outline " + name + " " + label,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
    }

    private RenderTarget target(RenderTarget current, String label, int width, int height) {
        if (current != null && current.width == width && current.height == height) return current;
        if (current != null) current.destroyBuffers();
        return new TextureTarget("adin outline " + name + " " + label, width, height, false, GpuFormat.RGBA8_UNORM);
    }

    private static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    private static GpuSampler linear() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    private static RenderPipeline pipeline(String fragmentShader, ColorTargetState color, BindGroupLayout samplers,
                                           BindGroupLayout uniforms) {
        return RenderPipeline.builder()
                .withLocation(Adin.id("pipeline/post/" + fragmentShader))
                .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
                .withFragmentShader(Adin.id("post/" + fragmentShader))
                .withBindGroupLayout(samplers)
                .withBindGroupLayout(uniforms)
                .withColorTargetState(color)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build();
    }
}
