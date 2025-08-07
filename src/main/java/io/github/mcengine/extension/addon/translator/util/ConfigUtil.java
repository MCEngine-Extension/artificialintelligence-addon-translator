package io.github.mcengine.extension.addon.translator.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * Utilities for creating, loading, saving, and reloading the Translator configuration
 * at a custom location under the plugin's data folder.
 * <p>
 * Location (relative to the plugin data folder):
 * <pre>
 *   extensions/addons/configs/MCEngineTranslator/config.yml
 * </pre>
 * <p>
 * Behavior:
 * <ul>
 *   <li>If the directory does not exist, it will be created.</li>
 *   <li>If the {@code config.yml} does not exist, a minimal default config will be generated once.</li>
 *   <li>Existing files are never overwritten.</li>
 * </ul>
 */
public final class ConfigUtil {

    /** The relative path where the Translator config is stored. */
    private final String folderPath = "extensions/addons/configs/MCEngineTranslator";

    /** The owning Bukkit plugin instance. */
    private final Plugin plugin;

    /** The resolved config file on disk: {@code <dataFolder>/<folderPath>/config.yml}. */
    private final File configFile;

    /** In-memory configuration handle; populated by {@link #initAndLoad()}. */
    private FileConfiguration config;

    /**
     * Creates a ConfigUtil bound to the plugin's data folder at the custom path.
     *
     * @param plugin Bukkit plugin instance.
     */
    public ConfigUtil(Plugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), folderPath + "/config.yml");
    }

    /**
     * Ensures the config directory exists and creates a new config file
     * only if it does not already exist. Never overwrites an existing file.
     */
    private void createIfMissing() {
        final File configDir = configFile.getParentFile();
        if (!configDir.exists() && !configDir.mkdirs()) {
            System.err.println("[Translator] Failed to create config directory: " + configDir.getAbsolutePath());
            return;
        }

        if (configFile.exists()) {
            // Do not overwrite existing config
            return;
        }

        // Create a minimal default configuration (example-style: write values directly)
        final YamlConfiguration def = new YamlConfiguration();

        // === Translator Defaults ===
        def.set("storage.backend", "sqlite"); // yaml | sqlite | mysql

        // SQLite defaults (db file stored under plugin data folder)
        def.set("storage.sqlite.file", "translator.db");

        // MySQL defaults
        def.set("storage.mysql.host", "127.0.0.1");
        def.set("storage.mysql.port", 3306);
        def.set("storage.mysql.database", "mcengine");
        def.set("storage.mysql.username", "root");
        def.set("storage.mysql.password", "password");
        def.set("storage.mysql.params", "useSSL=false&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true");

        // AI Defaults for Translator AddOn
        def.set("ai.platform", "deepseek");
        def.set("ai.model", "deepseek-chat");
        def.set("ai.tokenType", "server");
        def.set("ai.systemPrompt",
                "You are a precise, safe translator. Detect the source language and translate the user's " +
                "message to the requested target language code (ISO 639-1). Output only the translated text.");

        def.set("cache.maxEntries", 1000);
        def.set("cache.ttlSeconds", 300);

        try {
            def.save(configFile);
            System.out.println("[Translator] Created default translator config: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[Translator] Failed to save default translator config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initializes configuration: creates directory and default file if missing (once),
     * then loads the YAML into memory.
     *
     * @return loaded {@link FileConfiguration}.
     */
    public FileConfiguration initAndLoad() {
        try {
            createIfMissing();
            this.config = YamlConfiguration.loadConfiguration(configFile);
            return this.config;
        } catch (Exception e) {
            plugin.getLogger().severe("[Translator] Failed to initialize/load config: " + e.getMessage());
            this.config = new YamlConfiguration(); // fallback to empty to avoid NPEs
            return this.config;
        }
    }

    /**
     * Reloads the configuration from disk without creating or overwriting files.
     *
     * @return reloaded {@link FileConfiguration}.
     */
    public FileConfiguration reload() {
        this.config = YamlConfiguration.loadConfiguration(configFile);
        return this.config;
    }

    /**
     * @return the loaded configuration (call {@link #initAndLoad()} first).
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Saves the in-memory configuration back to disk.
     * Does nothing if the configuration was never loaded.
     */
    public void save() {
        if (config == null) return;
        try {
            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().severe("[Translator] Failed to save config: " + e.getMessage());
        }
    }

    /**
     * @return the physical config file on disk.
     */
    public File getConfigFile() {
        return configFile;
    }
}
