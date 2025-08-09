package io.github.mcengine.extension.addon.translator.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * Utilities for creating, loading, saving, and reloading the Translator configuration
 * at a custom location under the plugin's data folder.
 *
 * <p>Location (relative to the plugin data folder):</p>
 * <pre>
 *   extensions/addons/configs/MCEngineTranslator/config.yml
 * </pre>
 *
 * <p><b>Behavior</b></p>
 * <ul>
 *   <li>If the directory does not exist, it will be created.</li>
 *   <li>If the {@code config.yml} does not exist, a minimal default config will be generated once.</li>
 *   <li>Existing files are never overwritten.</li>
 * </ul>
 *
 * <p><b>Storage note:</b> This AddOn uses the Common API's shared database connection when
 * {@code storage.backend} is set to {@code db}. The YAML fallback stores player language
 * preferences directly inside this file.</p>
 */
public final class ConfigUtil {

    /** Relative path where the Translator config is stored. */
    private final String folderPath = "extensions/addons/configs/MCEngineTranslator";

    /** Owning Bukkit plugin instance. */
    private final Plugin plugin;

    /** Resolved config file on disk: {@code <dataFolder>/<folderPath>/config.yml}. */
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

        // Create a minimal default configuration (write values directly)
        final YamlConfiguration def = new YamlConfiguration();

        // Add explanatory header (saved to file)
        def.options().header(
            "MCEngine Translator AddOn Configuration\n" +
            "\n" +
            "Storage:\n" +
            "  storage.backend:\n" +
            "    - \"db\"   : Use Common API shared database for player language preferences (recommended).\n" +
            "    - \"yaml\" : Store preferences in this config file under the 'players' section.\n" +
            "\n" +
            "AI Settings:\n" +
            "  ai.platform   : Platform key registered in MCEngine (e.g., \"openai\", \"deepseek\", \"customurl\").\n" +
            "  ai.model      : Model name/alias registered under the platform.\n" +
            "  ai.tokenType  : \"server\" uses the server token; \"player\" uses each player's token from the Common API DB.\n" +
            "  ai.systemPrompt: System prompt that guides translation behavior.\n" +
            "\n" +
            "Cache:\n" +
            "  cache.maxEntries : Maximum number of translations kept in the in-memory cache.\n" +
            "  cache.ttlSeconds : Time-to-live for cached translations in seconds.\n" +
            "\n" +
            "Notes:\n" +
            "  - This file is created once with safe defaults and will not be overwritten on updates.\n" +
            "  - When using \"yaml\" backend, preferences will be saved under the 'players' section.\n"
        );
        def.options().copyHeader(true);

        // === Translator Defaults ===
        // Use Common API DB by default. Options: "db" | "yaml"
        def.set("storage.backend", "db");

        // AI Defaults for Translator AddOn
        def.set("ai.platform", "deepseek");
        def.set("ai.model", "deepseek-chat");
        def.set("ai.tokenType", "server");
        def.set("ai.systemPrompt",
                "You are a precise, safe translator. Detect the user's source language and translate the " +
                "message to the requested target language code (ISO 639-1). Output only the translated text.");

        // Simple in-memory cache defaults
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
