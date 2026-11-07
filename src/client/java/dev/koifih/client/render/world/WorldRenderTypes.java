package dev.koifih.client.render.world;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import dev.koifih.Adin;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorldRenderTypes {
    private static final DepthStencilState SEE_THROUGH = new DepthStencilState(CompareOp.ALWAYS_PASS, false);

    private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Adin.id("pipeline/world_fill"))
            .withDepthStencilState(SEE_THROUGH)
            .withCull(false)
            .build());
    private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Adin.id("pipeline/world_lines"))
            .withDepthStencilState(SEE_THROUGH)
            .build());

    public static final RenderType FILL = RenderType.create(Adin.MOD_ID + ":world_fill",
            RenderSetup.builder(FILL_PIPELINE).sortOnUpload().createRenderSetup());
    public static final RenderType LINES = RenderType.create(Adin.MOD_ID + ":world_lines",
            RenderSetup.builder(LINES_PIPELINE).createRenderSetup());
}
