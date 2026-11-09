package dev.koifih.client.module.impl.movement;

import dev.koifih.client.AdinClient;
import dev.koifih.client.event.Priority;
import dev.koifih.client.event.events.PreTickEvent;
import dev.koifih.client.module.Module;
import dev.koifih.client.rotation.Rotation;
import dev.koifih.client.rotation.RotationMode;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.EnumSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.SliderSetting;
import dev.koifih.client.util.Clicks;
import dev.koifih.client.util.Game;
import dev.koifih.client.util.Hotbar;
import dev.koifih.client.util.ProjectilePrediction;
import dev.koifih.client.util.SlotSwap;
import net.minecraft.SharedConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class PearlCatch extends Module {
    private static final String[] MODES = {"Auto", "Silent"};
    private static final int SILENT = 1;
    private static final int MAX_WAIT_TICKS = 20;

    private final EnumSetting rotation = add(new EnumSetting("rotation", SILENT, MODES));
    private final SliderSetting delay = add(new SliderSetting("delay", 100, 50, 500, Measure.MILLIS));
    private final BoolSetting silentSwap = add(new BoolSetting("silentSwap", false));
    private final BoolSetting swapBack = add(new BoolSetting("swapBack", true));
    private final SlotSwap slots = new SlotSwap();
    private Vec3 spawn;
    private ProjectilePrediction.Launch launch;
    private int thrownAt;

    public PearlCatch() {
        super("pearlCatch");
        swapBack.visibleWhen(() -> !silentSwap.get());
    }

    @Override
    public boolean activatable() {
        return true;
    }

    @Override
    protected void onEnable() {
        listen(PreTickEvent.class, event -> onTick());
    }

    @Override
    protected void onDisable() {
        spawn = null;
        slots.restore(mc.player, swapBack.get());
    }

    @Override
    protected void onActivate() {
        LocalPlayer player = mc.player;
        if (spawn != null || !Game.playing() || player.isUsingItem() || ready(player, Items.WIND_CHARGE) == Hotbar.NONE) return;
        if (!use(player, ready(player, Items.ENDER_PEARL))) return;
        spawn = ProjectilePrediction.throwOrigin(player);
        launch = null;
        thrownAt = player.tickCount;
    }

    private void onTick() {
        LocalPlayer player = mc.player;
        if (spawn == null || player == null) {
            spawn = null;
            slots.restore(player, swapBack.get());
            return;
        }
        int age = player.tickCount - thrownAt;
        if (!Game.playing() || age > MAX_WAIT_TICKS) {
            spawn = null;
            return;
        }
        if (launch == null) launch = launch(player, age);
        if (launch == null || age * SharedConstants.MILLIS_PER_TICK < delay.get()) return;
        ProjectilePrediction.Intercept target = ProjectilePrediction.windCharge(player, launch, age);
        if (target == null) return;
        spawn = null;
        AdinClient.ROTATIONS.face(player, Rotation.toward(Vec3.ZERO, target.direction()), Priority.HIGH,
                RotationMode.config(rotation.get() == SILENT ? RotationMode.SILENT : RotationMode.AUTO));
        use(player, ready(player, Items.WIND_CHARGE));
    }

    private ProjectilePrediction.Launch launch(LocalPlayer player, int age) {
        double reach = (age + 1) * EnderpearlItem.PROJECTILE_SHOOT_POWER;
        for (ThrownEnderpearl pearl : mc.level.getEntitiesOfClass(ThrownEnderpearl.class, player.getBoundingBox().inflate(reach))) {
            ProjectilePrediction.Launch launch = pearl.getOwner() == player ? ProjectilePrediction.launch(pearl, spawn) : null;
            if (launch != null) return launch;
        }
        return null;
    }

    private boolean use(LocalPlayer player, int slot) {
        if (slot == Hotbar.NONE || !slots.select(player, slot, silentSwap.get())) return false;
        Clicks.use(mc, slots.quiet(player, slot, silentSwap.get()));
        return true;
    }

    private static int ready(LocalPlayer player, Item item) {
        return Hotbar.find(player, stack -> stack.is(item) && !player.getCooldowns().isOnCooldown(stack));
    }
}
