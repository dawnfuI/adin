package dev.koifih.client.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.koifih.client.render.entity.EntityFill;
import dev.koifih.client.render.entity.EntityFills;
import dev.koifih.client.render.entity.EntityOutlines;
import dev.koifih.client.render.entity.HandBounds;
import dev.koifih.client.render.entity.Filled;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin {
    @Inject(method = "submitModel", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void adin$fillModel(Model model, Object state, PoseStack pose, RenderType type, int light, int overlay, int color,
                                TextureAtlasSprite sprite, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfo info) {
        if (EntityFills.isFill(type)) return;
        boolean hand = !(state instanceof Filled);
        SubmitNodeCollection collection = (SubmitNodeCollection) (Object) this;
        Identifier texture = EntityFills.texture(type);
        if (hand && texture != null && adin$outliningHand()) {
            EntityFill fill = adin$handOutline();
            HandBounds.include(model, pose);
            collection.submitModel(model, state, pose, EntityFills.silhouette(texture), fill.light(), fill.visibleOverlay(),
                    fill.visible(), sprite, 0, crumbling);
        }
        EntityFill fill = hand ? adin$hand(pose) : ((Filled) state).adin$fill();
        if (fill == null || texture == null) return;
        if (fill.occluded() != 0) {
            collection.submitModel(model, state, pose, EntityFills.occluded(texture, fill.effect()), fill.light(), fill.occludedOverlay(),
                    fill.occluded(), sprite, 0, crumbling);
        }
        RenderType visible = fill.ghost() ? EntityFills.ghost(texture)
                : hand ? EntityFills.visibleHand(texture, fill.effect()) : EntityFills.visible(texture, fill.effect());
        collection.submitModel(model, state, pose, visible, fill.light(), fill.visibleOverlay(), fill.visible(), sprite, 0, crumbling);
        info.cancel();
    }

    @Inject(method = "submitItem", at = @At("HEAD"), cancellable = true)
    private void adin$fillItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outline, int[] tints,
                               List<BakedQuad> quads, ItemStackRenderState.FoilType foil, CallbackInfo info) {
        if (quads.isEmpty()) return;
        Identifier texture = EntityFills.texture(quads.getFirst().materialInfo().itemRenderType());
        if (texture == null) return;
        SubmitNodeCollection collection = (SubmitNodeCollection) (Object) this;
        if (adin$outliningHand()) {
            EntityFill silhouette = adin$handOutline();
            HandBounds.include(quads, pose);
            collection.submitCustomGeometry(pose, EntityFills.silhouette(texture),
                    (entry, consumer) -> adin$putQuads(entry, consumer, quads, silhouette));
        }
        EntityFill fill = adin$hand(pose);
        if (fill == null) return;
        collection.submitCustomGeometry(pose, EntityFills.visibleHand(texture, fill.effect()),
                (entry, consumer) -> adin$putQuads(entry, consumer, quads, fill));
        info.cancel();
    }

    @Unique
    private static void adin$putQuads(PoseStack.Pose entry, VertexConsumer consumer, List<BakedQuad> quads, EntityFill fill) {
        QuadInstance instance = new QuadInstance();
        instance.setColor(fill.visible());
        instance.setLightCoords(fill.light());
        instance.setOverlayCoords(fill.visibleOverlay());
        for (BakedQuad quad : quads) consumer.putBakedQuad(entry, quad, instance);
    }

    @Unique
    private static boolean adin$outliningHand() {
        return EntityOutlines.hand() != null && EntityFills.inHandPass();
    }

    @Unique
    private static EntityFill adin$handOutline() {
        return EntityFill.solid(EntityOutlines.hand().color(), 0);
    }

    @Unique
    private static EntityFill adin$hand(PoseStack pose) {
        EntityFill fill = EntityFills.hand();
        if (fill == null) return null;
        Vector3f pivot = pose.last().pose().getTranslation(new Vector3f());
        return fill.anchored(new Vec3(pivot));
    }
}
