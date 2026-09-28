package dev.koifih.client.backtrack;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class TrackedPosition {
    private final VecDeltaCodec codec = new VecDeltaCodec();

    public Vec3 get() {
        return codec.getBase();
    }

    public void syncTo(Entity entity) {
        codec.setBase(entity.getPositionCodec().getBase());
    }

    public Vec3 handle(Packet<?> packet, Level level, Entity target) {
        Vec3 position = switch (packet) {
            case ClientboundMoveEntityPacket move when move.hasPosition() && move.getEntity(level) == target ->
                    codec.decode(move.getXa(), move.getYa(), move.getZa());
            case ClientboundTeleportEntityPacket teleport when teleport.id() == target.getId() -> absolute(teleport);
            case ClientboundEntityPositionSyncPacket sync when sync.id() == target.getId() -> sync.values().position();
            case ClientboundBundlePacket bundle -> bundled(bundle, level, target);
            default -> null;
        };
        if (position != null) codec.setBase(position);
        return position;
    }

    private Vec3 bundled(ClientboundBundlePacket bundle, Level level, Entity target) {
        Vec3 last = null;
        for (Packet<?> packet : bundle.subPackets()) {
            Vec3 position = handle(packet, level, target);
            if (position != null) last = position;
        }
        return last;
    }

    private Vec3 absolute(ClientboundTeleportEntityPacket teleport) {
        PositionMoveRotation current = new PositionMoveRotation(codec.getBase(), Vec3.ZERO, 0f, 0f);
        return PositionMoveRotation.calculateAbsolute(current, teleport.change(), teleport.relatives()).position();
    }
}
