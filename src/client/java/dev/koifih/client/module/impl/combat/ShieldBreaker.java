package dev.koifih.client.module.impl.combat;

import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.friends.FriendList;
import dev.koifih.client.mixin.accessor.MinecraftAccessor;
import dev.koifih.client.module.Module;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.util.Clicks;
import dev.koifih.client.util.Game;
import dev.koifih.client.util.Hotbar;
import dev.koifih.client.util.Players;
import dev.koifih.client.util.SlotSwap;
import dev.koifih.client.util.Time;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

public final class ShieldBreaker extends Module {
    private final SliderSetting delay = add(new SliderSetting("delay", 100, 50, 500, Measure.MILLIS));
    private final BoolSetting facing = add(new BoolSetting("facing", true));
    private final BoolSetting switchBack = add(new BoolSetting("switchBack", true));
    private final Time.Ticker sinceSwitch = new Time.Ticker();
    private final SlotSwap slots = new SlotSwap();

    public ShieldBreaker() {
        super("shieldBreaker");
    }

    @Override
    protected void onEnable() {
        listen(PreTickEvent.class, event -> onTick());
    }

    @Override
    protected void onDisable() {
        slots.restore(mc.player, switchBack.get());
    }

    private void onTick() {
        LocalPlayer player = mc.player;
        if (slots.swapped()) {
            if (!switchBack.get() || sinceSwitch.elapsed(delay.get())) slots.restore(player, switchBack.get());
            return;
        }
        if (!Game.playing() || using(player) || player.getAttackStrengthScale(0.5f) < 1f) return;
        Player target = crosshairPlayer(player);
        if (target == null || !shielded(player, target) || !holdAxe(player)) return;
        if (!Clicks.left(mc, target)) {
            mc.gameMode.attack(player, target);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    public void prepare(LocalPlayer player, Entity target) {
        if (isEnabled() && !using(player) && target instanceof Player other && shielded(player, other)) holdAxe(player);
    }

    private boolean holdAxe(LocalPlayer player) {
        if (player.getMainHandItem().is(ItemTags.AXES)) return true;
        int axe = Hotbar.find(player, stack -> stack.is(ItemTags.AXES));
        if (axe == Hotbar.NONE || !slots.select(player, axe, false)) return false;
        sinceSwitch.mark();
        return true;
    }

    private boolean shielded(LocalPlayer player, Player target) {
        return facing.get() ? Players.shieldBlocks(target, player.position()) : target.isBlocking();
    }

    private static boolean using(LocalPlayer player) {
        return player.isUsingItem() || mc.options.keyUse.isDown();
    }

    private static Player crosshairPlayer(LocalPlayer player) {
        ((MinecraftAccessor) mc).adin$pick(1f);
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player target)) return null;
        return target != player && target.isAlive() && !FriendList.protects(target) ? target : null;
    }
}
