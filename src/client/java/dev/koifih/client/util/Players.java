package dev.koifih.client.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Players {
    public static boolean canCrit(LocalPlayer player) {
        return player.fallDistance > 0.0 && !player.onClimbable() && !player.isInWater()
                && !player.isMobilityRestricted() && !player.isPassenger();
    }

    public static boolean shieldBlocks(Player target, Vec3 attacker) {
        Vec3 view = target.calculateViewVector(0f, target.getYHeadRot());
        return target.isBlocking() && attacker.subtract(target.position()).horizontal().normalize().dot(view) >= 0;
    }

    public static boolean consuming(LocalPlayer player) {
        if (player.isUsingItem()) return true;
        ItemStack main = player.getMainHandItem();
        if (main.has(DataComponents.CONSUMABLE)) return true;
        return player.getOffhandItem().has(DataComponents.CONSUMABLE) && !(main.getItem() instanceof BlockItem);
    }
}
