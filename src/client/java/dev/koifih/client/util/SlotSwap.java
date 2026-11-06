package dev.koifih.client.util;

import net.minecraft.client.player.LocalPlayer;
import java.util.HashSet;
import java.util.Set;

public final class SlotSwap {
    private static final Set<SlotSwap> ACTIVE = new HashSet<>();

    private int original = Hotbar.NONE;
    private boolean silent;

    public boolean select(LocalPlayer player, int slot, boolean silentSwap) {
        boolean quiet = original != Hotbar.NONE ? silent : silentSwap;
        int selected = Hotbar.selected(player);
        if (slot == (quiet ? Hotbar.serverSlot() : selected)) return true;
        if (original == Hotbar.NONE) {
            original = home(selected);
            silent = quiet;
            ACTIVE.add(this);
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
        ACTIVE.remove(this);
    }

    private static int home(int selected) {
        for (SlotSwap swap : ACTIVE) {
            if (!swap.silent) return swap.original;
        }
        return selected;
    }
}
