package io.github.mcengine.extension.addon.translator.player;

import io.github.mcengine.extension.addon.translator.util.ConfigUtil;
import io.github.mcengine.extension.addon.translator.player.orm.LangPreferenceRepository;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Stores per-player language preferences (UUID -> ISO 639-1 code).
 * <p>
 * Backends:
 * <ul>
 *   <li>{@code yaml} — persists via custom-path config handled by {@link ConfigUtil}</li>
 *   <li>{@code sqlite}/{@code mysql} — persists via {@link LangPreferenceRepository} (ORM/JDBC)</li>
 * </ul>
 */
public class PlayerLangStore {

    /** Storage backends supported for persistence. */
    public enum Backend { YAML, DB }

    /** Owning plugin instance. */
    private final Plugin plugin;

    /** Utility for accessing/saving the custom-path config. */
    private final ConfigUtil configUtil;

    /** Active storage backend. */
    private final Backend backend;

    /** Optional ORM repository when {@link Backend#DB} is in use. */
    private final LangPreferenceRepository repository;

    /** In-memory mapping of player UUID to language code. */
    private final Map<UUID, String> map = new ConcurrentHashMap<>();

    /**
     * Creates a YAML-backed store (backward compatibility).
     *
     * @param plugin     Bukkit plugin
     * @param configUtil Utility providing custom-path config access
     */
    public PlayerLangStore(Plugin plugin, ConfigUtil configUtil) {
        this(plugin, configUtil, Backend.YAML, null);
    }

    /**
     * Creates a store with the specified backend.
     *
     * @param plugin     Bukkit plugin
     * @param configUtil Utility providing custom-path config access
     * @param backend    storage backend to use
     * @param repository ORM repository when backend is {@link Backend#DB}; otherwise {@code null}
     */
    public PlayerLangStore(Plugin plugin, ConfigUtil configUtil, Backend backend, LangPreferenceRepository repository) {
        this.plugin = plugin;
        this.configUtil = configUtil;
        this.backend = backend == null ? Backend.YAML : backend;
        this.repository = repository;
        load();
    }

    /** Sets language for a player; null clears. */
    public void set(UUID uuid, String lang) {
        if (lang == null) {
            map.remove(uuid);
            if (backend == Backend.DB && repository != null) repository.delete(uuid);
        } else {
            String normalized = lang.toLowerCase(Locale.ROOT);
            map.put(uuid, normalized);
            if (backend == Backend.DB && repository != null) repository.upsert(uuid, normalized);
        }
        if (backend == Backend.YAML) save();
    }

    /** Gets language for a player or null. */
    public String get(UUID uuid) { return map.get(uuid); }

    /** Clears language for a player. */
    public void clear(UUID uuid) {
        map.remove(uuid);
        if (backend == Backend.DB && repository != null) repository.delete(uuid);
        if (backend == Backend.YAML) save();
    }

    /** Snapshot copy. */
    public Map<UUID, String> snapshot() { return new HashMap<>(map); }

    private void load() {
        if (backend == Backend.DB && repository != null) {
            map.clear();
            map.putAll(repository.findAll());
            return;
        }
        // YAML backend
        FileConfiguration cfg = configUtil.getConfig();
        if (cfg != null && cfg.isConfigurationSection("players")) {
            for (String k : cfg.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(k);
                    String lang = cfg.getString("players." + k);
                    if (lang != null) map.put(id, lang.toLowerCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    /** Persists player map to the same custom config file (YAML backend only). */
    public void save() {
        if (backend == Backend.DB) return;
        FileConfiguration cfg = configUtil.getConfig();
        if (cfg == null) return;
        Map<String, String> toSave = map.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
        cfg.set("players", toSave);
        configUtil.save();
    }
}
