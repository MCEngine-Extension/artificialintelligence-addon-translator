package io.github.mcengine.extension.addon.translator.player.orm;

import java.util.Map;
import java.util.UUID;

/**
 * Repository (ORM-like) for player language preferences.
 * Abstracts the underlying SQL dialect and connection.
 */
public interface LangPreferenceRepository {

    /**
     * Inserts or updates a player's language preference.
     *
     * @param uuid player UUID
     * @param lang ISO 639-1 code (lowercase)
     */
    void upsert(UUID uuid, String lang);

    /**
     * Fetches a player's language.
     *
     * @param uuid player UUID
     * @return ISO 639-1 code or {@code null}
     */
    String find(UUID uuid);

    /**
     * Loads all preferences.
     *
     * @return map of UUID -> language code
     */
    Map<UUID, String> findAll();

    /**
     * Removes a player's preference if present.
     *
     * @param uuid player UUID
     */
    void delete(UUID uuid);
}
