package dev.koifih.client.module.impl.macros;

import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.module.Module;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.util.Clicks;
import dev.koifih.client.util.Game;
import dev.koifih.client.util.Hotbar;
import dev.koifih.client.util.SlotSwap;
import dev.koifih.client.util.Time;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;

public final class KeyPearl extends Module {
    private final SliderSetting delay = add(new SliderSetting("delay", 100, 50, 500, Measure.MILLIS));
    private final BoolSetting silentSwap = add(new BoolSetting("silentSwap", false));
    private final BoolSetting swapBack = add(new BoolSetting("swapBack", true));
    private final SlotSwap slots = new SlotSwap();
    private final Time.Ticker sinceThrow = new Time.Ticker();

    public KeyPearl() {
        super("keyPearl");
        swapBack.visibleWhen(() -> !silentSwap.get());
    }

    @Override
    public boolean activatable() {
        return true;
    }

    @Override
    protected void onEnable() {
        listen(PreTickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        slots.restore(Game.player(), swapBack.get());
    }

    @Override
    protected void onActivate() {
        LocalPlayer player = mc.player;
        if (!Game.playing(mc) || player.isUsingItem()) return;
        int slot = player.getMainHandItem().is(Items.ENDER_PEARL) ? Hotbar.selected(player) : Hotbar.find(player, stack -> stack.is(Items.ENDER_PEARL));
        if (slot == Hotbar.NONE || player.getCooldowns().isOnCooldown(player.getInventory().getItem(slot))) return;
        if (!slots.select(player, slot, silentSwap.get())) return;
        Clicks.use(mc, slots.quiet(player, slot, silentSwap.get()));
        sinceThrow.mark();
    }

    private void onTick(PreTickEvent event) {
        if (sinceThrow.elapsed(delay.get())) slots.restore(event.client().player, swapBack.get());
    }
}
