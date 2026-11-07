package dev.koifih.client.mixin.network;

import dev.koifih.client.AdinClient;
import dev.koifih.client.event.events.AttackEvent;
import dev.koifih.client.module.impl.combat.Backtrack;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void adin$attack(Player player, Entity target, CallbackInfo info) {
        if (!AdinClient.MODULES.get(Backtrack.class).permits(target)) {
            info.cancel();
            return;
        }
        AdinClient.EVENTS.post(new AttackEvent(player, target));
    }
}
