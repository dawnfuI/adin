package dev.koifih.client.module.impl.player;

import dev.koifih.client.event.events.MouseMoveEvent;
import dev.koifih.client.mixin.accessor.AbstractContainerScreenAccessor;
import dev.koifih.client.module.Module;
import dev.koifih.client.setting.EnumSetting;
import dev.koifih.client.setting.HotbarSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.util.Clicks;
import dev.koifih.client.util.Hotbar;
import dev.koifih.client.util.Time;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Refill extends Module {
    private static final String[] MODES = {"Pot", "Bed", "Cart"};
    private static final int POT = 0;
    private static final int BED = 1;
    private static final int CART = 2;
    private static final double SLOT_HOVER_MIN = -1;
    private static final double SLOT_HOVER_MAX = 17;

    private record Crossing(Slot slot, double entry) {}

    private final EnumSetting mode = add(new EnumSetting("mode", POT, MODES));
    private final HotbarSetting slots = add(new HotbarSetting("slots", 1, 2, 3, 4, 5, 6, 7, 8));
    private final SliderSetting delay = add(new SliderSetting("delay", 50, 0, 500, Measure.MILLIS));
    private final Time.Ticker sinceMove = new Time.Ticker();
    private AbstractContainerScreen<?> lastScreen;
    private double lastX;
    private double lastY;

    public Refill() {
        super("refill");
    }

    @Override
    public String info() {
        return mode.selected();
    }

    @Override
    protected void onEnable() {
        listen(MouseMoveEvent.class, this::onMove);
    }

    @Override
    protected void onDisable() {
        lastScreen = null;
    }

    private void onMove(MouseMoveEvent event) {
        LocalPlayer player = event.client().player;
        if (player == null || !(event.client().gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            lastScreen = null;
            return;
        }
        if (screen != lastScreen) {
            lastScreen = screen;
            lastX = event.x();
            lastY = event.y();
        }
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        if (Clicks.simulate) {
            lastX = event.x();
            lastY = event.y();
            Slot hovered = accessor.adin$getHoveredSlot();
            if (hovered != null && movable(hovered, player) && matches(hovered.getItem())) move(accessor, player, hovered);
            return;
        }
        double fromX = lastX - accessor.adin$getLeftPos();
        double fromY = lastY - accessor.adin$getTopPos();
        double toX = event.x() - accessor.adin$getLeftPos();
        double toY = event.y() - accessor.adin$getTopPos();
        List<Crossing> crossed = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (!movable(slot, player) || !matches(slot.getItem())) continue;
            double entry = entry(fromX - slot.x, fromY - slot.y, toX - slot.x, toY - slot.y);
            if (entry >= 0) crossed.add(new Crossing(slot, entry));
        }
        crossed.sort(Comparator.comparingDouble(Crossing::entry));
        lastX = event.x();
        lastY = event.y();
        for (Crossing crossing : crossed) {
            if (!move(accessor, player, crossing.slot())) return;
        }
    }

    private boolean move(AbstractContainerScreenAccessor screen, LocalPlayer player, Slot slot) {
        if (!sinceMove.elapsed(delay.get())) return false;
        int target = emptyChosenSlot(player);
        if (target == Hotbar.NONE) return false;
        if (!Clicks.hotbar(mc, target)) screen.adin$slotClicked(slot, slot.index, target, ContainerInput.SWAP);
        sinceMove.mark();
        return true;
    }

    private static double entry(double x0, double y0, double x1, double y1) {
        double enter = 0;
        double exit = 1;
        double[] from = {x0, y0};
        double[] to = {x1, y1};
        for (int axis = 0; axis < 2; axis++) {
            double delta = to[axis] - from[axis];
            if (delta == 0) {
                if (from[axis] < SLOT_HOVER_MIN || from[axis] >= SLOT_HOVER_MAX) return -1;
                continue;
            }
            double near = (SLOT_HOVER_MIN - from[axis]) / delta;
            double far = (SLOT_HOVER_MAX - from[axis]) / delta;
            enter = Math.max(enter, Math.min(near, far));
            exit = Math.min(exit, Math.max(near, far));
            if (enter > exit) return -1;
        }
        return enter;
    }

    private boolean movable(Slot slot, LocalPlayer player) {
        if (slot.container != player.getInventory()) return slot.mayPickup(player);
        int index = slot.getContainerSlot();
        if (index < HotbarSetting.SLOTS) return !slots.has(index);
        return index < Inventory.INVENTORY_SIZE;
    }

    private int emptyChosenSlot(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < HotbarSetting.SLOTS; slot++) {
            if (slots.has(slot) && inventory.getItem(slot).isEmpty()) return slot;
        }
        return Hotbar.NONE;
    }

    private boolean matches(ItemStack stack) {
        return switch (mode.get()) {
            case POT -> isHealingPotion(stack);
            case BED -> stack.is(ItemTags.BEDS);
            case CART -> stack.is(Items.TNT_MINECART);
            default -> false;
        };
    }

    private static boolean isHealingPotion(ItemStack stack) {
        if (!stack.is(Items.SPLASH_POTION)) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && (contents.is(Potions.HEALING) || contents.is(Potions.STRONG_HEALING));
    }
}
