package dev.koifih.client.module.impl.player;

import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.util.Clicks;
import dev.koifih.client.mixin.accessor.MultiPlayerGameModeAccessor;
import dev.koifih.client.module.Module;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.util.Game;
import dev.koifih.client.util.Hotbar;
import dev.koifih.client.util.Time;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public final class KeyPearl extends Module {
    private final SliderSetting delay = add(new SliderSetting("delay", 100, 50, 500, Measure.MILLIS));
    private final BoolSetting silentSwap = add(new BoolSetting("silentSwap", false));
    private final BoolSetting swapBack = add(new BoolSetting("swapBack", true));
    private final Time.Ticker sinceSwap = new Time.Ticker();
    private int originalSlot = Hotbar.NONE;
    private boolean silent;
    private boolean threw;

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
        restore(Game.player());
    }

    @Override
    protected void onActivate() {
        LocalPlayer player = mc.player;
        if (!Game.playing(mc) || player.isUsingItem()) return;
        int selected = Hotbar.selected(player);
        int slot = player.getMainHandItem().is(Items.ENDER_PEARL) ? selected : Hotbar.find(player, stack -> stack.is(Items.ENDER_PEARL));
        if (slot == Hotbar.NONE || player.getCooldowns().isOnCooldown(player.getInventory().getItem(slot))) return;
        if (slot != selected) {
            if (originalSlot == Hotbar.NONE) originalSlot = selected;
            silent = silentSwap.get();
            if (!Hotbar.swap(player, slot, silent)) {
                originalSlot = Hotbar.NONE;
                return;
            }
            sinceSwap.mark();
        }
        if (silent && slot != selected) {
            ((MultiPlayerGameModeAccessor) mc.gameMode).adin$startPrediction(mc.level,
                    sequence -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence, player.getYRot(), player.getXRot()));
        } else if (!Clicks.right(mc) && !mc.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction()) {
            return;
        }
        player.swing(InteractionHand.MAIN_HAND);
        threw = true;
    }

    private void onTick(PreTickEvent event) {
        boolean thrown = threw;
        threw = false;
        if (originalSlot == Hotbar.NONE || thrown) return;
        LocalPlayer player = event.client().player;
        if (player == null || (!silent && !swapBack.get()) || sinceSwap.elapsed(delay.get())) restore(player);
    }

    private void restore(LocalPlayer player) {
        if (player != null && originalSlot != Hotbar.NONE) {
            boolean done = silent ? Hotbar.resync(player) : !swapBack.get() || Hotbar.swap(player, originalSlot, false);
            if (!done) return;
        }
        originalSlot = Hotbar.NONE;
        silent = false;
    }
}
