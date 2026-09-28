package dev.koifih.client.util;

import dev.koifih.client.mixin.accessor.MultiPlayerGameModeAccessor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Hotbar {
    public static final int NONE = -1;

    private static boolean acted;
    private static int silentSlot = NONE;
    private static int silentBase = NONE;

    public static boolean acted() {
        return acted;
    }

    public static int selected(LocalPlayer player) {
        return player.getInventory().getSelectedSlot();
    }

    public static int find(LocalPlayer player, Predicate<ItemStack> matcher) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.SELECTION_SIZE; slot++) {
            if (matcher.test(inventory.getItem(slot))) return slot;
        }
        return NONE;
    }

    public static int serverSlot() {
        int carried = carriedIndex();
        return silentSlot != NONE && carried == silentBase ? silentSlot : carried;
    }

    public static boolean swap(LocalPlayer player, int slot, boolean silent) {
        if (!Inventory.isHotbarSlot(slot) || acted) return false;
        if (silent) selectSilently(player, slot);
        else select(player, slot);
        return true;
    }

    public static boolean resync(LocalPlayer player) {
        int slot = selected(player);
        if (serverSlot() != slot) {
            if (acted) return false;
            send(player, slot);
        }
        silentSlot = NONE;
        return true;
    }

    public static void sent(Packet<?> packet) {
        if (packet instanceof ServerboundClientTickEndPacket) acted = false;
        else if (isAction(packet)) acted = true;
    }

    private static boolean isAction(Packet<?> packet) {
        return packet instanceof ServerboundAttackPacket || packet instanceof ServerboundInteractPacket
                || packet instanceof ServerboundUseItemPacket || packet instanceof ServerboundUseItemOnPacket
                || packet instanceof ServerboundPlayerActionPacket || packet instanceof ServerboundSwingPacket
                || packet instanceof ServerboundPlayerCommandPacket || packet instanceof ServerboundContainerClickPacket
                || packet instanceof ServerboundPickItemFromBlockPacket || packet instanceof ServerboundPickItemFromEntityPacket;
    }

    private static void select(LocalPlayer player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        if (silentSlot != NONE && serverSlot() != slot && carriedIndex() == slot) send(player, slot);
        silentSlot = NONE;
    }

    private static void selectSilently(LocalPlayer player, int slot) {
        if (serverSlot() == slot) return;
        send(player, slot);
        silentSlot = slot;
        silentBase = carriedIndex();
    }

    private static void send(LocalPlayer player, int slot) {
        player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static int carriedIndex() {
        return ((MultiPlayerGameModeAccessor) Minecraft.getInstance().gameMode).adin$getCarriedIndex();
    }
}
