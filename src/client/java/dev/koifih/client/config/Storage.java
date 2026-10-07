package dev.koifih.client.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.koifih.Adin;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Storage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SUFFIX = ".json";

    public static Path directory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(Adin.MOD_ID);
    }

    public static Path file(String name) {
        return directory().resolve(name + SUFFIX);
    }

    public static List<Path> files(Path directory) {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> stream = Files.list(directory)) {
            return new ArrayList<>(stream.filter(path -> path.toString().endsWith(SUFFIX)).sorted().toList());
        } catch (IOException exception) {
            Adin.LOGGER.warn("Cannot list {}", directory, exception);
            return List.of();
        }
    }

    public static String nameOf(Path path) {
        String file = path.getFileName().toString();
        return file.endsWith(SUFFIX) ? file.substring(0, file.length() - SUFFIX.length()) : file;
    }

    public static <T> T read(Path path, Class<T> type) {
        if (!Files.isRegularFile(path)) return null;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (IOException | RuntimeException exception) {
            Adin.LOGGER.warn("Cannot read {}", path, exception);
            return null;
        }
    }

    public static <T> T read(String json, Class<T> type) {
        try {
            return GSON.fromJson(json, type);
        } catch (RuntimeException exception) {
            Adin.LOGGER.warn("Cannot read config JSON", exception);
            return null;
        }
    }

    public static String json(Object value) {
        return GSON.toJson(value);
    }

    public static boolean write(Path path, Object value) {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
            move(temporary, path);
            return true;
        } catch (IOException | RuntimeException exception) {
            Adin.LOGGER.warn("Cannot write {}", path, exception);
            deleteQuietly(temporary);
            return false;
        }
    }

    private static void move(Path temporary, Path path) throws IOException {
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static boolean delete(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException exception) {
            Adin.LOGGER.warn("Cannot delete {}", path, exception);
            return false;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
