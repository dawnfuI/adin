package dev.koifih.client.render.blocks;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.koifih.Adin;
import dev.koifih.client.util.Colors;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockRenderer {
    public record Spec(int color, float fillOpacity, float lineWidth) {}

    private static final int SLOT_WORDS = 132;
    private static final int SLOT_BYTES = SLOT_WORDS * Integer.BYTES;
    private static final int ORIGIN_BYTES = 4 * Integer.BYTES;
    private static final int BRICK_WORD = 128;
    private static final int SHAPE_WORD = 130;
    private static final int SHAPE_BYTES = BlockIndex.VOLUME;
    private static final int MAX_SHAPES = 255;
    private static final int BOUND_BYTES = 2 * 4 * Float.BYTES;
    private static final int INITIAL_SLOTS = 256;
    private static final int LOADED_MARGIN = 3;
    private static final int BOX_INDICES = 18 * 6;
    private static final int QUAD_INDICES = 6;
    private static final int QUAD_BASE_VERTEX = 56;
    private static final double TINY_PIXELS = 10.0;
    private static final double SECTION_RADIUS = Math.sqrt(3) * 8;
    private static final int[] FACES = {0, 1, 3, 2, 4, 6, 7, 5, 0, 4, 5, 1, 2, 3, 7, 6, 0, 2, 6, 4, 1, 5, 7, 3};
    private static final int CORNERS = 8;
    private static final int EDGE_COUNT = 12;
    private static final int COMMAND_BYTES = 5 * Integer.BYTES;
    private static final double MARCH_ABOVE = 0.5;
    private static final double INSTANCE_BELOW = 0.4;
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putMat4f().putMat4f().putVec4().putVec4().putVec4().putVec4().putVec4().putIVec4().putIVec4().get();
    private static final int TEXEL_USAGE = GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC;
    private static final VertexFormat INSTANCE = VertexFormat.builder(1).addAttribute("Block", GpuFormat.R32_UINT).build();
    private static final BindGroupLayout VOLUME = BindGroupLayout.builder()
            .withUniform("BlockVolume", UniformType.UNIFORM_BUFFER)
            .withUniform("Occupancy", UniformType.TEXEL_BUFFER, GpuFormat.R32_UINT)
            .withUniform("ShapeIds", UniformType.TEXEL_BUFFER, GpuFormat.R8_UINT)
            .withUniform("ShapeBounds", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
            .build();
    private static final BindGroupLayout MARCH_LOOKUP = BindGroupLayout.builder()
            .withUniform("Lookup", UniformType.TEXEL_BUFFER, GpuFormat.R32_UINT)
            .build();
    private static final BindGroupLayout BOX_ORIGINS = BindGroupLayout.builder()
            .withUniform("Origins", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_SINT)
            .build();
    private static final RenderPipeline MARCH = RenderPipeline.builder()
            .withLocation(Adin.id("pipeline/blocks_volume"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Adin.id("core/blocks_volume"))
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(VOLUME)
            .withBindGroupLayout(MARCH_LOOKUP)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();
    private static final RenderPipeline BOX = RenderPipeline.builder()
            .withLocation(Adin.id("pipeline/blocks_box"))
            .withVertexShader(Adin.id("core/blocks_box"))
            .withFragmentShader(Adin.id("core/blocks_box"))
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(VOLUME)
            .withBindGroupLayout(BOX_ORIGINS)
            .withVertexBinding(0, INSTANCE)
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Vector3f FORWARD = new Vector3f();

    private static final Queue<Long> changed = new ConcurrentLinkedQueue<>();
    private static final Long2IntOpenHashMap slots = new Long2IntOpenHashMap();
    private static final Long2IntOpenHashMap shapeSlots = new Long2IntOpenHashMap();
    private static final Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
    private static final IntArrayList freeSlots = new IntArrayList();
    private static final IntArrayList freeShapeSlots = new IntArrayList();
    private static final Object2IntOpenHashMap<AABB> shapeIds = new Object2IntOpenHashMap<>();
    private static final VertexArena instances = new VertexArena("instances");
    private static volatile Spec spec;
    private static volatile int visibleBlocks;
    private static GpuBuffer occupancy;
    private static GpuBuffer origins;
    private static GpuBuffer shapes;
    private static GpuBuffer bounds;
    private static GpuBuffer lookup;
    private static GpuBuffer uniform;
    private static GpuBuffer boxIndices;
    private static GpuBuffer commands;
    private static int[] order = new int[0];
    private static double[] distances = new double[0];
    private static double tinyBeyond;
    private static int slotCapacity;
    private static int shapeCapacity;
    private static long totalBlocks;
    private static boolean marching;
    private static int[] grid = new int[0];
    private static int gridWidth;
    private static int gridHeight;
    private static int gridMinY;
    private static boolean gridDirty;

    static {
        slots.defaultReturnValue(-1);
        shapeSlots.defaultReturnValue(-1);
    }

    public static void init() {
        LevelRenderEvents.END_MAIN.register(BlockRenderer::render);
    }

    public static void configure(Spec next) {
        spec = next;
    }

    public static int visibleBlocks() {
        return visibleBlocks;
    }

    public static void onSectionChanged(long key) {
        changed.add(key);
    }

    private static void render(LevelRenderContext context) {
        BlockIndex.drain();
        Spec current = spec;
        ClientLevel level = Minecraft.getInstance().level;
        if (current == null || level == null) {
            dispose();
            visibleBlocks = 0;
            return;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        resizeGrid(level);
        drainChanges(encoder);
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        choosePath(encoder, (long) main.width * main.height);
        CameraRenderState camera = context.levelState().cameraRenderState;
        visibleBlocks = countVisible(camera.cullFrustum);
        if (slots.isEmpty()) return;
        if (gridDirty) uploadGrid(encoder);
        if (shapes == null) allocateShape(encoder);
        writeUniform(encoder, camera, current, main.width, main.height);
        int boxes = marching ? 0 : prepareBoxes(encoder, camera.cullFrustum, camera.pos, 2.0 / (camera.projectionMatrix.m11() * main.height));
        GpuTextureView color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : main.getColorTextureView();
        try (RenderPass pass = encoder.createRenderPass(() -> "adin block esp", color, Optional.empty(), null, OptionalDouble.empty())) {
            pass.setPipeline(marching ? MARCH : BOX);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("BlockVolume", uniform.slice());
            pass.setUniform("Occupancy", occupancy.slice());
            pass.setUniform("ShapeIds", shapes.slice());
            pass.setUniform("ShapeBounds", bounds.slice());
            if (marching) {
                pass.setUniform("Lookup", lookup.slice());
                pass.draw(3, 1, 0, 0);
            } else {
                pass.setUniform("Origins", origins.slice());
                drawBoxes(pass, boxes);
            }
        }
    }

    private static int prepareBoxes(CommandEncoder encoder, Frustum frustum, Vec3 camera, double pixelScale) {
        tinyBeyond = Math.sqrt(3) / (TINY_PIXELS * pixelScale) + SECTION_RADIUS;
        if (instances.isEmpty()) return 0;
        List<VertexArena.Range> ordered = instances.ordered();
        int visible = 0;
        if (order.length < ordered.size()) {
            order = new int[ordered.size() * 2];
            distances = new double[ordered.size() * 2];
        }
        for (int i = 0; i < ordered.size(); i++) {
            long key = ordered.get(i).key;
            if (frustum != null && !frustum.isVisible(sectionBox(key))) continue;
            double x = SectionPos.sectionToBlockCoord(SectionPos.x(key)) + 8 - camera.x;
            double y = SectionPos.sectionToBlockCoord(SectionPos.y(key)) + 8 - camera.y;
            double z = SectionPos.sectionToBlockCoord(SectionPos.z(key)) + 8 - camera.z;
            distances[i] = x * x + y * y + z * z;
            order[visible++] = i;
        }
        if (visible == 0) return 0;
        IntArrays.quickSort(order, 0, visible, (a, b) -> Double.compare(distances[b], distances[a]));
        boxIndices();
        if (!indirect()) return visible;
        long bytes = (long) visible * COMMAND_BYTES;
        if (commands == null || commands.size() < bytes) {
            if (commands != null) commands.close();
            commands = RenderSystem.getDevice().createBuffer(() -> "adin block esp commands",
                    GpuBuffer.USAGE_INDIRECT_PARAMETERS | GpuBuffer.USAGE_COPY_DST, Math.max(bytes * 2, 4096));
        }
        ByteBuffer data = MemoryUtil.memAlloc((int) bytes).order(ByteOrder.nativeOrder());
        try {
            for (int i = 0; i < visible; i++) {
                VertexArena.Range range = ordered.get(order[i]);
                boolean far = tiny(order[i]);
                data.putInt(far ? QUAD_INDICES : BOX_INDICES).putInt(range.bytes / Integer.BYTES).putInt(far ? BOX_INDICES : 0)
                        .putInt(far ? QUAD_BASE_VERTEX : 0).putInt((int) (range.offset / Integer.BYTES));
            }
            data.flip();
            encoder.writeToBuffer(commands.slice(0, bytes), data);
        } finally {
            MemoryUtil.memFree(data);
        }
        return visible;
    }

    private static void drawBoxes(RenderPass pass, int visible) {
        if (visible == 0) return;
        pass.setIndexBuffer(boxIndices, IndexType.SHORT);
        pass.setVertexBuffer(0, instances.buffer().slice());
        if (indirect()) {
            pass.drawIndexedIndirect(commands.slice(0, (long) visible * COMMAND_BYTES), visible);
            return;
        }
        List<VertexArena.Range> ordered = instances.ordered();
        for (int i = 0; i < visible; i++) {
            VertexArena.Range range = ordered.get(order[i]);
            boolean far = tiny(order[i]);
            pass.drawIndexed(far ? QUAD_INDICES : BOX_INDICES, range.bytes / Integer.BYTES, far ? BOX_INDICES : 0,
                    far ? QUAD_BASE_VERTEX : 0, (int) (range.offset / Integer.BYTES));
        }
    }

    private static boolean tiny(int section) {
        return distances[section] > tinyBeyond * tinyBeyond;
    }

    private static boolean indirect() {
        return RenderSystem.getDevice().getDeviceInfo().features().multiDrawIndirect();
    }

    private static GpuBuffer boxIndices() {
        if (boxIndices != null) return boxIndices;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc((BOX_INDICES + QUAD_INDICES) * Short.BYTES).order(ByteOrder.nativeOrder());
            for (int face = 0; face < FACES.length; face += 4) quad(data, FACES[face], FACES[face + 1], FACES[face + 2], FACES[face + 3]);
            for (int edge = 0; edge < EDGE_COUNT; edge++) {
                int base = CORNERS + edge * 4;
                quad(data, base, base + 1, base + 2, base + 3);
            }
            quad(data, 0, 1, 2, 3);
            data.flip();
            boxIndices = RenderSystem.getDevice().createBuffer(() -> "adin block esp box indices", GpuBuffer.USAGE_INDEX, data);
        }
        return boxIndices;
    }

    private static void quad(ByteBuffer data, int a, int b, int c, int d) {
        data.putShort((short) a).putShort((short) b).putShort((short) c).putShort((short) c).putShort((short) d).putShort((short) a);
    }

    private static AABB sectionBox(long key) {
        double x = SectionPos.sectionToBlockCoord(SectionPos.x(key));
        double y = SectionPos.sectionToBlockCoord(SectionPos.y(key));
        double z = SectionPos.sectionToBlockCoord(SectionPos.z(key));
        return new AABB(x, y, z, x + BlockIndex.SIZE, y + BlockIndex.SIZE, z + BlockIndex.SIZE);
    }

    private static int countVisible(Frustum frustum) {
        if (frustum == null) return 0;
        int total = 0;
        for (Long2IntOpenHashMap.Entry entry : counts.long2IntEntrySet()) {
            if (frustum.isVisible(sectionBox(entry.getLongKey()))) total += entry.getIntValue();
        }
        return total;
    }

    private static void choosePath(CommandEncoder encoder, long pixels) {
        if (!marching && totalBlocks > pixels * MARCH_ABOVE) {
            marching = true;
            instances.close();
        } else if (marching && totalBlocks < pixels * INSTANCE_BELOW) {
            marching = false;
            for (Long2IntOpenHashMap.Entry entry : slots.long2IntEntrySet()) {
                BlockIndex.Matches matches = BlockIndex.matches(entry.getLongKey());
                if (matches != null) storeInstances(encoder, entry.getLongKey(), entry.getIntValue(), matches);
            }
        }
    }

    private static void writeUniform(CommandEncoder encoder, CameraRenderState camera, Spec current, float width, float height) {
        if (uniform == null) {
            uniform = RenderSystem.getDevice().createBuffer(() -> "adin block esp volume", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_SIZE);
        }
        VIEW_PROJECTION.set(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        VIEW_PROJECTION.invert(INVERSE);
        INVERSE.transformProject(0f, 0f, 0.5f, FORWARD).normalize();
        float pixelScale = 2f / (camera.projectionMatrix.m11() * height);
        int blockX = (int) Math.floor(camera.pos.x);
        int blockY = (int) Math.floor(camera.pos.y);
        int blockZ = (int) Math.floor(camera.pos.z);
        int color = current.color();
        float red = Colors.red(color) / 255f;
        float green = Colors.green(color) / 255f;
        float blue = Colors.blue(color) / 255f;
        int range = Minecraft.getInstance().options.getEffectiveRenderDistance() + LOADED_MARGIN;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            encoder.writeToBuffer(uniform.slice(), Std140Builder.onStack(stack, UNIFORM_SIZE)
                    .putMat4f(INVERSE)
                    .putMat4f(VIEW_PROJECTION)
                    .putVec4(red, green, blue, current.fillOpacity())
                    .putVec4(red, green, blue, current.lineWidth())
                    .putVec4((float) (camera.pos.x - blockX), (float) (camera.pos.y - blockY), (float) (camera.pos.z - blockZ), 0f)
                    .putVec4(FORWARD.x, FORWARD.y, FORWARD.z, pixelScale)
                    .putVec4(width, height, 0f, 0f)
                    .putIVec4(blockX, blockY, blockZ, gridWidth)
                    .putIVec4(range, gridMinY, gridHeight, range * BlockIndex.SIZE + BlockIndex.SIZE)
                    .get());
        }
    }

    private static void resizeGrid(ClientLevel level) {
        int width = 2 * (Minecraft.getInstance().options.getEffectiveRenderDistance() + LOADED_MARGIN) + 3;
        int height = level.getSectionsCount();
        int minY = level.getMinSectionY();
        if (width == gridWidth && height == gridHeight && minY == gridMinY) return;
        gridWidth = width;
        gridHeight = height;
        gridMinY = minY;
        grid = new int[width * height * width];
        for (Long2IntOpenHashMap.Entry entry : slots.long2IntEntrySet()) place(entry.getLongKey(), entry.getIntValue() + 1);
        if (lookup != null) lookup.close();
        lookup = RenderSystem.getDevice().createBuffer(() -> "adin block esp lookup", TEXEL_USAGE, (long) grid.length * Integer.BYTES);
        gridDirty = true;
    }

    private static void place(long key, int value) {
        int y = SectionPos.y(key) - gridMinY;
        if (y < 0 || y >= gridHeight) return;
        int x = Math.floorMod(SectionPos.x(key), gridWidth);
        int z = Math.floorMod(SectionPos.z(key), gridWidth);
        int cell = (y * gridWidth + z) * gridWidth + x;
        if (value != 0 || grid[cell] == slots.get(key) + 1) grid[cell] = value;
        gridDirty = true;
    }

    private static void uploadGrid(CommandEncoder encoder) {
        ByteBuffer data = MemoryUtil.memAlloc(grid.length * Integer.BYTES).order(ByteOrder.nativeOrder());
        try {
            data.asIntBuffer().put(grid);
            encoder.writeToBuffer(lookup.slice(), data);
        } finally {
            MemoryUtil.memFree(data);
        }
        gridDirty = false;
    }

    private static void drainChanges(CommandEncoder encoder) {
        Long key;
        while ((key = changed.poll()) != null) {
            BlockIndex.Matches matches = BlockIndex.matches(key);
            if (matches == null) release(key);
            else store(encoder, key, matches);
        }
    }

    private static void release(long key) {
        int slot = slots.get(key);
        if (slot < 0) return;
        place(key, 0);
        slots.remove(key);
        totalBlocks -= counts.remove(key);
        instances.remove(key);
        freeSlots.add(slot);
        int shapeSlot = shapeSlots.remove(key);
        if (shapeSlot >= 0) freeShapeSlots.add(shapeSlot);
    }

    private static void store(CommandEncoder encoder, long key, BlockIndex.Matches matches) {
        int slot = slots.get(key);
        if (slot < 0) {
            slot = allocate(encoder);
            slots.put(key, slot);
            place(key, slot + 1);
            writeOrigin(encoder, key, slot);
        }
        int count = matches.count();
        totalBlocks += count - counts.put(key, count);
        int shapeSlot = storeShapes(encoder, key, matches);
        ByteBuffer data = MemoryUtil.memAlloc(SLOT_BYTES).order(ByteOrder.nativeOrder());
        try {
            long[] bits = matches.bits();
            for (long word : bits) {
                data.putInt((int) word);
                data.putInt((int) (word >>> 32));
            }
            long bricks = bricks(bits);
            data.putInt((int) bricks);
            data.putInt((int) (bricks >>> 32));
            data.putInt(shapeSlot + 1);
            data.putInt(0);
            data.flip();
            encoder.writeToBuffer(occupancy.slice((long) slot * SLOT_BYTES, SLOT_BYTES), data);
        } finally {
            MemoryUtil.memFree(data);
        }
        if (!marching) storeInstances(encoder, key, slot, matches);
    }

    private static void storeInstances(CommandEncoder encoder, long key, int slot, BlockIndex.Matches matches) {
        long[] bits = matches.bits();
        ByteBuffer data = MemoryUtil.memAlloc(matches.count() * Integer.BYTES).order(ByteOrder.nativeOrder());
        try {
            for (int word = 0; word < bits.length; word++) {
                long value = bits[word];
                while (value != 0L) {
                    data.putInt(slot << 12 | word * Long.SIZE + Long.numberOfTrailingZeros(value));
                    value &= value - 1;
                }
            }
            data.flip();
            instances.put(encoder, key, data);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static void writeOrigin(CommandEncoder encoder, long key, int slot) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(ORIGIN_BYTES).order(ByteOrder.nativeOrder());
            data.putInt(SectionPos.x(key)).putInt(SectionPos.y(key)).putInt(SectionPos.z(key)).putInt(0).flip();
            encoder.writeToBuffer(origins.slice((long) slot * ORIGIN_BYTES, ORIGIN_BYTES), data);
        }
    }

    private static long bricks(long[] bits) {
        long mask = 0L;
        for (int word = 0; word < bits.length; word++) {
            long value = bits[word];
            while (value != 0L) {
                int index = word * Long.SIZE + Long.numberOfTrailingZeros(value);
                value &= value - 1;
                mask |= 1L << (((index >>> 10) << 4) | (((index >>> 6) & 3) << 2) | ((index >>> 2) & 3));
            }
        }
        return mask;
    }

    private static int storeShapes(CommandEncoder encoder, long key, BlockIndex.Matches matches) {
        int existing = shapeSlots.get(key);
        if (matches.shapes() == null) {
            if (existing >= 0) {
                shapeSlots.remove(key);
                freeShapeSlots.add(existing);
            }
            return -1;
        }
        int slot = existing >= 0 ? existing : allocateShape(encoder);
        shapeSlots.put(key, slot);
        ByteBuffer data = MemoryUtil.memAlloc(SHAPE_BYTES);
        try {
            for (int index = 0; index < BlockIndex.VOLUME; index++) {
                AABB shape = matches.has(index) ? matches.shape(index) : null;
                data.put(index, (byte) (shape == null ? 0 : shapeId(encoder, shape)));
            }
            encoder.writeToBuffer(shapes.slice((long) slot * SHAPE_BYTES, SHAPE_BYTES), data);
        } finally {
            MemoryUtil.memFree(data);
        }
        return slot;
    }

    private static int shapeId(CommandEncoder encoder, AABB shape) {
        int id = shapeIds.getInt(shape);
        if (id != 0) return id;
        if (shapeIds.size() >= MAX_SHAPES) return 0;
        id = shapeIds.size() + 1;
        shapeIds.put(shape, id);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(BOUND_BYTES).order(ByteOrder.nativeOrder());
            data.putFloat((float) shape.minX).putFloat((float) shape.minY).putFloat((float) shape.minZ).putFloat(0f)
                    .putFloat((float) shape.maxX).putFloat((float) shape.maxY).putFloat((float) shape.maxZ).putFloat(0f).flip();
            encoder.writeToBuffer(bounds.slice((long) (id - 1) * BOUND_BYTES, BOUND_BYTES), data);
        }
        return id;
    }

    private static int allocate(CommandEncoder encoder) {
        if (!freeSlots.isEmpty()) return freeSlots.removeInt(freeSlots.size() - 1);
        if (occupancy == null || slots.size() >= slotCapacity) {
            int capacity = Math.max(INITIAL_SLOTS, slotCapacity * 2);
            occupancy = grow(encoder, occupancy, "occupancy", (long) capacity * SLOT_BYTES);
            origins = grow(encoder, origins, "origins", (long) capacity * ORIGIN_BYTES);
            slotCapacity = capacity;
        }
        return slots.size();
    }

    private static int allocateShape(CommandEncoder encoder) {
        if (bounds == null) bounds = RenderSystem.getDevice().createBuffer(() -> "adin block esp bounds", TEXEL_USAGE, (long) MAX_SHAPES * BOUND_BYTES);
        if (!freeShapeSlots.isEmpty()) return freeShapeSlots.removeInt(freeShapeSlots.size() - 1);
        if (shapes == null || shapeSlots.size() >= shapeCapacity) {
            int capacity = Math.max(16, shapeCapacity * 2);
            shapes = grow(encoder, shapes, "shapes", (long) capacity * SHAPE_BYTES);
            shapeCapacity = capacity;
        }
        return shapeSlots.size();
    }

    private static GpuBuffer grow(CommandEncoder encoder, GpuBuffer old, String label, long size) {
        GpuBuffer next = RenderSystem.getDevice().createBuffer(() -> "adin block esp " + label, TEXEL_USAGE, size);
        if (old != null) {
            encoder.copyToBuffer(old.slice(), next.slice(0, old.size()));
            old.close();
        }
        return next;
    }

    private static void dispose() {
        changed.clear();
        if (slots.isEmpty() && occupancy == null) return;
        slots.clear();
        shapeSlots.clear();
        counts.clear();
        freeSlots.clear();
        freeShapeSlots.clear();
        shapeIds.clear();
        instances.close();
        totalBlocks = 0;
        marching = false;
        Arrays.fill(grid, 0);
        for (GpuBuffer buffer : new GpuBuffer[] {occupancy, origins, shapes, bounds}) if (buffer != null) buffer.close();
        occupancy = null;
        origins = null;
        shapes = null;
        bounds = null;
        slotCapacity = 0;
        shapeCapacity = 0;
        gridDirty = true;
    }
}
