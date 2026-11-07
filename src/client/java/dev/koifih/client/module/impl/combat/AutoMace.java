package dev.koifih.client.module.impl.combat;

import dev.koifih.client.AdinClient;
import dev.koifih.client.event.Priority;
import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.friends.FriendList;
import dev.koifih.client.mixin.accessor.MinecraftAccessor;
import dev.koifih.client.module.Module;
import dev.koifih.client.module.impl.misc.AntiBot;
import dev.koifih.client.rotation.Rotation;
import dev.koifih.client.rotation.RotationMode;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.EnumSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.util.Clicks;
import dev.koifih.client.util.Game;
import dev.koifih.client.util.Hotbar;
import dev.koifih.client.util.Maths;
import dev.koifih.client.util.SlotSwap;
import dev.koifih.client.util.Time;
import java.util.Comparator;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AutoMace extends Module {
    private static final Predicate<ItemStack> MACE = stack -> stack.is(Items.MACE);
    private static final Predicate<ItemStack> AXE = stack -> stack.is(ItemTags.AXES);
    private static final long SMASH_GAP_MILLIS = 1000;
    private static final double AIM_INSET = 0.1;
    private static final float SETTLED_DEGREES = 1f;

    private final EnumSetting rotation = add(new EnumSetting("rotation", RotationMode.MANUAL, RotationMode.NAMES));
    private final SliderSetting delay = add(new SliderSetting("delay", 100, 50, 500, Measure.MILLIS));
    private final BoolSetting silentSwap = add(new BoolSetting("silentSwap", false));
    private final BoolSetting swapBack = add(new BoolSetting("swapBack", true));
    private final BoolSetting stunSlam = add(new BoolSetting("stunSlam", false));
    private final Time.Ticker sinceSwap = new Time.Ticker();
    private final Time.Ticker sinceSmash = new Time.Ticker();
    private final SlotSwap slots = new SlotSwap();
    private Player stunned;
    private Rotation origin;

    public AutoMace() {
        super("autoMace");
        swapBack.visibleWhen(() -> !silentSwap.get());
    }

    @Override
    public String info() {
        return rotation.selected();
    }

    @Override
    protected void onEnable() {
        listen(PreTickEvent.class, event -> onTick());
    }

    @Override
    protected void onDisable() {
        stunned = null;
        origin = null;
        slots.restore(mc.player, swapBack.get());
    }

    private void onTick() {
        Player target = stunned;
        stunned = null;
        if (target != null) slam(mc.player, target);
        else attack(mc.player);
        if (stunned == null) restoreView(mc.player);
    }

    private void attack(LocalPlayer player) {
        if (sinceSwap.elapsed(delay.get())) slots.restore(player, swapBack.get());
        if (!ready(player) || player.getAttackStrengthScale(0.5f) < 1f || !sinceSmash.elapsed(SMASH_GAP_MILLIS)) return;
        Player target = rotation.get() == RotationMode.MANUAL ? crosshairPlayer() : nearestPlayer(player);
        if (target == null || Hotbar.find(player, MACE) == Hotbar.NONE) return;
        boolean stun = stunSlam.get() && shields(target, player) && swap(player, AXE);
        if (!stun && !swap(player, MACE)) return;
        strike(player, target);
        if (stun) stunned = target;
        else smashed(player);
    }

    private void slam(LocalPlayer player, Player target) {
        if (!ready(player) || !player.isWithinEntityInteractionRange(target, 0) || !swap(player, MACE)) return;
        strike(player, target);
        smashed(player);
    }

    private void strike(LocalPlayer player, Player target) {
        sinceSwap.mark();
        boolean aimed = crosshairPlayer() == target;
        if (aimed && Clicks.left(mc, target)) return;
        if (!aimed) {
            int mode = rotation.get() == RotationMode.MANUAL ? RotationMode.SILENT : rotation.get();
            if (mode == RotationMode.AUTO && origin == null) origin = Rotation.of(player);
            AdinClient.ROTATIONS.face(player, aimPoint(player, target), Priority.HIGH, RotationMode.config(mode));
        }
        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
    }

    private void restoreView(LocalPlayer player) {
        if (origin == null || player == null) return;
        if (Rotation.of(player).distanceTo(origin) < SETTLED_DEGREES) origin = null;
        else AdinClient.ROTATIONS.aim(origin, Priority.HIGH, RotationMode.config(RotationMode.AUTO));
    }

    private boolean swap(LocalPlayer player, Predicate<ItemStack> item) {
        int slot = Hotbar.find(player, item);
        return slot != Hotbar.NONE && slots.select(player, slot, silentSwap.get());
    }

    private void smashed(LocalPlayer player) {
        player.resetFallDistance();
        sinceSmash.mark();
    }

    private static Vec3 aimPoint(LocalPlayer player, Player target) {
        return Maths.nearest(target.getBoundingBox().deflate(AIM_INSET), player.getEyePosition());
    }

    private static boolean shields(Player target, LocalPlayer player) {
        Vec3 view = target.calculateViewVector(0f, target.getYHeadRot());
        return target.isBlocking() && player.position().subtract(target.position()).horizontal().normalize().dot(view) >= 0;
    }

    private static boolean ready(LocalPlayer player) {
        return Game.playing() && !player.isUsingItem() && MaceItem.canSmashAttack(player);
    }

    private static Player crosshairPlayer() {
        ((MinecraftAccessor) mc).adin$pick(1f);
        return mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player target && valid(target) ? target : null;
    }

    private static Player nearestPlayer(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        return mc.level.players().stream()
                .filter(target -> valid(target) && !target.isSpectator() && player.isWithinEntityInteractionRange(target, 0) && visible(player, target))
                .min(Comparator.comparingDouble(target -> target.getBoundingBox().distanceToSqr(eye)))
                .orElse(null);
    }

    private static boolean visible(LocalPlayer player, Player target) {
        ClipContext context = new ClipContext(player.getEyePosition(), aimPoint(player, target), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        return mc.level.clip(context).getType() == HitResult.Type.MISS;
    }

    private static boolean valid(Player target) {
        return target != mc.player && target.isAlive() && !FriendList.protects(target) && !AntiBot.flags(target);
    }
}
