package dev.koifih.client.util;

import dev.koifih.client.mixin.accessor.MultiPlayerGameModeAccessor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Placement {
    private static final float DUPLICATE_MIN_DELTA = 2f;
    private static final float DUPLICATE_EPSILON = 0.0001f;
    private static final double DIAGONAL_RATIO = 0.4;

    private static boolean placed;
    private static boolean rotated;
    private static float lastRotationDelta;
    private static float lastPlacedDelta = -1f;

    public static boolean ready() {
        return !placed && !blocked();
    }

    public static boolean blocked() {
        return rotated && duplicates(lastRotationDelta);
    }

    public static boolean duplicates(float yawDelta) {
        return yawDelta > DUPLICATE_MIN_DELTA && Math.abs(yawDelta - lastPlacedDelta) < DUPLICATE_EPSILON;
    }

    public static void rotated(float yawDelta) {
        lastRotationDelta = yawDelta;
        rotated = true;
    }

    public static BlockHitResult placeInto(Level level, Vec3 eye, BlockPos target) {
        return placeInto(level, eye, target, null);
    }

    public static BlockHitResult placeInto(Level level, Vec3 eye, BlockPos target, BlockPos avoid) {
        BlockHitResult best = null;
        for (Direction face : Direction.values()) {
            BlockPos against = target.relative(face.getOpposite());
            if (against.equals(avoid) || !level.getBlockState(against).isFaceSturdy(level, against, face)) continue;
            best = nearer(eye, best, candidate(level, eye, against, face));
        }
        return best;
    }

    public static Vec3i sideToward(BlockPos pos, Vec3 eye) {
        Vec3 offset = eye.subtract(Vec3.atCenterOf(pos));
        double x = Math.abs(offset.x);
        double z = Math.abs(offset.z);
        boolean diagonal = Math.min(x, z) > DIAGONAL_RATIO * Math.max(x, z);
        int stepX = diagonal || x >= z ? (int) Math.signum(offset.x) : 0;
        int stepZ = diagonal || z > x ? (int) Math.signum(offset.z) : 0;
        return new Vec3i(stepX, 0, stepZ);
    }

    public static BlockHitResult clickOn(Level level, Vec3 eye, BlockPos block) {
        BlockHitResult best = null;
        for (Direction face : Direction.values()) {
            BlockPos neighbor = block.relative(face);
            if (!level.getBlockState(neighbor).getCollisionShape(level, neighbor).isEmpty()) continue;
            best = nearer(eye, best, candidate(level, eye, block, face));
        }
        return best;
    }

    public static boolean looksAt(Level level, BlockPos pos, Vec3 eye, Vec3 look, double range) {
        return shape(level, pos).clip(eye, eye.add(look.scale(range))).isPresent();
    }

    public static AABB shape(Level level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
    }

    private static BlockHitResult candidate(Level level, Vec3 eye, BlockPos block, Direction face) {
        if (!canClick(eye, new AABB(block), face)) return null;
        return new BlockHitResult(faceCenter(shape(level, block), face), face, block, false);
    }

    private static BlockHitResult nearer(Vec3 eye, BlockHitResult current, BlockHitResult candidate) {
        if (candidate == null) return current;
        if (current == null || candidate.getLocation().distanceToSqr(eye) < current.getLocation().distanceToSqr(eye)) return candidate;
        return current;
    }

    private static boolean canClick(Vec3 eye, AABB block, Direction face) {
        if (block.contains(eye)) return true;
        return switch (face) {
            case UP -> eye.y >= block.maxY;
            case DOWN -> eye.y <= block.minY;
            case NORTH -> eye.z <= block.minZ;
            case SOUTH -> eye.z >= block.maxZ;
            case WEST -> eye.x <= block.minX;
            case EAST -> eye.x >= block.maxX;
        };
    }

    private static Vec3 faceCenter(AABB box, Direction face) {
        Vec3 center = box.getCenter();
        return switch (face) {
            case UP -> new Vec3(center.x, box.maxY, center.z);
            case DOWN -> new Vec3(center.x, box.minY, center.z);
            case NORTH -> new Vec3(center.x, center.y, box.minZ);
            case SOUTH -> new Vec3(center.x, center.y, box.maxZ);
            case WEST -> new Vec3(box.minX, center.y, center.z);
            case EAST -> new Vec3(box.maxX, center.y, center.z);
        };
    }

    public static boolean placeable(LocalPlayer player, ItemStack stack, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem item)) return false;
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        if (!context.canPlace()) return false;
        BlockState state = item.getBlock().getStateForPlacement(context);
        Level level = player.level();
        return state != null && state.canSurvive(level, context.getClickedPos())
                && level.isUnobstructed(state, context.getClickedPos(), CollisionContext.of(player));
    }

    public static void use(LocalPlayer player, BlockHitResult hit, int slot, boolean quiet, boolean placing) {
        Minecraft client = Game.mc();
        if (quiet) {
            ItemStack stack = player.getInventory().getItem(slot);
            ((MultiPlayerGameModeAccessor) client.gameMode).adin$startPrediction(client.level, sequence -> {
                if (placing) predict(player, stack, hit);
                return new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, sequence);
            });
            player.swing(InteractionHand.MAIN_HAND);
        } else if (!Clicks.right(client, hit)) {
            client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    public static void predict(LocalPlayer player, ItemStack stack, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem item)) return;
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        BlockState state = item.getBlock().getStateForPlacement(context);
        if (state != null) Game.level().setBlock(context.getClickedPos(), state, Block.UPDATE_ALL_IMMEDIATE);
    }

    public static void sent(Packet<?> packet) {
        if (packet instanceof ServerboundClientTickEndPacket) {
            placed = false;
        } else if (packet instanceof ServerboundUseItemOnPacket) {
            placed = true;
            if (rotated) lastPlacedDelta = lastRotationDelta;
            rotated = false;
        }
    }
}
