package dev.koifih.client.util;

import net.minecraft.client.player.LocalPlayer;

public final class SlotSwap {
    private int original = Hotbar.NONE;
    private boolean silent;

    public boolean select(LocalPlayer player, int slot, boolean silentSwap) {
        boolean quiet = original != Hotbar.NONE ? silent : silentSwap;
        int selected = Hotbar.selected(player);
        if (slot == (quiet ? Hotbar.serverSlot() : selected)) return true;
        if (original == Hotbar.NONE) {
            original = selected;
            silent = quiet;
        }
        return Hotbar.swap(player, slot, quiet);
    }

    public boolean quiet(LocalPlayer player, int slot, boolean silentSwap) {
        return (original != Hotbar.NONE ? silent : silentSwap) && slot != Hotbar.selected(player);
    }

    public boolean swapped() {
        return original != Hotbar.NONE;
    }

    public void restore(LocalPlayer player, boolean swapBack) {
        if (player != null && original != Hotbar.NONE) {
            boolean done = silent ? Hotbar.resync(player) : !swapBack || Hotbar.swap(player, original, false);
            if (!done) return;
        }
        original = Hotbar.NONE;
        silent = false;
    }
}
