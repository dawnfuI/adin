package dev.koifih.client.module.impl.combat;

import dev.koifih.client.AdinClient;
import dev.koifih.client.event.Priority;
import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.mixin.accessor.MultiPlayerGameModeAccessor;
import dev.koifih.client.module.Module;
import dev.koifih.client.module.impl.hud.Notifications;
import dev.koifih.client.rotation.ManualAim;
import dev.koifih.client.rotation.Rotation;
import dev.koifih.client.rotation.RotationConfig;
import dev.koifih.client.rotation.Smoothing;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.EnumSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.util.Game;
import dev.koifih.client.util.Hotbar;
import dev.koifih.client.util.Lang;
import dev.koifih.client.util.Placement;
import dev.koifih.client.util.SlotSwap;
import dev.koifih.client.util.Time;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.function.Predicate;

public final class XbowCart extends Module {
    private static final String[] MODES = {"Manual", "Auto", "Silent"};
    private static final int MANUAL = 0;
    private static final int AUTO = 1;
    private static final int SILENT = 2;
    private static final RotationConfig AUTO_AIM = RotationConfig.visible(0f, Smoothing.EASE_OUT_CUBIC);
    private static final RotationConfig SILENT_AIM = RotationConfig.silent(0f, Smoothing.EASE_OUT_CUBIC);
    private static final int MAX_DROP = 2;
    private static final long TIMEOUT = 2000;
    private static final long MANUAL_TIMEOUT = 8000;
    private static final double BOTTOM = 0.5;
    private static final double AIM_LIFT = 0.25;
    private static final double AIM_INSET = 0.1;
    private static final double SHOT_RANGE = 8.0;
    private static final double ARROW_DROP = 0.1;
    private static final Predicate<ItemStack> RAIL = stack -> stack.getItem() instanceof BlockItem item
            && item.getBlock().defaultBlockState().is(BlockTags.RAILS);
    private static final Predicate<ItemStack> IGNITER = stack -> stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
    private static final Predicate<ItemStack> CROSSBOW = stack -> stack.is(Items.CROSSBOW);
    private static final Predicate<ItemStack> CHARGED = CROSSBOW.and(CrossbowItem::isCharged);

    private enum Stage { IDLE, RAIL, CART, FIRE, SHOOT }

    private final EnumSetting rotation = add(new EnumSetting("rotation", AUTO, MODES));
    private final SliderSetting delay = add(new SliderSetting("delay", 50, 50, 500, Measure.MILLIS));
    private final BoolSetting silentSwap = add(new BoolSetting("silentSwap", false));
    private final BoolSetting swapBack = add(new BoolSetting("swapBack", true));
    private final Time.Ticker pacer = new Time.Ticker();
    private final Time.Stopwatch sinceStart = new Time.Stopwatch();
    private Stage stage = Stage.IDLE;
    private Vec3i side;
    private BlockPos fire;
    private BlockPos cart;
    private final SlotSwap slots = new SlotSwap();

    public XbowCart() {
        super("xbowCart");
        swapBack.visibleWhen(() -> !silentSwap.get());
    }

    @Override
    public String info() {
        return rotation.selected();
    }

    @Override
    public boolean activatable() {
        return true;
    }

    @Override
    protected void onEnable() {
        listen(PreTickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        stop(Game.player());
    }

    @Override
    protected void onActivate() {
        LocalPlayer player = mc.player;
        if (stage != Stage.IDLE || !Game.playing(mc)) return;
        if (Hotbar.find(player, CHARGED) == Hotbar.NONE) {
            if (Hotbar.find(player, CROSSBOW) != Hotbar.NONE) {
                AdinClient.MODULES.get(Notifications.class).alert(this, Lang.get("xbowCart.uncharged"));
            }
            return;
        }
        if (!locate(player)) return;
        stage = Stage.RAIL;
        sinceStart.reset();
        run(player);
    }

    private void onTick(PreTickEvent event) {
        LocalPlayer player = event.client().player;
        if (stage == Stage.IDLE) {
            if (slots.swapped() && pacer.ready()) slots.restore(player, swapBack.get());
            return;
        }
        if (player == null || !Game.playing(mc) || sinceStart.elapsed(manual() ? MANUAL_TIMEOUT : TIMEOUT)) {
            stop(player);
            return;
        }
        run(player);
    }

    private void run(LocalPlayer player) {
        if (player.isUsingItem()) return;
        Stage before;
        do {
            before = stage;
            switch (stage) {
                case RAIL -> rail(player);
                case CART -> cart(player);
                case FIRE -> fire(player);
                case SHOOT -> shoot(player);
                default -> {}
            }
        } while (stage != before && stage != Stage.IDLE);
    }

    private boolean locate(LocalPlayer player) {
        BlockPos rail = spot(player);
        if (rail == null) return false;
        Vec3i step = Placement.sideToward(rail, player.getEyePosition());
        BlockPos flame = rail.offset(step);
        Direction direction = Direction.getApproximateNearest(-step.getX(), 0, -step.getZ());
        if (flame.equals(rail) || player.getBoundingBox().intersects(new AABB(flame)) || !fits(flame, rail, direction)) return false;
        side = step;
        fire = flame;
        cart = rail;
        return true;
    }

    private BlockPos spot(LocalPlayer player) {
        BlockHitResult hit = ManualAim.block(player, player.blockInteractionRange());
        if (hit == null) return null;
        BlockPos pos = hit.getBlockPos();
        if (mc.level.getBlockState(pos).is(BlockTags.RAILS)) return pos;
        BlockPos start = hit.getDirection() == Direction.UP ? pos.above() : pos.relative(hit.getDirection());
        for (int drop = 0; drop <= MAX_DROP; drop++) {
            BlockPos cell = start.below(drop);
            BlockState state = mc.level.getBlockState(cell);
            if (state.is(BlockTags.RAILS)) return cell;
            if (!state.canBeReplaced()) return null;
            if (sturdy(cell.below())) return cell;
        }
        return null;
    }

    private boolean fits(BlockPos flame, BlockPos rail, Direction direction) {
        BlockState railState = mc.level.getBlockState(rail);
        boolean railReady = railState.is(BlockTags.RAILS) || (railState.canBeReplaced() && sturdy(rail.below()));
        boolean fireReady = burning(flame) || (sturdy(flame.below()) && BaseFireBlock.canBePlacedAt(mc.level, flame, direction));
        return railReady && fireReady;
    }

    private void rail(LocalPlayer player) {
        if (mc.level.getBlockState(cart).is(BlockTags.RAILS)) {
            stage = Stage.CART;
            return;
        }
        int slot = Hotbar.find(player, RAIL);
        if (slot == Hotbar.NONE) {
            stop(player);
            return;
        }
        BlockHitResult hit = manual() ? ManualAim.placing(player, cart) : Placement.placeInto(mc.level, player.getEyePosition(), cart);
        if (hit == null || !Placement.placeable(player, player.getInventory().getItem(slot), hit)) return;
        act(player, hit, slot, true, Stage.CART);
    }

    private void cart(LocalPlayer player) {
        if (tntCart() != null) {
            stage = Stage.FIRE;
            return;
        }
        if (!mc.level.getBlockState(cart).is(BlockTags.RAILS)) {
            stage = Stage.RAIL;
            return;
        }
        int slot = Hotbar.find(player, stack -> stack.is(Items.TNT_MINECART));
        if (slot == Hotbar.NONE) {
            stop(player);
            return;
        }
        BlockHitResult hit = manual() ? ManualAim.clicking(player, cart) : Placement.clickOn(mc.level, player.getEyePosition(), cart);
        if (hit != null) act(player, hit, slot, false, Stage.FIRE);
    }

    private void fire(LocalPlayer player) {
        if (burning(fire)) {
            stage = Stage.SHOOT;
            return;
        }
        int slot = Hotbar.find(player, IGNITER);
        if (slot == Hotbar.NONE) {
            stop(player);
            return;
        }
        BlockPos ground = fire.below();
        BlockHitResult hit = manual() ? ManualAim.placing(player, fire)
                : new BlockHitResult(Vec3.upFromBottomCenterOf(ground, 1.0), Direction.UP, ground, false);
        if (hit != null) act(player, hit, slot, false, Stage.SHOOT);
    }

    private void shoot(LocalPlayer player) {
        int slot = Hotbar.find(player, CHARGED);
        if (slot == Hotbar.NONE) {
            stop(player);
            return;
        }
        AABB bottom = bottom();
        Vec3 point = aimPoint(bottom);
        if (!pacer.ready()) {
            aim(player, point);
            return;
        }
        if (!lined(player, bottom)) face(player, point);
        if (!lined(player, bottom) || !slots.select(player, slot, silentSwap.get())) return;
        if (slots.quiet(player, slot, silentSwap.get())) {
            ((MultiPlayerGameModeAccessor) mc.gameMode).adin$startPrediction(mc.level,
                    sequence -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence, player.getYRot(), player.getXRot()));
        } else {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
        pacer.pace(delay.get());
        stage = Stage.IDLE;
    }

    private AABB bottom() {
        MinecartTNT tnt = tntCart();
        AABB box = tnt != null ? tnt.getBoundingBox() : EntityTypes.TNT_MINECART.getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(cart));
        return new AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY + box.getYsize() * BOTTOM, box.maxZ);
    }

    private Vec3 aimPoint(AABB bottom) {
        Vec3 center = bottom.getCenter();
        return new Vec3(center.x + side.getX() * (bottom.getXsize() * 0.5 - AIM_INSET), bottom.minY + AIM_LIFT,
                center.z + side.getZ() * (bottom.getZsize() * 0.5 - AIM_INSET));
    }

    private boolean lined(LocalPlayer player, AABB bottom) {
        Vec3 look = manual() ? ManualAim.look(player) : AdinClient.ROTATIONS.rotation(player).direction();
        Vec3 start = player.getEyePosition().subtract(0, ARROW_DROP, 0);
        Vec3 end = start.add(look.scale(SHOT_RANGE));
        return bottom.clip(start, end).isPresent() && new AABB(fire).clip(start, end).isPresent();
    }

    private void act(LocalPlayer player, BlockHitResult hit, int slot, boolean placing, Stage next) {
        if (!pacer.ready()) {
            aim(player, hit.getLocation());
            return;
        }
        if (!facing(player, hit)) face(player, hit.getLocation());
        if (facing(player, hit) && use(player, hit, slot, placing)) stage = next;
    }

    private boolean facing(LocalPlayer player, BlockHitResult hit) {
        if (manual()) return true;
        Vec3 look = AdinClient.ROTATIONS.rotation(player).direction();
        return Placement.looksAt(mc.level, hit.getBlockPos(), player.getEyePosition(), look, player.blockInteractionRange());
    }

    private void aim(LocalPlayer player, Vec3 point) {
        if (manual()) return;
        AdinClient.ROTATIONS.aim(partialTick -> Rotation.toward(player.getEyePosition(partialTick), point), Priority.HIGH, config());
    }

    private void face(LocalPlayer player, Vec3 point) {
        if (!manual()) AdinClient.ROTATIONS.face(player, Rotation.toward(player.getEyePosition(), point), Priority.HIGH, config());
    }

    private RotationConfig config() {
        return rotation.get() == SILENT ? SILENT_AIM : AUTO_AIM;
    }

    private boolean manual() {
        return rotation.get() == MANUAL;
    }

    private MinecartTNT tntCart() {
        for (MinecartTNT tnt : mc.level.getEntitiesOfClass(MinecartTNT.class, new AABB(cart))) {
            if (tnt.isAlive()) return tnt;
        }
        return null;
    }

    private boolean burning(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock() instanceof BaseFireBlock;
    }

    private boolean sturdy(BlockPos pos) {
        return mc.level.getBlockState(pos).isFaceSturdy(mc.level, pos, Direction.UP);
    }

    private boolean use(LocalPlayer player, BlockHitResult hit, int slot, boolean placing) {
        if (!Placement.ready() || !slots.select(player, slot, silentSwap.get())) return false;
        Placement.use(player, hit, slot, slots.quiet(player, slot, silentSwap.get()), placing);
        pacer.pace(delay.get());
        return true;
    }

    private void stop(LocalPlayer player) {
        stage = Stage.IDLE;
        slots.restore(player, swapBack.get());
    }
}
