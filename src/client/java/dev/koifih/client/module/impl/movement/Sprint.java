package dev.koifih.client.module.impl.movement;

import dev.koifih.client.event.events.TickEvent;
import dev.koifih.client.module.Module;
public final class Sprint extends Module {

    boolean wasSprinting;

    public Sprint() {
        super("sprint");
    }

    @Override
    protected void onEnable() {
        wasSprinting = mc.options.toggleSprint().get();
        listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        if (wasSprinting) {
            mc.options.toggleSprint().set(true);
        }
    }

    private void onTick(TickEvent event) {
        if (mc.options.toggleSprint().get()) {
            mc.options.toggleSprint().set(false);
        }
        mc.options.keySprint.setDown(true);
    }
}
