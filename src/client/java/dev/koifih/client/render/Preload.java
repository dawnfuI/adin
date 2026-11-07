package dev.koifih.client.render;

import dev.koifih.client.render.entity.EntityFills;
import dev.koifih.client.render.entity.EntityOutlines;
import dev.koifih.client.render.font.Fonts;
import dev.koifih.client.render.post.GhostComposite;
import dev.koifih.client.render.post.KawaseBlur;
import dev.koifih.client.render.state.Pipelines;
import dev.koifih.client.render.world.WorldRenderTypes;
import dev.koifih.client.ui.clickgui.ClickGui;
import java.lang.invoke.MethodHandles;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Preload {
    private static final List<Class<?>> EAGER = List.of(Pipelines.class, EntityFills.class, EntityOutlines.class,
            KawaseBlur.class, GhostComposite.class, WorldRenderTypes.class, Fonts.class);

    public static void init() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        for (Class<?> type : EAGER) {
            try {
                lookup.ensureInitialized(type);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            Fonts.ALL.forEach(font -> client.getTextureManager().getTexture(font.texture()));
            new ClickGui().init(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        });
    }
}
