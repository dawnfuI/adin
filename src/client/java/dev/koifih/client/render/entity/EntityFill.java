package dev.koifih.client.render.entity;

import net.minecraft.world.phys.Vec3;

public record EntityFill(int visible, int occluded, int visibleOverlay, int occludedOverlay, int light, int effect) {
    public static final EntityFill GHOST = new EntityFill(0xFFFFFFFF, 0, 0, 0, 0, EntityFills.GHOST);

    private static final float ORIGIN_UNITS = 512f;
    private static final float SCALE_UNITS = 256f;

    public static EntityFill solid(int visible, int occluded) {
        return gradient(visible, visible, occluded, occluded, 0, 0);
    }

    public static EntityFill gradient(int visible, int visibleEnd, int occluded, int occludedEnd, int screenMinY, int screenMaxY) {
        int light = (clamp(screenMinY) & 0xFFFF) | (clamp(screenMaxY) << 16);
        return new EntityFill(visible, occluded, color(visibleEnd), color(occludedEnd), light, 0);
    }

    public static EntityFill effect(int visible, int occluded, int effect, Vec3 origin, float scale) {
        int overlay = (wrapped(origin.x, ORIGIN_UNITS) & 0xFFFF) | (wrapped(origin.y, ORIGIN_UNITS) << 16);
        int light = (wrapped(origin.z, ORIGIN_UNITS) & 0xFFFF) | (fixed(scale, SCALE_UNITS) << 16);
        return new EntityFill(visible, occluded, overlay, overlay, light, effect);
    }

    public boolean ghost() {
        return effect == EntityFills.GHOST;
    }

    public EntityFill anchored(Vec3 origin) {
        if (effect == 0) return this;
        return effect(visible, occluded, effect, origin, (light >>> 16) / SCALE_UNITS);
    }

    private static int color(int rgb) {
        return ((rgb >> 16) & 0xFF) | (rgb & 0xFF00) | ((rgb & 0xFF) << 16);
    }

    private static int fixed(double value, float units) {
        return clamp((int) Math.round(value * units));
    }

    private static int wrapped(double value, float units) {
        return (short) Math.round(value * units);
    }

    private static int clamp(int value) {
        return Math.clamp(value, Short.MIN_VALUE, Short.MAX_VALUE);
    }
}
