package io.github.mcengine.extension.addon.translator;

import io.github.mcengine.api.core.MCEngineCoreApi;
import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.api.artificialintelligence.extension.addon.IMCEngineArtificialIntelligenceAddOn;

import io.github.mcengine.extension.addon.translator.command.TranslatorCommand;
import io.github.mcengine.extension.addon.translator.hologram.HologramManager;
import io.github.mcengine.extension.addon.translator.hologram.MoveListener;
import io.github.mcengine.extension.addon.translator.hologram.QuitListener;
import io.github.mcengine.extension.addon.translator.lang.LangRegistry;
import io.github.mcengine.extension.addon.translator.listener.ChatListener;
import io.github.mcengine.extension.addon.translator.player.PlayerLangStore;
import io.github.mcengine.extension.addon.translator.player.PlayerLangStore.Backend;
import io.github.mcengine.extension.addon.translator.player.orm.JdbcLangPreferenceRepository;
import io.github.mcengine.extension.addon.translator.player.orm.LangPreferenceRepository;
import io.github.mcengine.extension.addon.translator.storage.DatabaseManager;
import io.github.mcengine.extension.addon.translator.storage.MySqlDialect;
import io.github.mcengine.extension.addon.translator.storage.SqlDialect;
import io.github.mcengine.extension.addon.translator.storage.SqliteDialect;
import io.github.mcengine.extension.addon.translator.translate.TranslationCache;
import io.github.mcengine.extension.addon.translator.translate.TranslationService;
import io.github.mcengine.extension.addon.translator.util.ConfigUtil;

import io.github.mcengine.api.artificialintelligence.MCEngineArtificialIntelligenceApi;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Translator AddOn entrypoint.
 * Wires config, services, listeners, and the /translator command.
 */
public class Translator implements IMCEngineArtificialIntelligenceAddOn {

    /** Extension logger used for structured AddOn logs. */
    private MCEngineExtensionLogger logger;

    /** Persistent store: per-player language preferences. */
    private PlayerLangStore playerLangStore;

    /** Translation service wrapper around AI with cache. */
    private TranslationService translationService;

    /** Per-player hologram manager. */
    private HologramManager holograms;

    /** ISO 639-1 language registry. */
    private LangRegistry langRegistry;

    /** Custom-path configuration utility. */
    private ConfigUtil configUtil;

    /** Loaded configuration handle. */
    private FileConfiguration cfg;

    /** Command bridge impl (created after onLoad initializes services). */
    private TranslatorCommand commandImpl;

    /** Database manager when using DB backend; otherwise {@code null}. */
    private DatabaseManager dbManager;

    /** ORM repository for player language prefs (DB backend only). */
    private LangPreferenceRepository langRepo;

    @Override
    public void onLoad(Plugin plugin) {
        // Logger: (Plugin, name, shortName)
        this.logger = new MCEngineExtensionLogger(
                plugin,
                "AddOn",
                "ai-translator"
        );

        // Custom-path config
        this.configUtil = new ConfigUtil(plugin);
        this.cfg = configUtil.initAndLoad();

        // Core services
        this.langRegistry = new LangRegistry();

        // Determine storage backend
        String backendStr = cfg.getString("storage.backend", "yaml").toLowerCase(Locale.ROOT);
        Backend backend = switch (backendStr) {
            case "sqlite", "mysql" -> Backend.DB;
            default -> Backend.YAML;
        };

        // Optional DB init
        if (backend == Backend.DB) {
            SqlDialect dialect;
            if ("mysql".equals(backendStr)) {
                dialect = new MySqlDialect();
            } else {
                dialect = new SqliteDialect();
            }
            this.dbManager = new DatabaseManager(plugin, cfg, backendStr, logger);
            Connection conn = dbManager.getConnection();
            if (conn != null) {
                this.langRepo = new JdbcLangPreferenceRepository(conn, dialect, plugin.getLogger());
            } else {
                logger.getLogger().warning("[Translator] Falling back to YAML because DB connection could not be established.");
                backend = Backend.YAML;
            }
        }

        this.playerLangStore = new PlayerLangStore(plugin, configUtil, backend, langRepo);

        TranslationCache cache = new TranslationCache(
                cfg.getLong("cache.maxEntries", 1000),
                cfg.getLong("cache.ttlSeconds", 300)
        );
        MCEngineArtificialIntelligenceApi ai = new MCEngineArtificialIntelligenceApi();
        this.translationService = new TranslationService(
                plugin,
                ai,
                cache,
                cfg.getString("ai.platform", "openai"),
                cfg.getString("ai.model", "gpt-4o-mini"),
                cfg.getString("ai.tokenType", "server"),
                cfg.getString("ai.systemPrompt",
                        "You are a precise, safe translator. Detect the source language and translate the user's message to the requested target language code (ISO 639-1). Output only the translated text.")
        );

        this.holograms = new HologramManager(plugin);

        // Listeners
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new MoveListener(holograms), plugin);
        pm.registerEvents(new QuitListener(holograms), plugin);
        pm.registerEvents(new ChatListener(plugin, playerLangStore, translationService, holograms), plugin);

        // Command bridge
        this.commandImpl = new TranslatorCommand(p -> playerLangStore, () -> langRegistry);

        // Register /translator via CommandMap
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            CommandMap commandMap = (CommandMap) f.get(Bukkit.getServer());

            Command translator = new Command("translator") {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return Translator.this.execute(sender, label, args);
                }
                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    return Translator.this.tabComplete(sender, alias, args);
                }
            };
            translator.setDescription("Manage AI Translator language");
            translator.setUsage("/translator set <lang> | /translator get | /translator clear");
            commandMap.register("translator", translator);
        } catch (Exception e) {
            logger.getLogger().severe("[Translator] Failed to register /translator command: " + e.getMessage());
        }
    }

    /** Executes the /translator command via our bridge. */
    public boolean execute(CommandSender sender, String label, String[] args) {
        return commandImpl.onCommand(sender, null, label, args);
    }

    /** Provides tab completions without depending on an external class at runtime. */
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        final List<String> out = new ArrayList<>(16);
        if (args == null) return out;

        if (args.length == 0) return out;

        if (args.length == 1) {
            final String p = args[0] == null ? "" : args[0].toLowerCase(Locale.ROOT);
            if ("set".startsWith(p)) out.add("set");
            if ("get".startsWith(p)) out.add("get");
            if ("clear".startsWith(p)) out.add("clear");
            return out;
        }

        if (args.length == 2 && "set".equalsIgnoreCase(args[0])) {
            final String p = args[1] == null ? "" : args[1].toLowerCase(Locale.ROOT);
            if (langRegistry == null) return out; // safety
            int added = 0;
            for (String c : langRegistry.codes()) {
                if (c.startsWith(p)) {
                    out.add(c);
                    if (++added >= 50) break;
                }
            }
            return out;
        }

        return out;
    }

    @Override
    public void onDisload(Plugin plugin) {
        if (holograms != null) holograms.shutdown();
        if (playerLangStore != null) playerLangStore.save();
        if (configUtil != null) configUtil.save();
        if (dbManager != null) dbManager.close();
    }

    @Override
    public void setId(String id) {
        MCEngineCoreApi.setId("mcengine-artificialintelligence-addon-translator");
    }
}
