package dev.koifih.client.module.impl.player;

import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.event.events.TotemPopEvent;
import dev.koifih.client.mixin.accessor.AbstractContainerScreenAccessor;
import dev.koifih.client.module.Module;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.EnumSetting;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.util.Hotbar;
import dev.koifih.client.util.Maths;
import dev.koifih.client.util.Time;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

public final class AutoTotem extends Module {
    private static final String[] MODES = {"Auto", "Hover"};
    private static final int HOVER = 1;
    private static final String[] TIMINGS = {"Normal", "Totem Guard"};
    private static final int TOTEM_GUARD = 1;
    private static final long STEP_TIMEOUT_MILLIS = 2000;
    private static final int MIN_SLOW_TICKS = 2;
    private static final int MAX_SLOW_TICKS = 3;

    private final EnumSetting mode = add(new EnumSetting("mode", 0, MODES));
    private final BoolSetting openInventory = add(new BoolSetting("openInventory", true));
    private final SliderSetting slot = add(new SliderSetting("slot", 9, 1, 9));
    private final SliderSetting delay = add(new SliderSetting("delay", 50, 0, 500, Measure.MILLIS));
    private final EnumSetting timing = add(new EnumSetting("timing", 0, TIMINGS));
    private final Time.Ticker sinceStep = new Time.Ticker();
    private Step step = Step.IDLE;
    private long wait;
    private boolean slow;
    private InteractionHand popped = InteractionHand.OFF_HAND;

    private enum Step { IDLE, POPPED, OPENED, HAND, HOTBAR }

    public AutoTotem() {
        super("autoTotem");
        openInventory.visibleWhen(() -> mode.get() == HOVER);
    }

    @Override
    public String info() {
        return mode.selected();
    }

    @Override
    protected void onEnable() {
        listen(TotemPopEvent.class, this::onPop);
        listen(PreTickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        step = Step.IDLE;
    }

    private void onPop(TotemPopEvent event) {
        if (step != Step.IDLE && step != Step.POPPED) return;
        popped = event.client().player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        advance(Step.POPPED);
    }

    private void onTick(PreTickEvent event) {
        LocalPlayer player = event.client().player;
        if (player == null) {
            step = Step.IDLE;
            return;
        }
        if (step == Step.POPPED) open(player);
        switch (step) {
            case OPENED -> fill(player, handSource(player), handButton(player), -1, Step.HAND);
            case HAND -> fill(player, hotbarSource(player), slot.get() - 1, reserved(), Step.HOTBAR);
            case HOTBAR -> close();
            default -> {}
        }
    }

    private void open(LocalPlayer player) {
        if (player.getItemInHand(popped).is(Items.TOTEM_OF_UNDYING)) {
            if (sinceStep.elapsed(STEP_TIMEOUT_MILLIS)) step = Step.IDLE;
        } else if (handSource(player) == null && hotbarSource(player) == null) {
            step = Step.IDLE;
        } else if (manual()) {
            if (mc.gui.screen() instanceof InventoryScreen) advance(Step.OPENED);
        } else if (sinceStep.elapsed(STEP_TIMEOUT_MILLIS)) {
            step = Step.IDLE;
        } else if (mc.gui.screen() == null) {
            mc.gui.setScreen(new InventoryScreen(player));
            advance(Step.OPENED);
        }
    }

    private void fill(LocalPlayer player, Slot source, int button, int skip, Step next) {
        if (!screenOpen() || !sinceStep.elapsed(wait)) return;
        if (source == null) {
            advance(next);
            return;
        }
        if (mode.get() == HOVER) {
            source = hoveredTotem(skip);
            if (source == null) return;
        }
        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, source.index, button, ContainerInput.SWAP, player);
        if (step == Step.OPENED) slow = !slow;
        advance(next);
    }

    private void close() {
        if (!screenOpen() || !sinceStep.elapsed(wait)) return;
        if (!manual() && mc.gui.screen() instanceof InventoryScreen screen) screen.onClose();
        step = Step.IDLE;
    }

    private boolean screenOpen() {
        if (mc.gui.screen() instanceof InventoryScreen) return true;
        if (manual()) advance(Step.POPPED);
        else step = Step.IDLE;
        return false;
    }

    private boolean manual() {
        return mode.get() == HOVER && !openInventory.get();
    }

    private Slot hoveredTotem(int skip) {
        Slot hovered = ((AbstractContainerScreenAccessor) mc.gui.screen()).adin$getHoveredSlot();
        if (hovered == null || hovered.index == skip || hovered.index == InventoryMenu.SHIELD_SLOT) return null;
        return hovered.getItem().is(Items.TOTEM_OF_UNDYING) ? hovered : null;
    }

    private void advance(Step next) {
        step = next;
        sinceStep.mark();
        boolean opened = next == Step.OPENED;
        long base = opened && timing.get() == TOTEM_GUARD ? reaction() : delay.get();
        wait = opened ? Math.max(base, Time.TICK_MILLIS) : base;
    }

    private long reaction() {
        int ticks = slow ? (int) Maths.random(MIN_SLOW_TICKS, MAX_SLOW_TICKS + 1) : 0;
        return delay.get() + ticks * Time.TICK_MILLIS;
    }

    private int handButton(LocalPlayer player) {
        return popped == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : Hotbar.selected(player);
    }

    private Slot handSource(LocalPlayer player) {
        if (player.getItemInHand(popped).is(Items.TOTEM_OF_UNDYING)) return null;
        Slot source = totemIn(player, InventoryMenu.INV_SLOT_START, InventoryMenu.USE_ROW_SLOT_END, reserved());
        return source != null ? source : totemIn(player, reserved(), reserved() + 1, -1);
    }

    private Slot hotbarSource(LocalPlayer player) {
        if (player.getInventory().getItem(slot.get() - 1).is(Items.TOTEM_OF_UNDYING)) return null;
        return totemIn(player, InventoryMenu.INV_SLOT_START, InventoryMenu.USE_ROW_SLOT_END, reserved());
    }

    private int reserved() {
        return InventoryMenu.USE_ROW_SLOT_START + slot.get() - 1;
    }

    private static Slot totemIn(LocalPlayer player, int from, int to, int skip) {
        InventoryMenu menu = player.inventoryMenu;
        for (int index = from; index < to; index++) {
            Slot slot = menu.getSlot(index);
            if (index != skip && slot.getItem().is(Items.TOTEM_OF_UNDYING)) return slot;
        }
        return null;
    }
}
