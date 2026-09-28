package dev.koifih.client.render.post;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.koifih.Adin;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;
import java.util.Optional;
import java.util.OptionalDouble;

public final class KawaseBlur {
    private static final int CONFIG_SIZE = 16;
    private static final int MAX_PASSES = 8;
    private static final Vector4fc CLEAR = new Vector4f();
    private static final BindGroupLayout CONFIG = BindGroupLayout.builder()
            .withUniform("KawaseConfig", UniformType.UNIFORM_BUFFER)
            .build();
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Adin.id("pipeline/post/kawase"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Adin.id("post/kawase"))
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withBindGroupLayout(CONFIG)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    private final String name;
    private final GpuBuffer[] offsets = new GpuBuffer[MAX_PASSES + 1];
    private RenderTarget down;
    private RenderTarget first;
    private RenderTarget second;
    private int passes;

    public KawaseBlur(String name) {
        this.name = name;
    }

    public static int passes(float radius) {
        return Math.clamp((int) Math.ceil(Math.sqrt(radius)), 1, MAX_PASSES);
    }

    public GpuTextureView blur(CommandEncoder encoder, GpuTextureView source, int width, int height, float radius,
                               ScreenRectangle halfArea) {
        int halfWidth = (width + 1) / 2;
        int halfHeight = (height + 1) / 2;
        down = target(down, "down", halfWidth, halfHeight);
        first = target(first, "first", halfWidth, halfHeight);
        second = target(second, "second", halfWidth, halfHeight);
        upload(encoder, radius);
        pass(encoder, "down", down.getColorTextureView(), halfArea, offsets[0], source);
        GpuTextureView from = down.getColorTextureView();
        for (int i = 1; i <= passes; i++) {
            RenderTarget to = (i & 1) == 1 ? first : second;
            pass(encoder, "pass " + i, to.getColorTextureView(), halfArea, offsets[i], from);
            from = to.getColorTextureView();
        }
        return from;
    }

    public GpuTextureView downsampled() {
        return down.getColorTextureView();
    }

    private void pass(CommandEncoder encoder, String label, GpuTextureView color, ScreenRectangle area, GpuBuffer offset,
                      GpuTextureView source) {
        try (RenderPass pass = encoder.createRenderPass(() -> "adin kawase " + name + " " + label, color,
                Optional.of(CLEAR), null, OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            if (area != null) pass.enableScissor(area.left(), area.top(), area.width(), area.height());
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("KawaseConfig", offset.slice());
            pass.draw(3, 1, 0, 0);
        }
    }

    private void upload(CommandEncoder encoder, float radius) {
        passes = passes(radius);
        for (int i = 0; i <= passes; i++) {
            if (offsets[i] != null) continue;
            int index = i;
            offsets[i] = RenderSystem.getDevice().createBuffer(() -> "adin kawase " + name + " " + index,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CONFIG_SIZE);
            float offset = i == 0 ? 1f : i - 0.5f;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                encoder.writeToBuffer(offsets[i].slice(), Std140Builder.onStack(stack, CONFIG_SIZE)
                        .putVec2(offset, offset).putVec2(0f, 0f).get());
            }
        }
    }

    private RenderTarget target(RenderTarget current, String label, int width, int height) {
        if (current != null && current.width == width && current.height == height) return current;
        if (current != null) current.destroyBuffers();
        return new TextureTarget("adin kawase " + name + " " + label, width, height, false, GpuFormat.RGBA8_UNORM);
    }
}
