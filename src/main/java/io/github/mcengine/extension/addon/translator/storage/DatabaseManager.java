package io.github.mcengine.extension.addon.translator.storage;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.logging.Logger;

/**
 * Opens and manages a single JDBC connection for the translator add-on.
 * Supports {@code sqlite} and {@code mysql} backends.
 */
public final class DatabaseManager implements AutoCloseable {

    /** Parent plugin for locating data folder. */
    private final Plugin plugin;
    /** Loaded configuration backing DB settings. */
    private final FileConfiguration cfg;
    /** Backend id: "sqlite" or "mysql". */
    private final String backend;
    /** Structured extension logger. */
    private final MCEngineExtensionLogger extLogger;
    /** Open JDBC connection (or null if failed). */
    private Connection connection;

    public DatabaseManager(Plugin plugin, FileConfiguration cfg, String backend, MCEngineExtensionLogger logger) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.backend = backend;
        this.extLogger = logger;
        open();
    }

    private void open() {
        Logger log = plugin.getLogger();
        try {
            if ("sqlite".equalsIgnoreCase(backend)) {
                String file = cfg.getString("storage.sqlite.file", "translator.db");
                File dbFile = new File(plugin.getDataFolder(), file);
                if (dbFile.getParentFile() != null) dbFile.getParentFile().mkdirs();
                String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                connection = DriverManager.getConnection(url);
                log.info("[Translator] SQLite connected: " + dbFile.getAbsolutePath());
            } else if ("mysql".equalsIgnoreCase(backend)) {
                String host = cfg.getString("storage.mysql.host", "127.0.0.1");
                int port = cfg.getInt("storage.mysql.port", 3306);
                String database = cfg.getString("storage.mysql.database", "mcengine");
                String user = cfg.getString("storage.mysql.username", "root");
                String pass = cfg.getString("storage.mysql.password", "password");
                String params = cfg.getString("storage.mysql.params", "useSSL=false&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true");
                String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + params;
                connection = DriverManager.getConnection(url, user, pass);
                log.info("[Translator] MySQL connected: " + host + ":" + port + "/" + database);
            } else {
                log.warning("[Translator] Unknown DB backend: " + backend);
            }
        } catch (Exception e) {
            extLogger.getLogger().severe("[Translator] DB connection failed: " + e.getMessage());
            connection = null;
        }
    }

    /** @return the open JDBC connection or {@code null} if unavailable. */
    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (Exception ignored) {}
        connection = null;
    }
}
