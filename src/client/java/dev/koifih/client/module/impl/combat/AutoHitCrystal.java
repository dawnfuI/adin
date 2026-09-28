package dev.koifih.client.module.impl.combat;

import dev.koifih.client.AdinClient;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.util.Clicks;
import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.mixin.accessor.MultiPlayerGameModeAccessor;
import dev.koifih.client.module.Module;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.util.Game;
import dev.koifih.client.util.Hotbar;
import dev.koifih.client.util.Placement;
import dev.koifih.client.util.Players;
import dev.koifih.client.util.Time;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class AutoHitCrystal extends Module {
    private final SliderSetting delay = add(new SliderSetting("delay", 100, 50, 500, Measure.MILLIS));
    private final BoolSetting silentSwap = add(new BoolSetting("silentSwap", false));
    private final BoolSetting swapBack = add(new BoolSetting("swapBack", true));
    private final Time.Ticker sinceSwap = new Time.Ticker();
    private int originalSlot = Hotbar.NONE;
    private boolean silent;
    private boolean placed;

    public AutoHitCrystal() {
        super("autoHitCrystal");
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
        if (!Game.playing(mc) || Players.consuming(player) || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockState target = mc.level.getBlockState(hit.getBlockPos());
        if (target.is(Blocks.OBSIDIAN) || target.is(Blocks.BEDROCK)) return;
        int selected = Hotbar.selected(player);
        int slot = player.getMainHandItem().is(Items.OBSIDIAN) ? selected : Hotbar.find(player, stack -> stack.is(Items.OBSIDIAN));
        if (slot == Hotbar.NONE || !Placement.placeable(player, player.getInventory().getItem(slot), hit) || !Placement.ready()) return;
        boolean quiet = originalSlot != Hotbar.NONE ? silent : silentSwap.get();
        if (slot != (quiet ? Hotbar.serverSlot() : selected)) {
            if (originalSlot == Hotbar.NONE) {
                originalSlot = selected;
                silent = quiet;
            }
            if (!Hotbar.swap(player, slot, quiet)) {
                originalSlot = Hotbar.NONE;
                return;
            }
            sinceSwap.mark();
        }
        if (quiet && slot != selected) {
            ItemStack stack = player.getInventory().getItem(slot);
            BlockPos pos = target.canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
            ((MultiPlayerGameModeAccessor) mc.gameMode).adin$startPrediction(mc.level, sequence -> {
                Placement.predict(player, stack, hit);
                return new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, sequence);
            });
            SoundType sound = Blocks.OBSIDIAN.defaultBlockState().getSoundType();
            mc.level.playSound(player, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
            player.swing(InteractionHand.MAIN_HAND);
        } else if (!Clicks.right(mc, hit)) {
            if (!mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction()) return;
            player.swing(InteractionHand.MAIN_HAND);
        }
        placed = true;
    }

    public int holding() {
        return originalSlot;
    }

    private void onTick(PreTickEvent event) {
        boolean justPlaced = placed;
        placed = false;
        if (originalSlot == Hotbar.NONE || justPlaced) return;
        if (AdinClient.MODULES.get(AutoCrystal.class).holding()) {
            originalSlot = Hotbar.NONE;
            silent = false;
            return;
        }
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
