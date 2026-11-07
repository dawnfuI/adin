package dev.koifih.client.module.impl.macros;

import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.module.Module;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.util.Clicks;
import dev.koifih.client.util.Effects;
import dev.koifih.client.util.Game;
import dev.koifih.client.util.Hotbar;
import dev.koifih.client.util.SlotSwap;
import dev.koifih.client.util.Time;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class KeyPot extends Module {
    private static final List<Holder<MobEffect>> BUFFS = List.of(MobEffects.STRENGTH, MobEffects.SPEED);

    private final SliderSetting delay = add(new SliderSetting("delay", 100, 50, 500, Measure.MILLIS));
    private final BoolSetting silentSwap = add(new BoolSetting("silentSwap", false));
    private final SlotSwap slots = new SlotSwap();
    private final Time.Ticker sinceThrow = new Time.Ticker();
    private final Deque<Pot> pending = new ArrayDeque<>();

    private record Pot(int slot, Holder<MobEffect> effect) {
        boolean stillThere(LocalPlayer player) {
            return Effects.splash(player.getInventory().getItem(slot), effect);
        }
    }

    public KeyPot() {
        super("keyPot");
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
        pending.clear();
        slots.restore(Game.player(), true);
    }

    @Override
    protected void onActivate() {
        LocalPlayer player = mc.player;
        if (!Game.playing() || !pending.isEmpty()) return;
        queue(player, MobEffects.INSTANT_HEALTH);
        for (Holder<MobEffect> buff : BUFFS) {
            if (!player.hasEffect(buff)) queue(player, buff);
        }
        step(player);
    }

    private void onTick(PreTickEvent event) {
        if (!Game.playing()) pending.clear();
        if (sinceThrow.elapsed(delay.get())) step(mc.player);
    }

    private void queue(LocalPlayer player, Holder<MobEffect> effect) {
        int slot = Hotbar.find(player, stack -> Effects.splash(stack, effect));
        if (slot != Hotbar.NONE && pending.stream().noneMatch(pot -> pot.slot() == slot)) pending.add(new Pot(slot, effect));
    }

    private void step(LocalPlayer player) {
        pending.removeIf(pot -> !pot.stillThere(player));
        Pot next = pending.peek();
        if (next == null) {
            slots.restore(player, true);
            return;
        }
        if (!slots.select(player, next.slot(), silentSwap.get())) return;
        Clicks.use(mc, slots.quiet(player, next.slot(), silentSwap.get()));
        pending.poll();
        sinceThrow.mark();
    }
}
