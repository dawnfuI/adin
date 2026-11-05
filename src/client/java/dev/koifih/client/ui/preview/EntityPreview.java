package dev.koifih.client.ui.preview;

import dev.koifih.Adin;
import dev.koifih.client.mixin.accessor.FallingBlockEntityAccessor;
import dev.koifih.client.render.Point;
import dev.koifih.client.render.entity.EntityFill;
import dev.koifih.client.render.entity.EntityOutline;
import dev.koifih.client.render.entity.Filled;
import dev.koifih.client.render.entity.Ghost;
import dev.koifih.client.render.screen.Projector;
import dev.koifih.client.util.Game;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EntityPreview {
    private static final Map<EntityType<?>, Optional<Entity>> ENTITIES = new HashMap<>();
    private static int nextId = -1_000_000;
    private static FallingBlockEntity fallingBlock;

    public static void clear() {
        ENTITIES.clear();
        fallingBlock = null;
    }

    private static LocalPlayer player() {
        return Game.player();
    }

    private static Optional<Entity> entity(EntityType<?> type) {
        return ENTITIES.computeIfAbsent(type, EntityPreview::create);
    }

    private static Optional<Entity> create(EntityType<?> type) {
        var level = Game.level();
        if (level == null) return Optional.empty();
        try {
            Entity entity = type.create(level, EntitySpawnReason.LOAD);
            if (entity == null) return Optional.empty();
            entity.setId(nextId--);
            return Optional.of(entity);
        } catch (Exception exception) {
            Adin.LOGGER.debug("Cannot preview entity {}", type, exception);
            return Optional.empty();
        }
    }

    public static boolean canPreviewEntity(EntityType<?> type) {
        return entity(type).isPresent();
    }

    public static void drawEntity(GuiGraphicsExtractor graphics, EntityType<?> type, int x0, int y0, int x1, int y1, float yawDegrees) {
        Entity entity = entity(type).orElse(null);
        if (entity == null) return;
        entity.setYRot(yawDegrees);
        entity.setXRot(0f);
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = yawDegrees;
            living.yBodyRotO = yawDegrees;
            living.yHeadRot = yawDegrees;
            living.yHeadRotO = yawDegrees;
        }
        if (!render(graphics, entity, x0, y0, x1, y1, 0f, null, null, null)) ENTITIES.put(type, Optional.empty());
    }

    public static void drawBlock(GuiGraphicsExtractor graphics, Block block, int x0, int y0, int x1, int y1, float yawDegrees) {
        if (fallingBlock == null) {
            Entity entity = entity(EntityTypes.FALLING_BLOCK).orElse(null);
            if (!(entity instanceof FallingBlockEntity falling)) return;
            fallingBlock = falling;
        }
        ((FallingBlockEntityAccessor) fallingBlock).adin$setBlockState(block.defaultBlockState());
        render(graphics, fallingBlock, x0, y0, x1, y1, yawDegrees, null, null, null);
    }

    public static boolean drawPlayer(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float sceneYawDegrees,
                                     PlayerSkin skin, EntityFill fill, EntityOutline outline) {
        return drawPlayer(graphics, x0, y0, x1, y1, sceneYawDegrees, skin, fill, outline, null);
    }

    public static boolean drawPlayer(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float sceneYawDegrees,
                                     PlayerSkin skin, Consumer<EntityRenderState> adjust) {
        return drawPlayer(graphics, x0, y0, x1, y1, sceneYawDegrees, skin, null, null, adjust);
    }

    public static boolean drawPlayer(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float sceneYawDegrees,
                                     PlayerSkin skin, EntityFill fill, EntityOutline outline, Consumer<EntityRenderState> adjust) {
        var player = player();
        return player != null && render(graphics, player, x0, y0, x1, y1, sceneYawDegrees, skin, fill, outline, 0f, adjust);
    }

    public static boolean drawWalkingPlayer(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float sceneYawDegrees,
                                            PlayerSkin skin, float offsetX, float walkPos, float walkSpeed) {
        return drawWalking(graphics, x0, y0, x1, y1, sceneYawDegrees, skin, offsetX, walkPos, walkSpeed, state -> {});
    }

    public static boolean drawWalkingGhost(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float sceneYawDegrees,
                                           PlayerSkin skin, float offsetX, float walkPos, float walkSpeed) {
        return drawWalking(graphics, x0, y0, x1, y1, sceneYawDegrees, skin, offsetX, walkPos, walkSpeed, Ghost::prepare);
    }

    private static boolean drawWalking(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float sceneYawDegrees,
                                       PlayerSkin skin, float offsetX, float walkPos, float walkSpeed,
                                       Consumer<EntityRenderState> adjust) {
        var player = player();
        return player != null && render(graphics, player, x0, y0, x1, y1, sceneYawDegrees, skin, null, null, offsetX, state -> {
            adjust.accept(state);
            if (!(state instanceof LivingEntityRenderState living)) return;
            living.walkAnimationPos = walkPos;
            living.walkAnimationSpeed = walkSpeed;
            living.pose = Pose.STANDING;
        });
    }

    public static Projector playerProjector(int x0, int y0, int x1, int y1, float sceneYawDegrees) {
        return playerProjector(x0, y0, x1, y1, sceneYawDegrees, 0f);
    }

    public static Projector playerProjector(int x0, int y0, int x1, int y1, float sceneYawDegrees, float offsetX) {
        var player = player();
        if (player == null) return null;
        float scale = fitScale(player, x0, y0, x1, y1);
        Quaternionf rotation = sceneRotation(sceneYawDegrees);
        Vector3f lift = new Vector3f(offsetX, player.getBbHeight() / 2f, 0f);
        float centerX = (x0 + x1) * 0.5f;
        float centerY = (y0 + y1) * 0.5f;
        return (x, y, z) -> {
            Vector3f point = rotation.transform(new Vector3f((float) x, (float) y, (float) z)).add(lift).mul(scale);
            return new Point(centerX + point.x, centerY + point.y, point.z);
        };
    }

    public static float playerScale(int x0, int y0, int x1, int y1) {
        var player = player();
        return player == null ? 1f : fitScale(player, x0, y0, x1, y1);
    }

    public static AABB playerLocalBounds() {
        var player = player();
        if (player == null) return null;
        double half = player.getBbWidth() / 2.0;
        return new AABB(-half, 0.0, -half, half, player.getBbHeight(), half);
    }

    private static float fitScale(Entity entity, int x0, int y0, int x1, int y1) {
        float height = Math.max(0.3f, entity.getBbHeight());
        float width = Math.max(0.3f, entity.getBbWidth());
        return Math.min((y1 - y0) * 0.5f / height, (x1 - x0) * 0.55f / width);
    }

    private static Quaternionf sceneRotation(float sceneYawDegrees) {
        Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(15));
        return new Quaternionf().rotateZ((float) Math.PI).mul(camera).rotateY((float) Math.toRadians(sceneYawDegrees));
    }

    private static boolean render(GuiGraphicsExtractor graphics, Entity entity, int x0, int y0, int x1, int y1, float sceneYawDegrees,
                                  PlayerSkin skin, EntityFill fill, EntityOutline outline) {
        return render(graphics, entity, x0, y0, x1, y1, sceneYawDegrees, skin, fill, outline, 0f, null);
    }

    private static boolean render(GuiGraphicsExtractor graphics, Entity entity, int x0, int y0, int x1, int y1, float sceneYawDegrees,
                                  PlayerSkin skin, EntityFill fill, EntityOutline outline, float offsetX,
                                  Consumer<EntityRenderState> adjust) {
        var player = player();
        if (player != null && entity != player) entity.setPos(player.getX(), player.getY(), player.getZ());
        EntityRenderState state;
        try {
            EntityRenderer<? super Entity, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            state = renderer.createRenderState(entity, 1f);
        } catch (Exception exception) {
            Adin.LOGGER.debug("Cannot extract preview state for {}", entity.getType(), exception);
            return false;
        }
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (skin != null && state instanceof AvatarRenderState avatar) avatar.skin = skin;
        ((Filled) state).adin$setFill(fill);
        ((Filled) state).adin$setOutline(outline);
        if (adjust != null) adjust.accept(state);
        float scale = fitScale(entity, x0, y0, x1, y1);
        Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(15));
        graphics.entity(state, scale, new Vector3f(offsetX, entity.getBbHeight() / 2f, 0f), sceneRotation(sceneYawDegrees), camera, x0, y0, x1, y1);
        return true;
    }
}
