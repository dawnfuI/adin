package dev.koifih.client.module;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.InputConstants;
import dev.koifih.Adin;
import dev.koifih.client.config.State;
import dev.koifih.client.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);
    private final Map<String, Module> byId = new HashMap<>();
    private final Map<Class<? extends Module>, Module> byType = new HashMap<>();

    private static final String IMPL = "dev/koifih/client/module/impl";
    private static final List<String> ORDER = List.of(
            "AimAssist", "Triggerbot", "ShieldBreaker", "AutoHitCrystal", "AutoCrystal", "AutoAnchor", "AutoCart", "XbowCart", "Backtrack",
            "Sprint", "MoveFix", "JumpReset",
            "Esp", "Nametags", "BlockEsp", "Capes", "WorldModifier",
            "Notifications", "ModuleList", "Watermark", "KeybindList",
            "KeyPearl", "AutoTotem", "Refill",
            "Friends", "AntiBot");

    public ModuleManager() {
        for (Category category : Category.values()) byCategory.put(category, new ArrayList<>());
        List<Class<? extends Module>> types = discover();
        types.sort(Comparator.comparingInt(ModuleManager::rank).thenComparing(Class::getSimpleName));
        for (Class<? extends Module> type : types) register(type);
    }

    private static int rank(Class<? extends Module> type) {
        int index = ORDER.indexOf(type.getSimpleName());
        return index < 0 ? ORDER.size() : index;
    }

    static List<Class<? extends Module>> discover() {
        ModContainer container = FabricLoader.getInstance().getModContainer(Adin.MOD_ID)
                .orElseThrow(() -> new IllegalStateException("Missing mod container: " + Adin.MOD_ID));
        List<Class<? extends Module>> types = new ArrayList<>();
        for (Path root : container.getRootPaths()) collect(root, types);
        if (types.isEmpty()) throw new IllegalStateException("No modules found under " + IMPL);
        return types;
    }

    static void collect(Path root, List<Class<? extends Module>> types) {
        Path base = root.resolve(IMPL);
        if (!Files.isDirectory(base)) return;
        try (Stream<Path> stream = Files.walk(base)) {
            for (Path path : stream.toList()) {
                String name = className(root, path);
                if (name == null) continue;
                Class<?> type = Class.forName(name, false, ModuleManager.class.getClassLoader());
                if (!Module.class.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())) continue;
                types.add(type.asSubclass(Module.class));
            }
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot scan modules in " + root, exception);
        }
    }

    static String className(Path root, Path path) {
        String file = path.getFileName().toString();
        if (!file.endsWith(".class") || file.indexOf('$') >= 0) return null;
        String relative = root.relativize(path).toString();
        return relative.substring(0, relative.length() - ".class".length())
                .replace(root.getFileSystem().getSeparator(), ".");
    }

    static Category categoryOf(Class<?> type) {
        String prefix = IMPL.replace('/', '.') + ".";
        String rest = type.getPackageName().substring(prefix.length());
        int dot = rest.indexOf('.');
        String name = dot < 0 ? rest : rest.substring(0, dot);
        return Category.valueOf(name.toUpperCase(Locale.ROOT));
    }

    private void register(Class<? extends Module> type) {
        Module module;
        try {
            module = type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create module " + type.getName(), exception);
        }
        if (byId.containsKey(module.id())) throw new IllegalArgumentException("Duplicate module id: " + module.id());
        module.category = categoryOf(type);
        modules.add(module);
        byCategory.get(module.category).add(module);
        byId.put(module.id(), module);
        byType.put(type, module);
    }

    public List<Module> all() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> in(Category category) {
        return Collections.unmodifiableList(byCategory.get(category));
    }

    public Optional<Module> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Module get(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown module: " + id));
    }

    public <M extends Module> M get(Class<M> type) {
        Module module = byType.get(type);
        if (module == null) throw new IllegalArgumentException("Unregistered module: " + type.getSimpleName());
        return type.cast(module);
    }

    public boolean isEnabled(Class<? extends Module> type) {
        Module module = byType.get(type);
        return module != null && module.isEnabled();
    }

    public void tickKeybinds(Minecraft client) {
        for (Module module : modules) module.tickKeybind(client);
    }

    public boolean activates(InputConstants.Key key) {
        for (Module module : modules) {
            if (module.activatable() && module.isEnabled() && module.key().equals(key)) return true;
        }
        return false;
    }

    public Map<String, State.ModuleState> snapshot() {
        Map<String, State.ModuleState> states = new LinkedHashMap<>();
        for (Module module : modules) {
            State.ModuleState state = new State.ModuleState();
            state.enabled = module.isEnabled();
            state.key = module.key().getName();
            state.hold = module.hold();
            state.settings = new LinkedHashMap<>();
            for (Setting<?> setting : module.settings()) state.settings.put(setting.id(), setting.save());
            states.put(module.id(), state);
        }
        return states;
    }

    public void apply(Map<String, State.ModuleState> states) {
        for (Module module : modules) {
            State.ModuleState state = states.get(module.id());
            if (state == null) continue;
            module.setHold(state.hold);
            module.setKey(parseKey(state.key));
            if (state.settings != null) {
                for (Setting<?> setting : module.settings()) {
                    JsonElement json = state.settings.get(setting.id());
                    if (json != null) setting.load(json);
                }
            }
            module.setEnabled(state.enabled);
        }
    }

    private static InputConstants.Key parseKey(String name) {
        if (name == null) return InputConstants.UNKNOWN;
        try {
            return InputConstants.getKey(name);
        } catch (IllegalArgumentException exception) {
            return InputConstants.UNKNOWN;
        }
    }
}
