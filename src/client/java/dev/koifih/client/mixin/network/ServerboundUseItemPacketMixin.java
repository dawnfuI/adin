package dev.koifih.client.mixin.network;

import dev.koifih.client.AdinClient;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerboundUseItemPacket.class)
public abstract class ServerboundUseItemPacketMixin {
    @ModifyVariable(method = "<init>(Lnet/minecraft/world/InteractionHand;IFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static float adin$yaw(float yaw) {
        return AdinClient.ROTATIONS.sentYaw(yaw);
    }

    @ModifyVariable(method = "<init>(Lnet/minecraft/world/InteractionHand;IFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static float adin$pitch(float pitch) {
        return AdinClient.ROTATIONS.sentPitch(pitch);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/InteractionHand;IFF)V", at = @At("TAIL"))
    private void adin$lock(CallbackInfo info) {
        AdinClient.ROTATIONS.lock();
    }
}
