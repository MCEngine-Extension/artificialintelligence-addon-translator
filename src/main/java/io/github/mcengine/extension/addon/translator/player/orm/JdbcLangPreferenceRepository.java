package io.github.mcengine.extension.addon.translator.player.orm;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Logger;

/**
 * JDBC implementation of {@link LangPreferenceRepository} using the Common API's shared connection.
 * <p>
 * Dialect is auto-detected from the JDBC URL:
 * <ul>
 *   <li><b>MySQL</b>: {@code jdbc:mysql:...}</li>
 *   <li><b>SQLite</b>: all other URLs are treated as SQLite</li>
 * </ul>
 */
public final class JdbcLangPreferenceRepository implements LangPreferenceRepository {

    /** Shared JDBC connection (managed externally by Common API). */
    private final Connection conn;

    /** Logger for diagnostics. */
    private final Logger logger;

    /** Table name for preferences. */
    private final String table = "translator_player_lang";

    /** Detected SQL dialect. */
    private final Dialect dialect;

    /**
     * Supported SQL dialects for small DDL/DML differences.
     */
    private enum Dialect {
        MYSQL, SQLITE
    }

    /**
     * Creates a new repository and ensures the table exists.
     *
     * @param conn   shared SQL connection
     * @param logger logger for diagnostics
     */
    public JdbcLangPreferenceRepository(Connection conn, Logger logger) {
        this.conn = conn;
        this.logger = logger;
        this.dialect = detectDialect(conn);
        createTable();
    }

    /** Detects the SQL dialect from the JDBC URL. */
    private Dialect detectDialect(Connection c) {
        try {
            DatabaseMetaData md = c.getMetaData();
            String url = md != null ? md.getURL() : null;
            if (url != null && url.toLowerCase(Locale.ROOT).startsWith("jdbc:mysql")) {
                return Dialect.MYSQL;
            }
        } catch (Exception ignored) {}
        return Dialect.SQLITE;
    }

    /** Creates the preferences table if absent. */
    private void createTable() {
        final String ddl;
        if (dialect == Dialect.MYSQL) {
            ddl = "CREATE TABLE IF NOT EXISTS " + table + " (\n" +
                  "  player_uuid CHAR(36) NOT NULL PRIMARY KEY,\n" +
                  "  lang_code   VARCHAR(8) NOT NULL\n" +
                  ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        } else {
            ddl = "CREATE TABLE IF NOT EXISTS " + table + " (\n" +
                  "  player_uuid TEXT PRIMARY KEY,\n" +
                  "  lang_code   TEXT NOT NULL\n" +
                  ");";
        }

        try (PreparedStatement ps = conn.prepareStatement(ddl)) {
            ps.execute();
        } catch (Exception e) {
            logger.warning("[Translator] Failed creating table: " + e.getMessage());
        }
    }

    @Override
    public void upsert(UUID uuid, String lang) {
        // Safety: store lowercase, short (ISO 639-1)
        final String normalized = lang == null ? null : lang.toLowerCase(Locale.ROOT);
        if (normalized == null || normalized.isBlank()) return;

        final String sql;
        if (dialect == Dialect.MYSQL) {
            sql = "INSERT INTO " + table + " (player_uuid, lang_code) VALUES (?, ?) " +
                  "ON DUPLICATE KEY UPDATE lang_code = VALUES(lang_code);";
        } else {
            sql = "INSERT INTO " + table + " (player_uuid, lang_code) VALUES (?, ?) " +
                  "ON CONFLICT(player_uuid) DO UPDATE SET lang_code = excluded.lang_code;";
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, normalized);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("[Translator] upsert failed: " + e.getMessage());
        }
    }

    @Override
    public String find(UUID uuid) {
        final String sql = "SELECT lang_code FROM " + table + " WHERE player_uuid = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (Exception e) {
            logger.warning("[Translator] find failed: " + e.getMessage());
        }
        return null;
    }

    @Override
    public Map<UUID, String> findAll() {
        final String sql = "SELECT player_uuid, lang_code FROM " + table + ";";
        Map<UUID, String> out = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID id = UUID.fromString(rs.getString(1));
                    String lang = rs.getString(2);
                    if (lang != null && !lang.isBlank()) {
                        out.put(id, lang.toLowerCase(Locale.ROOT));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception e) {
            logger.warning("[Translator] findAll failed: " + e.getMessage());
        }
        return out;
    }

    @Override
    public void delete(UUID uuid) {
        final String sql = "DELETE FROM " + table + " WHERE player_uuid = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("[Translator] delete failed: " + e.getMessage());
        }
    }
}
