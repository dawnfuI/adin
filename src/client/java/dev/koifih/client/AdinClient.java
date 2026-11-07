package dev.koifih.client;

import dev.koifih.client.util.Clicks;
import dev.koifih.client.util.Time;
import dev.koifih.client.event.Priority;
import dev.koifih.client.backtrack.Backtracker;
import dev.koifih.client.config.StateStore;
import dev.koifih.client.event.EventBus;
import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.event.events.TickEvent;
import dev.koifih.client.input.Keybinds;
import dev.koifih.client.module.ModuleManager;
import dev.koifih.client.render.screen.ScreenRenderer;
import dev.koifih.client.render.blocks.BlockIndex;
import dev.koifih.client.render.blocks.BlockRenderer;
import dev.koifih.client.render.world.WorldRenderer;
import dev.koifih.client.rotation.RotationManager;
import dev.koifih.client.ui.HudEditor;
import dev.koifih.client.ui.preview.EntityPreview;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class AdinClient implements ClientModInitializer {
    public static final EventBus EVENTS = new EventBus();
    public static final ModuleManager MODULES = new ModuleManager();
    public static final RotationManager ROTATIONS = new RotationManager();

    @Override
    public void onInitializeClient() {
        StateStore.init();
        Backtracker.init();
        Keybinds.register();
        ROTATIONS.init();
        HudEditor.init();
        WorldRenderer.init();
        BlockIndex.init(BlockRenderer::onSectionChanged);
        BlockRenderer.init();
        ScreenRenderer.init();
        EVENTS.subscribe(PreTickEvent.class, Priority.HIGHEST, event -> Time.tick());
        EVENTS.subscribe(PreTickEvent.class, event -> MODULES.tickKeybinds());
        ClientTickEvents.START_CLIENT_TICK.register(client -> EVENTS.post(new PreTickEvent(client)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> EVENTS.post(new TickEvent(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> EntityPreview.clear());
        Clicks.init();
    }
}
