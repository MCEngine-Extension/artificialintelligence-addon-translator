package io.github.mcengine.extension.addon.translator.translate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Expiring LRU cache mapping (message, targetLang) -> translated string.
 * <p>
 * NOTE: Avoids nested classes to prevent {@code NoClassDefFoundError} for
 * {@code TranslationCache$Key} in shaded/minimized builds. Uses a composite
 * {@link String} key instead.
 * </p>
 */
public class TranslationCache {

    /** Hash map load factor used for initial capacity calculation. */
    private static final float LOAD_FACTOR = 0.75f;

    /** Maximum number of entries to retain (LRU). */
    private final long maxEntries;

    /** Time-to-live in seconds for cached entries. */
    private final long ttlSeconds;

    /** Access-ordered map to track LRU eviction (composite String key). */
    private final Map<String, Entry> map;

    public TranslationCache(long maxEntries, long ttlSeconds) {
        this.maxEntries = Math.max(100, maxEntries);
        this.ttlSeconds = Math.max(10, ttlSeconds);
        this.map = new LinkedHashMap<>(
                (int) (this.maxEntries / LOAD_FACTOR) + 1, LOAD_FACTOR, true
        ) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > TranslationCache.this.maxEntries;
            }
        };
    }

    /** Put a translated value into cache. */
    public synchronized void put(String message, String lang, String translated) {
        map.put(keyOf(message, lang), new Entry(translated, Instant.now().getEpochSecond()));
    }

    /** Get a translated value if present and not expired. */
    public synchronized String get(String message, String lang) {
        final String k = keyOf(message, lang);
        final Entry e = map.get(k);
        if (e == null) return null;

        final long now = Instant.now().getEpochSecond();
        if (now - e.timestamp > ttlSeconds) {
            map.remove(k);
            return null;
        }
        return e.value;
    }

    /** Builds a stable composite key for (message, lang). */
    private static String keyOf(String message, String lang) {
        // Use an unlikely separator to avoid collisions.
        final char SEP = '\u0001';
        final String m = message == null ? "" : message;
        final String l = lang == null ? "" : lang.toLowerCase();
        return m + SEP + l;
    }

    /** Single cached entry with a creation timestamp (epoch seconds). */
    private static final class Entry {
        /** Cached translated value. */
        final String value;
        /** First-seen timestamp (epoch seconds). */
        final long timestamp;

        Entry(String v, long t) {
            this.value = v;
            this.timestamp = t;
        }
    }
}
