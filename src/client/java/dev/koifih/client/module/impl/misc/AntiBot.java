package dev.koifih.client.module.impl.misc;

import dev.koifih.client.AdinClient;
import dev.koifih.client.event.events.AttackEvent;
import dev.koifih.client.event.events.TickEvent;
import dev.koifih.client.module.Module;
import dev.koifih.client.setting.BoolSetting;
import dev.koifih.client.setting.EnumSetting;
import dev.koifih.client.setting.Measure;
import dev.koifih.client.setting.MultiSetting;
import dev.koifih.client.setting.SliderSetting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AntiBot extends Module {
    private static final String[] CHECKS = {"Tab list", "Offline UUID", "Duplicate name", "Entity ID", "Pitch", "Motionless", "God"};
    private static final int TAB_LIST = 0;
    private static final int OFFLINE_UUID = 1;
    private static final int DUPLICATE_NAME = 2;
    private static final int ENTITY_ID = 3;
    private static final int PITCH = 4;
    private static final int MOTIONLESS = 5;
    private static final int GOD = 6;

    private static final String[] PRESET_NAMES = {"CubeCraft", "Strict"};
    private static final List<Set<Integer>> PRESETS = List.of(
            Set.of(TAB_LIST, OFFLINE_UUID, DUPLICATE_NAME, ENTITY_ID),
            Set.of(TAB_LIST, OFFLINE_UUID, DUPLICATE_NAME, ENTITY_ID, PITCH, MOTIONLESS, GOD));

    private static final int ONLINE_UUID_VERSION = 4;
    private static final int MAX_ENTITY_ID = 1_000_000_000;
    private static final float MAX_PITCH = 90.0f;
    private static final double MOVE_EPSILON = 0.01;

    private final EnumSetting preset = add(new EnumSetting("preset", 0, PRESET_NAMES));
    private final BoolSetting advanced = add(new BoolSetting("advanced", false));
    private final MultiSetting checks = add(new MultiSetting("checks", CHECKS, TAB_LIST, OFFLINE_UUID, DUPLICATE_NAME, ENTITY_ID));
    private final SliderSetting grace = add(new SliderSetting("grace", 60, 20, 400, Measure.TICKS));

    private final Map<Integer, Track> tracked = new HashMap<>();
    private final Set<Integer> bots = new HashSet<>();

    public AntiBot() {
        super("antiBot");
        preset.visibleWhen(() -> !advanced.get());
        checks.visibleWhen(advanced::get);
        grace.visibleWhen(() -> uses(MOTIONLESS) || uses(GOD));
    }

    public static boolean flags(Entity entity) {
        if (!(entity instanceof Player) || !AdinClient.MODULES.isEnabled(AntiBot.class)) return false;
        return AdinClient.MODULES.get(AntiBot.class).bots.contains(entity.getId());
    }

    @Override
    public String info() {
        String label = advanced.get() ? "Advanced" : preset.selected();
        return bots.isEmpty() ? label : label + " " + bots.size();
    }

    @Override
    protected void onEnable() {
        listen(TickEvent.class, this::onTick);
        listen(AttackEvent.class, this::onAttack);
    }

    @Override
    protected void onDisable() {
        forget();
    }

    private boolean uses(int check) {
        return advanced.get() ? checks.has(check) : PRESETS.get(preset.get()).contains(check);
    }

    private void onAttack(AttackEvent event) {
        if (event.target() instanceof Player target) tracked.computeIfAbsent(target.getId(), id -> new Track()).attacked = true;
    }

    private void onTick(TickEvent event) {
        ClientLevel level = mc.level;
        LocalPlayer self = mc.player;
        if (level == null || self == null) {
            forget();
            return;
        }
        List<AbstractClientPlayer> players = new ArrayList<>(level.players());
        Map<String, Integer> names = new HashMap<>();
        Set<Integer> present = new HashSet<>();
        for (AbstractClientPlayer player : players) {
            present.add(player.getId());
            tracked.computeIfAbsent(player.getId(), id -> new Track()).update(player);
            names.merge(name(player), 1, Integer::sum);
        }
        tracked.keySet().retainAll(present);
        bots.clear();
        for (AbstractClientPlayer player : players) {
            if (player != self && detect(player, names)) bots.add(player.getId());
        }
    }

    private boolean detect(Player player, Map<String, Integer> names) {
        int id = player.getId();
        if (uses(ENTITY_ID) && (id < 0 || id > MAX_ENTITY_ID)) return true;
        if (uses(PITCH) && Math.abs(player.getXRot()) > MAX_PITCH) return true;
        if (uses(OFFLINE_UUID) && player.getUUID().version() != ONLINE_UUID_VERSION) return true;
        if (uses(TAB_LIST) && !listed(player.getUUID())) return true;
        if (uses(DUPLICATE_NAME) && names.getOrDefault(name(player), 0) > 1) return true;
        Track track = tracked.get(id);
        if (track == null || player.tickCount < grace.get()) return false;
        if (uses(MOTIONLESS) && !track.moved) return true;
        return uses(GOD) && track.attacked && !track.hurt;
    }

    private void forget() {
        tracked.clear();
        bots.clear();
    }

    private static boolean listed(UUID id) {
        ClientPacketListener connection = mc.getConnection();
        return connection == null || connection.getPlayerInfo(id) != null;
    }

    private static String name(Player player) {
        return player.getGameProfile().name();
    }

    private static final class Track {
        private Vec3 spawn;
        private float health = Float.NaN;
        private boolean moved;
        private boolean attacked;
        private boolean hurt;

        private void update(Player player) {
            if (spawn == null) spawn = player.position();
            if (!moved && player.position().distanceToSqr(spawn) > MOVE_EPSILON) moved = true;
            if (!hurt && (player.hurtTime > 0 || player.getHealth() < health)) hurt = true;
            health = player.getHealth();
        }
    }
}
