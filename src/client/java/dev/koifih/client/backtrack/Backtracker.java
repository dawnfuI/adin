package dev.koifih.client.backtrack;

import dev.koifih.Adin;
import dev.koifih.client.AdinClient;
import dev.koifih.client.event.Priority;
import dev.koifih.client.event.events.PacketProcessEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Backtracker {
    public static final class Held {
        private final Packet<?> packet;
        private final ClientPacketListener listener;
        private final long at;
        private boolean scanned;
        private Held(Packet<?> packet, ClientPacketListener listener, long at) {
            this.packet = packet;
            this.listener = listener;
            this.at = at;
        }

        public Packet<?> packet() {
            return packet;
        }

        public boolean scanned() {
            return scanned;
        }

        public void markScanned() {
            scanned = true;
        }
    }

    private static final Set<SoundEvent> FEEDBACK_SOUNDS = Set.of(
            SoundEvents.PLAYER_HURT,
            SoundEvents.PLAYER_ATTACK_CRIT,
            SoundEvents.PLAYER_ATTACK_KNOCKBACK,
            SoundEvents.PLAYER_ATTACK_NODAMAGE,
            SoundEvents.PLAYER_ATTACK_STRONG,
            SoundEvents.PLAYER_ATTACK_SWEEP,
            SoundEvents.PLAYER_ATTACK_WEAK);
    private static final ConcurrentLinkedQueue<Held> QUEUE = new ConcurrentLinkedQueue<>();
    private static volatile boolean holding;

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> drop());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> drop());
        AdinClient.EVENTS.subscribe(PacketProcessEvent.class, Priority.LOWEST, event -> {
            if (!holding) releaseAll();
        });
    }

    public static void hold(boolean hold) {
        holding = hold;
    }

    public static boolean isLagging() {
        return !QUEUE.isEmpty();
    }

    public static long heldMillis() {
        Held head = QUEUE.peek();
        return head == null ? 0L : System.currentTimeMillis() - head.at;
    }

    public static Iterable<Held> held() {
        return QUEUE;
    }

    public static boolean intercept(Packet<?> packet, PacketListener listener) {
        if (!(listener instanceof ClientPacketListener game)) return false;
        if (!holding && QUEUE.isEmpty()) return false;
        if (immediate(packet)) return false;
        QUEUE.add(new Held(packet, game, System.currentTimeMillis()));
        return true;
    }

    public static boolean forcesRelease(Packet<?> packet) {
        return packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundDisconnectPacket
                || packet instanceof ClientboundRespawnPacket
                || packet instanceof ClientboundLoginPacket
                || packet instanceof ClientboundStartConfigurationPacket
                || packet instanceof ClientboundSetHealthPacket health && health.getHealth() <= 0f;
    }

    private static boolean immediate(Packet<?> packet) {
        return packet instanceof ClientboundKeepAlivePacket
                || packet instanceof ClientboundDisconnectPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundPlayerChatPacket
                || packet instanceof ClientboundDisguisedChatPacket
                || packet instanceof ClientboundSoundPacket sound && FEEDBACK_SOUNDS.contains(sound.getSound().value())
                || packet instanceof ClientboundAnimatePacket animate && critical(animate);
    }

    private static boolean critical(ClientboundAnimatePacket animate) {
        return animate.getAction() == ClientboundAnimatePacket.CRITICAL_HIT
                || animate.getAction() == ClientboundAnimatePacket.MAGIC_CRITICAL_HIT;
    }

    public static void release(long delayMillis) {
        long now = System.currentTimeMillis();
        Held head = QUEUE.peek();
        if (head == null || now - head.at < delayMillis) return;
        flushVanilla();
        while ((head = QUEUE.peek()) != null && now - head.at >= delayMillis) {
            Held next = QUEUE.poll();
            if (next == null) return;
            dispatch(next);
        }
    }

    public static void releaseThrough(Held last) {
        flushVanilla();
        Held head;
        while ((head = QUEUE.poll()) != null) {
            dispatch(head);
            if (head == last) return;
        }
    }

    public static void releaseAll() {
        if (QUEUE.isEmpty()) return;
        flushVanilla();
        Held head;
        while ((head = QUEUE.poll()) != null) dispatch(head);
    }

    private static void flushVanilla() {
        Minecraft client = Minecraft.getInstance();
        if (client.packetProcessor().isSameThread()) client.packetProcessor().processQueuedPackets();
    }

    public static void drop() {
        QUEUE.clear();
    }

    @SuppressWarnings("unchecked")
    private static void dispatch(Held held) {
        Connection connection = held.listener.getConnection();
        if (!connection.isConnected() || connection.getPacketListener() != held.listener) return;
        try {
            ((Packet<ClientGamePacketListener>) held.packet).handle(held.listener);
        } catch (RuntimeException exception) {
            Adin.LOGGER.error("Failed to replay {}", held.packet.getClass().getSimpleName(), exception);
        }
    }
}
