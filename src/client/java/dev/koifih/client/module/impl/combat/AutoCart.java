package dev.koifih.client.module.impl.combat;

import dev.koifih.client.AdinClient;
import dev.koifih.client.event.Priority;
import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.module.Module;
import dev.koifih.client.rotation.Rotation;
import dev.koifih.client.rotation.RotationConfig;
import dev.koifih.client.rotation.Smoothing;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.EnumSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.util.Game;
import dev.koifih.client.util.Hotbar;
import dev.koifih.client.util.Placement;
import dev.koifih.client.util.SlotSwap;
import dev.koifih.client.util.ProjectilePrediction;
import dev.koifih.client.util.Time;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public final class AutoCart extends Module {
    private static final String[] MODES = {"Normal", "Silent"};
    private static final int SILENT = 1;
    private static final int DROP_SEARCH = 8;
    private static final double CART_CLEARANCE = 1.0;
    private static final double ARROW_SEARCH = 8.0;
    private static final double MOVING = 0.01;
    private static final Predicate<ItemStack> RAIL = stack -> stack.getItem() instanceof BlockItem item
            && item.getBlock().defaultBlockState().is(BlockTags.RAILS);

    private enum Stage { IDLE, TRACK, RAIL, CART }

    private final EnumSetting mode = add(new EnumSetting("mode", 0, MODES));
    private final SliderSetting smoothness = add(new SliderSetting("smoothness", 50, 0, 100, Measure.PERCENT));
    private final SliderSetting delay = add(new SliderSetting("delay", 100, 50, 500, Measure.MILLIS));
    private final BoolSetting silentSwap = add(new BoolSetting("silentSwap", false));
    private final BoolSetting swapBack = add(new BoolSetting("swapBack", true));
    private final Time.Ticker pacer = new Time.Ticker();
    private Stage stage = Stage.IDLE;
    private BlockPos ground;
    private AbstractArrow tracked;
    private final Set<Integer> seenArrows = new HashSet<>();
    private final SlotSwap slots = new SlotSwap();

    public AutoCart() {
        super("autoCart");
        swapBack.visibleWhen(() -> !silentSwap.get());
    }

    @Override
    public String info() {
        return mode.selected();
    }

    @Override
    protected void onEnable() {
        listen(PreTickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        stop(Game.player());
    }

    private AbstractArrow firedArrow(LocalPlayer player) {
        AbstractArrow fired = null;
        for (AbstractArrow arrow : mc.level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(ARROW_SEARCH))) {
            if (arrow.getOwner() != player || arrow.getDeltaMovement().lengthSqr() < MOVING || !seenArrows.add(arrow.getId())) continue;
            fired = arrow;
        }
        return fired;
    }

    private boolean flying(AbstractArrow arrow) {
        return arrow != null && !arrow.isRemoved() && arrow.getDeltaMovement().lengthSqr() >= MOVING;
    }

    private void track(LocalPlayer player) {
        if (!flying(tracked)) {
            stop(player);
            return;
        }
        BlockHitResult landing = ProjectilePrediction.arrow(player, tracked);
        if (landing == null) return;
        BlockPos found = ground(landing);
        if (found == null || !Vec3.atCenterOf(found.above()).closerThan(player.getEyePosition(), player.blockInteractionRange())) return;
        if (!mc.level.getEntitiesOfClass(MinecartTNT.class, new AABB(found.above()).inflate(CART_CLEARANCE)).isEmpty()) {
            stop(player);
            return;
        }
        ground = found;
        stage = Stage.RAIL;
    }

    private void onTick(PreTickEvent event) {
        LocalPlayer player = event.client().player;
        if (player == null) {
            stage = Stage.IDLE;
            return;
        }
        if (stage == Stage.IDLE) {
            AbstractArrow arrow = Game.playing(mc) && holdsFlameBow(player) ? firedArrow(player) : null;
            if (!pacer.ready()) return;
            slots.restore(player, swapBack.get());
            if (arrow != null) {
                tracked = arrow;
                stage = Stage.TRACK;
            }
            return;
        }
        if (!Game.playing(mc) || !flying(tracked)) {
            stop(player);
            return;
        }
        if (stage == Stage.TRACK) {
            track(player);
            return;
        }
        if (player.isUsingItem()) return;
        BlockPos rail = ground.above();
        BlockState state = mc.level.getBlockState(rail);
        if (stage == Stage.RAIL) {
            if (state.is(BlockTags.RAILS)) {
                stage = Stage.CART;
            } else {
                BlockHitResult hit = Placement.placeInto(mc.level, player.getEyePosition(), rail);
                if (hit == null || !aimed(player, hit)) return;
                if (use(player, hit, RAIL.and(stack -> Placement.placeable(player, stack, hit)), true)) stage = Stage.CART;
                return;
            }
        }
        if (!state.is(BlockTags.RAILS)) {
            if (pacer.elapsed(delay.get() * 4L)) stop(player);
            return;
        }
        BlockHitResult hit = Placement.clickOn(mc.level, player.getEyePosition(), rail);
        if (hit == null || !aimed(player, hit)) return;
        if (use(player, hit, stack -> stack.is(Items.TNT_MINECART), false)) stage = Stage.IDLE;
    }

    private boolean aimed(LocalPlayer player, BlockHitResult hit) {
        Vec3 point = hit.getLocation();
        Smoothing smoothing = Smoothing.EASE_OUT_CUBIC;
        RotationConfig config = mode.get() == SILENT
                ? RotationConfig.silent(smoothness.get() / 100f, smoothing)
                : RotationConfig.visible(smoothness.get() / 100f, smoothing);
        AdinClient.ROTATIONS.aim(partialTick -> Rotation.toward(player.getEyePosition(partialTick), point), Priority.HIGH, config);
        if (!pacer.ready()) return false;
        Vec3 eye = player.getEyePosition();
        Vec3 look = AdinClient.ROTATIONS.rotation(player).direction();
        return Placement.looksAt(mc.level, hit.getBlockPos(), eye, look, player.blockInteractionRange());
    }

    private BlockPos ground(BlockHitResult landing) {
        BlockPos start = landing.getDirection() == Direction.UP
                ? landing.getBlockPos()
                : landing.getBlockPos().relative(landing.getDirection()).below();
        for (int drop = 0; drop < DROP_SEARCH; drop++) {
            BlockPos candidate = start.below(drop);
            BlockState above = mc.level.getBlockState(candidate.above());
            if (!above.canBeReplaced() && !above.is(BlockTags.RAILS)) continue;
            if (mc.level.getBlockState(candidate).isFaceSturdy(mc.level, candidate, Direction.UP)) return candidate;
        }
        return null;
    }

    private static boolean holdsFlameBow(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(Items.BOW)) return false;
        for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
            if (enchantment.is(Enchantments.FLAME)) return true;
        }
        return false;
    }

    private boolean use(LocalPlayer player, BlockHitResult hit, Predicate<ItemStack> matcher, boolean placing) {
        int slot = Hotbar.find(player, matcher);
        if (slot == Hotbar.NONE) {
            stop(player);
            return false;
        }
        if (!Placement.ready()) return false;
        if (!slots.select(player, slot, silentSwap.get())) return false;
        Placement.use(player, hit, slot, slots.quiet(player, slot, silentSwap.get()), placing);
        pacer.pace(delay.get());
        return true;
    }

    private void stop(LocalPlayer player) {
        stage = Stage.IDLE;
        tracked = null;
        seenArrows.clear();
        slots.restore(player, swapBack.get());
    }
}
