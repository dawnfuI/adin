package dev.koifih.client.render.blocks;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class VertexArena {
    static final class Range {
        final long key;
        long offset;
        final int bytes;

        private Range(long key, long offset, int bytes) {
            this.key = key;
            this.offset = offset;
            this.bytes = bytes;
        }
    }

    private static final long INITIAL_CAPACITY = 1 << 20;
    private static final int USAGE = GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC;

    private final String label;
    private final Long2ObjectOpenHashMap<Range> byKey = new Long2ObjectOpenHashMap<>();
    private final List<Range> ordered = new ArrayList<>();
    private GpuBuffer buffer;
    private long capacity;
    private long used;
    private long live;

    Range put(CommandEncoder encoder, long key, ByteBuffer data) {
        remove(key);
        int bytes = data.remaining();
        if (bytes == 0) return null;
        if (buffer == null || used + bytes > capacity) repack(encoder, bytes);
        Range range = new Range(key, used, bytes);
        encoder.writeToBuffer(buffer.slice(used, bytes), data);
        used += bytes;
        live += bytes;
        byKey.put(key, range);
        ordered.add(range);
        return range;
    }

    void remove(long key) {
        Range old = byKey.remove(key);
        if (old == null) return;
        live -= old.bytes;
        ordered.remove(old);
    }

    List<Range> ordered() {
        return ordered;
    }

    GpuBuffer buffer() {
        return buffer;
    }

    boolean isEmpty() {
        return live == 0;
    }

    void close() {
        if (buffer != null) buffer.close();
        buffer = null;
        byKey.clear();
        ordered.clear();
        capacity = 0;
        used = 0;
        live = 0;
    }

    private void repack(CommandEncoder encoder, int incoming) {
        long needed = live + incoming;
        long next = Math.max(capacity, INITIAL_CAPACITY);
        while (needed > next / 2) next *= 2;
        GpuBuffer target = RenderSystem.getDevice().createBuffer(() -> "adin block esp " + label, USAGE, next);
        long cursor = 0;
        for (Range range : ordered) {
            encoder.copyToBuffer(buffer.slice(range.offset, range.bytes), target.slice(cursor, range.bytes));
            range.offset = cursor;
            cursor += range.bytes;
        }
        if (buffer != null) buffer.close();
        buffer = target;
        capacity = next;
        used = cursor;
    }
}
