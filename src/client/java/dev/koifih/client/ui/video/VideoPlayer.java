package dev.koifih.client.ui.video;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.koifih.Adin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.IOException;

public final class VideoPlayer implements AutoCloseable {
    private final AviVideo video;
    private final Identifier texture;
    private final long started = System.nanoTime();
    private DynamicTexture surface;
    private int shown = -1;

    VideoPlayer(String id, AviVideo video) {
        this.video = video;
        this.texture = Adin.id("video/player/" + id);
    }

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int drawWidth, int drawHeight) {
        int frame = (int) ((System.nanoTime() - started) / 1e9 * video.fps()) % video.frameCount();
        if (frame != shown) present(frame);
        if (surface == null) return;
        graphics.blit(surface.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                x, y, x + drawWidth, y + drawHeight, 0f, 1f, 0f, 1f);
    }

    private void present(int frame) {
        try (NativeImage image = video.decode(frame)) {
            if (surface == null) {
                int width = image.getWidth();
                int height = image.getHeight();
                surface = new DynamicTexture(() -> "adin video " + texture.getPath(), width, height, false);
                Minecraft.getInstance().getTextureManager().register(texture, surface);
            }
            surface.getPixels().copyFrom(image);
            surface.upload();
            shown = frame;
        } catch (IOException exception) {
            Adin.LOGGER.error("Cannot decode video frame {} of {}", frame, texture, exception);
            shown = frame;
        }
    }

    @Override
    public void close() {
        if (surface != null) Minecraft.getInstance().getTextureManager().release(texture);
        surface = null;
    }
}
