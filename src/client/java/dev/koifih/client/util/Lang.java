package dev.koifih.client.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Lang {
    public enum Language {
        ENGLISH("en", "English"),
        RUSSIAN("ru", "Russian"),
        POLISH("pl", "Polish"),
        FRENCH("fr", "French"),
        CROATIAN("hr", "Croatian");

        public static final Language[] ALL = values();
        private final String code;
        private final String displayName;

        Language(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public static final class Localized {
        private final Supplier<String> resolve;
        private Language language;
        private String value;

        public Localized(Supplier<String> resolve) {
            this.resolve = resolve;
        }

        public void invalidate() {
            language = null;
        }

        public String get() {
            if (language != current) {
                value = resolve.get();
                language = current;
            }
            return value;
        }
    }

    private static final Map<Language, Map<String, String>> TRANSLATIONS = new EnumMap<>(Language.class);
    private static Language current = Language.ENGLISH;

    public static Language current() {
        return current;
    }

    public static void set(Language language) {
        current = language;
    }

    public static String getOrDefault(String key, String fallback) {
        String value = translations(current).get(key);
        if (value == null) value = translations(Language.ENGLISH).get(key);
        return value == null ? fallback : value;
    }

    public static String capitalize(String id) {
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    public static String get(String key) {
        return getOrDefault(key, key);
    }

    private static Map<String, String> translations(Language language) {
        return TRANSLATIONS.computeIfAbsent(language, Lang::load);
    }

    private static Map<String, String> load(Language language) {
        String path = "/assets/adin/translations/" + language.code + ".json";
        try (var stream = Lang.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing translations: " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load translations: " + path, exception);
        }
    }
}
