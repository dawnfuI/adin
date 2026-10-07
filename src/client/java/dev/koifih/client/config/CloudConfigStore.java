package dev.koifih.client.config;

import dev.koifih.Adin;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Read-only public config catalogue hosted in the Adin showcase repository. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CloudConfigStore {
    private static final String BASE_URL = "https://raw.githubusercontent.com/isucksunstoes69-dotcom/showcase/main/configs/";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static volatile List<Entry> catalogue = List.of();

    public static List<Entry> list() {
        return catalogue;
    }

    public static CompletableFuture<List<Entry>> refresh() {
        return get("index.json", Index.class).thenApply(index -> {
            catalogue = index == null || index.configs == null ? List.of() : List.copyOf(index.configs);
            return catalogue;
        });
    }

    public static CompletableFuture<Config> importConfig(Entry entry) {
        if (entry == null || !entry.valid()) return CompletableFuture.completedFuture(null);
        return get(entry.path(), Config.class).thenApply(ConfigStore::importPublic);
    }

    private static <T> CompletableFuture<T> get(String path, Class<T> type) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        Adin.LOGGER.warn("Cloud config request failed with HTTP {}", response.statusCode());
                        return null;
                    }
                    return Storage.read(response.body(), type);
                })
                .exceptionally(exception -> {
                    Adin.LOGGER.warn("Cannot fetch cloud configs", exception);
                    return null;
                });
    }

    public static final class Index {
        public List<Entry> configs = new ArrayList<>();
    }

    public static final class Entry {
        public String id = "";
        public String name = "";
        public String description = "";
        public String author = "";
        public Config.Scope scope = Config.Scope.BOTH;
        public long created;
        public String file = "";

        public String path() {
            return file == null || file.isBlank() ? id + ".json" : file;
        }

        private boolean valid() {
            return id != null && id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                    && path().matches("[a-z0-9][a-z0-9._/-]*\\.json");
        }
    }
}
