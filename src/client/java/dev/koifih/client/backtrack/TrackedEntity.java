package dev.koifih.client.backtrack;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class TrackedEntity {
    private final VecDeltaCodec codec = new VecDeltaCodec();
    private final WalkAnimationState walk = new WalkAnimationState();
    private Vec3 lastTick = Vec3.ZERO;
    private float yRot;
    private float yRotO;
    private float xRot;
    private float xRotO;
    private float headRot;
    private float headRotO;
    private InteractionHand swingHand;
    private int swingTime;
    private float attack;
    private float attackO;

    public Vec3 position() {
        return codec.getBase();
    }

    public void syncTo(LivingEntity entity) {
        codec.setBase(entity.getPositionCodec().getBase());
        lastTick = position();
        yRot = yRotO = entity.getYRot();
        xRot = xRotO = entity.getXRot();
        headRot = headRotO = entity.getYHeadRot();
        walk.stop();
        swingHand = null;
        attack = attackO = 0f;
    }

    public Vec3 handle(Packet<?> packet, LivingEntity target) {
        Level level = target.level();
        return switch (packet) {
            case ClientboundMoveEntityPacket move when move.getEntity(level) == target -> moved(move);
            case ClientboundTeleportEntityPacket teleport when teleport.id() == target.getId() -> placed(
                    PositionMoveRotation.calculateAbsolute(current(), teleport.change(), teleport.relatives()));
            case ClientboundEntityPositionSyncPacket sync when sync.id() == target.getId() -> placed(sync.values());
            case ClientboundRotateHeadPacket head when head.getEntity(level) == target -> {
                headRot = head.getYHeadRot();
                yield null;
            }
            case ClientboundAnimatePacket animate when animate.getId() == target.getId() -> {
                swung(animate.getAction());
                yield null;
            }
            case ClientboundBundlePacket bundle -> {
                Vec3 last = null;
                for (Packet<?> sub : bundle.subPackets()) {
                    Vec3 position = handle(sub, target);
                    if (position != null) last = position;
                }
                yield last;
            }
            default -> null;
        };
    }

    public void tick(LivingEntity target) {
        Vec3 now = position();
        walk.update(Math.min((float) now.subtract(lastTick).horizontalDistance() * 4f, 1f), 0.4f, 1f);
        lastTick = now;
        yRotO = yRot;
        xRotO = xRot;
        headRotO = headRot;
        attackO = attack;
        if (swingHand == null) return;
        int duration = target.getItemInHand(swingHand).getSwingAnimation().duration();
        if (++swingTime >= duration) swingHand = null;
        attack = swingHand == null ? 0f : (float) swingTime / duration;
    }

    public void pose(EntityRenderState state, float partialTick) {
        if (!(state instanceof LivingEntityRenderState living)) return;
        float head = Mth.rotLerp(partialTick, headRotO, headRot);
        living.bodyRot = Mth.rotLerp(partialTick, yRotO, yRot);
        living.yRot = Mth.wrapDegrees(head - living.bodyRot);
        living.xRot = Mth.lerp(partialTick, xRotO, xRot);
        living.walkAnimationPos = walk.position(partialTick);
        living.walkAnimationSpeed = walk.speed(partialTick);
        if (!(state instanceof ArmedEntityRenderState armed)) return;
        float diff = attack - attackO;
        armed.attackTime = attackO + (diff < 0f ? diff + 1f : diff) * partialTick;
        if (swingHand != null) {
            armed.attackArm = swingHand == InteractionHand.MAIN_HAND ? armed.mainArm : armed.mainArm.getOpposite();
        }
    }

    private Vec3 moved(ClientboundMoveEntityPacket move) {
        if (move.hasRotation()) {
            yRot = move.getYRot();
            xRot = move.getXRot();
        }
        if (!move.hasPosition()) return null;
        Vec3 position = codec.decode(move.getXa(), move.getYa(), move.getZa());
        codec.setBase(position);
        return position;
    }

    private Vec3 placed(PositionMoveRotation values) {
        yRot = values.yRot();
        xRot = values.xRot();
        codec.setBase(values.position());
        return values.position();
    }

    private PositionMoveRotation current() {
        return new PositionMoveRotation(position(), Vec3.ZERO, yRot, xRot);
    }

    private void swung(int action) {
        if (action != ClientboundAnimatePacket.SWING_MAIN_HAND && action != ClientboundAnimatePacket.SWING_OFF_HAND) return;
        swingHand = action == ClientboundAnimatePacket.SWING_MAIN_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        swingTime = -1;
    }
}
