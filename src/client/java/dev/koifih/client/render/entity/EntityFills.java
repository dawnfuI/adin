package dev.koifih.client.render.entity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.koifih.Adin;
import dev.koifih.client.mixin.accessor.RenderSetupAccessor;
import dev.koifih.client.mixin.accessor.RenderTypeAccessor;
import dev.koifih.client.mixin.accessor.TextureBindingAccessor;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EntityFills {
    public static final int EFFECTS = 6;
    public static final int GHOST = EFFECTS + 1;

    private static final String TEXTURE = "Sampler0";
    private static final ColorTargetState TRANSLUCENT = new ColorTargetState(BlendFunction.TRANSLUCENT);
    private static final RenderPipeline[] OCCLUDED = pipelines("occluded", ColorTargetState.DEFAULT,
            new DepthStencilState(CompareOp.LESS_THAN, false), false);
    private static final RenderPipeline[] VISIBLE = pipelines("visible", TRANSLUCENT, DepthStencilState.DEFAULT, false);
    private static final RenderPipeline[] HAND = pipelines("hand", TRANSLUCENT, DepthStencilState.DEFAULT, true);
    private static final RenderPipeline SILHOUETTE = pipeline("silhouette", 0, ColorTargetState.DEFAULT,
            new DepthStencilState(CompareOp.ALWAYS_PASS, false), true);
    private static final RenderPipeline GHOST_BODY = pipeline("ghost", GHOST, ColorTargetState.DEFAULT, DepthStencilState.DEFAULT, false);
    private static final OutputTarget OUTLINE = new OutputTarget(Adin.MOD_ID + ":outline", EntityOutlines::mask);
    private static final Set<RenderPipeline> ALL = new HashSet<>();
    private static final Map<Key, RenderType> VISIBLE_TYPES = new HashMap<>();
    private static final Map<Key, RenderType> OCCLUDED_TYPES = new HashMap<>();
    private static final Map<Key, RenderType> HAND_TYPES = new HashMap<>();
    private static final Map<Identifier, RenderType> SILHOUETTE_TYPES = new HashMap<>();
    private static final Map<Identifier, RenderType> GHOST_TYPES = new HashMap<>();

    private static EntityFill hand;
    private static boolean handPass;

    private record Key(Identifier texture, int effect) {}

    static {
        for (RenderPipeline pipeline : OCCLUDED) ALL.add(pipeline);
        for (RenderPipeline pipeline : VISIBLE) ALL.add(pipeline);
        for (RenderPipeline pipeline : HAND) ALL.add(pipeline);
        ALL.add(SILHOUETTE);
        ALL.add(GHOST_BODY);
    }

    public static void beginHand(EntityFill fill) {
        hand = fill;
        handPass = true;
    }

    public static void endHand() {
        hand = null;
        handPass = false;
    }

    public static EntityFill hand() {
        return hand;
    }

    public static boolean inHandPass() {
        return handPass;
    }

    public static RenderType visible(Identifier texture, int effect) {
        return VISIBLE_TYPES.computeIfAbsent(new Key(texture, effect), key -> create("visible", VISIBLE[key.effect()], key));
    }

    public static RenderType visibleHand(Identifier texture, int effect) {
        return HAND_TYPES.computeIfAbsent(new Key(texture, effect), key -> create("hand", HAND[key.effect()], key));
    }

    public static RenderType silhouette(Identifier texture) {
        return SILHOUETTE_TYPES.computeIfAbsent(texture, key -> RenderType.create(Adin.MOD_ID + ":entity_silhouette/" + key,
                RenderSetup.builder(SILHOUETTE).withTexture(TEXTURE, key).setOutputTarget(OUTLINE).createRenderSetup()));
    }

    public static RenderType occluded(Identifier texture, int effect) {
        return OCCLUDED_TYPES.computeIfAbsent(new Key(texture, effect), key -> create("occluded", OCCLUDED[key.effect()], key));
    }

    public static RenderType ghost(Identifier texture) {
        return GHOST_TYPES.computeIfAbsent(texture, key -> create("ghost", GHOST_BODY, new Key(key, GHOST)));
    }

    public static boolean isFill(RenderType type) {
        return ALL.contains(type.pipeline());
    }

    public static Identifier texture(RenderType type) {
        RenderSetup setup = ((RenderTypeAccessor) type).adin$getSetup();
        Object binding = ((RenderSetupAccessor) (Object) setup).adin$getTextures().get(TEXTURE);
        return binding == null ? null : ((TextureBindingAccessor) binding).adin$getLocation();
    }

    private static RenderType create(String name, RenderPipeline pipeline, Key key) {
        return RenderType.create(Adin.MOD_ID + ":entity_fill_" + name + key.effect() + "/" + key.texture(),
                RenderSetup.builder(pipeline).withTexture(TEXTURE, key.texture()).createRenderSetup());
    }

    private static RenderPipeline[] pipelines(String name, ColorTargetState color, DepthStencilState depth, boolean hand) {
        RenderPipeline[] pipelines = new RenderPipeline[EFFECTS + 1];
        for (int effect = 0; effect < pipelines.length; effect++) {
            pipelines[effect] = pipeline(name + effect, effect, color, depth, hand);
        }
        return pipelines;
    }

    private static RenderPipeline pipeline(String name, int effect, ColorTargetState color, DepthStencilState depth, boolean hand) {
        return RenderPipelines.register(RenderPipeline.builder()
                .withLocation(Adin.id("pipeline/fill_" + name))
                .withVertexShader(Adin.id("core/fill"))
                .withFragmentShader(Adin.id("core/fill"))
                .withShaderDefine("EFFECT", effect)
                .withShaderDefine("HAND", hand ? 1 : 0)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(color)
                .withDepthStencilState(depth)
                .withCull(false)
                .build());
    }
}
