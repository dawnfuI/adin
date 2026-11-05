package dev.koifih.client.render.post;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.koifih.Adin;
import net.minecraft.resources.Identifier;
import java.util.Optional;
import java.util.OptionalDouble;

public final class GhostComposite {
    private static final float BODY_RADIUS = 6f;
    private static final BindGroupLayout SAMPLERS = BindGroupLayout.builder()
            .withSampler("BodySampler")
            .withSampler("CoverageSampler")
            .withSampler("ShadowSampler")
            .build();
    private static final RenderPipeline OVERLAY = pipeline("overlay",
            new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA), true);
    private static final RenderPipeline REPLACE = pipeline("replace", ColorTargetState.DEFAULT, false);

    private final float shadowRadius;
    private final RenderPipeline pipeline;
    private final KawaseBlur body;
    private final KawaseBlur coverage;
    private final KawaseBlur shadow;

    public GhostComposite(String name, float shadowRadius, boolean replace) {
        this.shadowRadius = shadowRadius;
        pipeline = replace ? REPLACE : OVERLAY;
        body = new KawaseBlur("ghost " + name + " body");
        coverage = new KawaseBlur("ghost " + name + " coverage");
        shadow = new KawaseBlur("ghost " + name + " shadow");
    }

    public void draw(GpuTextureView source, GpuTextureView mask, int width, int height, GpuTextureView target) {
        RenderSystem.assertOnRenderThread();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuTextureView blurredBody = body.blur(encoder, source, width, height, BODY_RADIUS, null);
        GpuTextureView blurredCoverage = mask == source ? blurredBody
                : coverage.blur(encoder, mask, width, height, BODY_RADIUS, null);
        GpuTextureView blurredShadow = shadow.blur(encoder, mask, width, height, shadowRadius, null);
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = encoder.createRenderPass(() -> "adin ghost composite", target, Optional.empty(), null,
                OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("BodySampler", blurredBody, sampler);
            pass.bindTexture("CoverageSampler", blurredCoverage, sampler);
            pass.bindTexture("ShadowSampler", blurredShadow, sampler);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static RenderPipeline pipeline(String name, ColorTargetState color, boolean separateMask) {
        return RenderPipeline.builder()
                .withLocation(Adin.id("pipeline/post/ghost_" + name))
                .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
                .withFragmentShader(Adin.id("post/ghost"))
                .withShaderDefine("SEPARATE_MASK", separateMask ? 1 : 0)
                .withBindGroupLayout(SAMPLERS)
                .withColorTargetState(color)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build();
    }
}
