package dev.koifih.client.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigStore {
    private static final Set<String> RESERVED = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");
    private static final int MAX_ID_LENGTH = 48;

    public static Path directory() {
        return Storage.directory().resolve("configs");
    }

    private static Path path(String id) {
        return directory().resolve(id + ".json");
    }

    public static List<Config> list() {
        List<Config> configs = new ArrayList<>();
        for (Path file : Storage.files(directory())) {
            Config config = Storage.read(file, Config.class);
            if (config != null && normalize(config, Storage.nameOf(file))) configs.add(config);
        }
        configs.sort(Comparator.comparingLong((Config config) -> config.created).reversed());
        return configs;
    }

    private static boolean normalize(Config config, String id) {
        if (config.name == null || config.name.isBlank()) return false;
        config.id = id;
        if (config.description == null) config.description = "";
        if (config.author == null) config.author = "";
        if (config.scope == null) config.scope = Config.Scope.BOTH;
        if (config.visibility == null) config.visibility = Config.Visibility.PRIVATE;
        if (config.version <= 0) config.version = State.VERSION;
        return true;
    }

    public static boolean save(Config config) {
        return Storage.write(path(config.id), config);
    }

    public static boolean delete(Config config) {
        return Storage.delete(path(config.id));
    }

    public static Config create(String name, String description, Config.Scope scope, Config.Visibility visibility) {
        Config config = new Config();
        config.name = name.trim();
        config.description = description.trim();
        config.scope = scope;
        config.visibility = visibility;
        config.author = Minecraft.getInstance().getUser().getName();
        config.created = System.currentTimeMillis();
        config.id = uniqueId(config.name);
        Snapshot.capture(config, scope != Config.Scope.SETTINGS, scope != Config.Scope.COLORS);
        save(config);
        return config;
    }

    public static Config importPublic(Config config) {
        if (config == null || !normalize(config, config.id == null ? "" : config.id)) return null;
        config.id = uniqueId(slug(config.id.isBlank() ? config.name : config.id));
        config.visibility = Config.Visibility.PRIVATE;
        return save(config) ? config : null;
    }

    public static String export(Config config) {
        return Storage.json(config);
    }

    public static void apply(Config config) {
        Snapshot.apply(config, config.hasColors(), config.hasSettings());
        StateStore.save();
    }

    private static String uniqueId(String name) {
        String base = slug(name);
        if (!Files.exists(path(base))) return base;
        for (int suffix = 2; suffix <= 999; suffix++) {
            String candidate = base + "-" + suffix;
            if (!Files.exists(path(candidate))) return candidate;
        }
        return base + "-" + System.currentTimeMillis();
    }

    private static String slug(String name) {
        String slug = name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_ID_LENGTH) slug = slug.substring(0, MAX_ID_LENGTH).replaceAll("-+$", "");
        if (slug.isEmpty()) return "config";
        return RESERVED.contains(slug) ? slug + "-config" : slug;
    }
}
