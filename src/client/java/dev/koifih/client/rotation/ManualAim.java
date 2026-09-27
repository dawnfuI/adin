package dev.koifih.client.rotation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ManualAim {
    public static Vec3 look(LocalPlayer player) {
        return Rotation.of(player).direction();
    }

    public static BlockHitResult block(LocalPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(look(player).scale(range));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    public static BlockHitResult clicking(LocalPlayer player, BlockPos block) {
        BlockHitResult hit = block(player, player.blockInteractionRange());
        return hit != null && hit.getBlockPos().equals(block) ? hit : null;
    }

    public static BlockHitResult placing(LocalPlayer player, BlockPos target) {
        BlockHitResult hit = block(player, player.blockInteractionRange());
        return hit != null && hit.getBlockPos().relative(hit.getDirection()).equals(target) ? hit : null;
    }
}
