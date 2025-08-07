package io.github.mcengine.extension.addon.translator.translate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Expiring LRU cache mapping (message, targetLang) -> translated string.
 */
public class TranslationCache {

    /** Hash map load factor used for initial capacity calculation. */
    private static final float LOAD_FACTOR = 0.75f;
    /** Maximum number of entries to retain (LRU). */
    private final long maxEntries;
    /** Time-to-live in seconds for cached entries. */
    private final long ttlSeconds;
    /** Access-ordered map to track LRU eviction. */
    private final Map<Key, Entry> map;

    public TranslationCache(long maxEntries, long ttlSeconds) {
        this.maxEntries = Math.max(100, maxEntries);
        this.ttlSeconds = Math.max(10, ttlSeconds);
        this.map = new LinkedHashMap<>((int) (this.maxEntries / LOAD_FACTOR) + 1, LOAD_FACTOR, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Key, Entry> eldest) {
                return size() > TranslationCache.this.maxEntries;
            }
        };
    }

    /** Put a translated value into cache. */
    public synchronized void put(String message, String lang, String translated) {
        map.put(new Key(message, lang), new Entry(translated, Instant.now().getEpochSecond()));
    }

    /** Get a translated value if present and not expired. */
    public synchronized String get(String message, String lang) {
        Key k = new Key(message, lang);
        Entry e = map.get(k);
        if (e == null) return null;
        long now = Instant.now().getEpochSecond();
        if (now - e.timestamp > ttlSeconds) {
            map.remove(k);
            return null;
        }
        return e.value;
    }

    private record Key(String message, String lang) {
        public Key { message = message == null ? "" : message; lang = lang == null ? "" : lang.toLowerCase(); }
        @Override public boolean equals(Object o) { return o instanceof Key k && message.equals(k.message) && lang.equals(k.lang); }
        @Override public int hashCode() { return Objects.hash(message, lang); }
    }

    private static class Entry {
        /** Cached translated value. */
        final String value;
        /** First-seen timestamp (epoch seconds). */
        final long timestamp;
        Entry(String v, long t){ value=v; timestamp=t; }
    }
}
