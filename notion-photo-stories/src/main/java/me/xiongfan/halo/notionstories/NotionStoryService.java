package me.xiongfan.halo.notionstories;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class NotionStoryService {

    private final NotionStorySettings settings;
    private final NotionClient notionClient;
    private final ConcurrentHashMap<String, CacheEntry<NotionStory>> detailCache =
        new ConcurrentHashMap<>();
    private volatile CacheEntry<List<NotionStory>> listCache;

    public NotionStoryService(NotionStorySettings settings, NotionClient notionClient) {
        this.settings = settings;
        this.notionClient = notionClient;
    }

    public Mono<NotionStoryListPage> listPage() {
        return settings.get().flatMap(properties -> {
            if (!properties.hasRequiredConfig()) {
                return Mono.just(NotionStoryListPage.configMissing(properties));
            }
            return cachedList(properties)
                .map(stories -> NotionStoryListPage.configured(properties, stories));
        });
    }

    public Mono<NotionStoryDetailPage> detailPage(String slug) {
        return settings.get().flatMap(properties -> {
            if (!properties.hasRequiredConfig()) {
                return Mono.error(new NotionConfigMissingException());
            }
            return cachedDetail(properties, slug)
                .map(story -> new NotionStoryDetailPage(properties, story));
        });
    }

    private Mono<List<NotionStory>> cachedList(NotionStoryProperties properties) {
        var now = Instant.now();
        var key = properties.cacheFingerprint();
        var current = listCache;
        if (current != null && current.isValid(key, now)) {
            return Mono.just(current.value());
        }
        return notionClient.listStories(properties)
            .doOnNext(stories -> listCache = CacheEntry.of(key, stories, properties, now));
    }

    private Mono<NotionStory> cachedDetail(NotionStoryProperties properties, String slug) {
        var now = Instant.now();
        var key = properties.cacheFingerprint() + ":" + slug;
        var current = detailCache.get(key);
        if (current != null && current.isValid(key, now)) {
            return Mono.just(current.value());
        }
        return notionClient.getStoryBySlug(properties, slug)
            .doOnNext(story -> detailCache.put(key, CacheEntry.of(key, story, properties, now)));
    }

    private record CacheEntry<T>(String key, T value, Instant expiresAt) {

        static <T> CacheEntry<T> of(String key, T value, NotionStoryProperties properties,
            Instant now) {
            var ttl = Duration.ofMinutes(properties.getCacheTtlMinutes());
            return new CacheEntry<>(key, value, now.plus(ttl));
        }

        boolean isValid(String requestedKey, Instant now) {
            return key.equals(requestedKey) && expiresAt.isAfter(now);
        }
    }
}
