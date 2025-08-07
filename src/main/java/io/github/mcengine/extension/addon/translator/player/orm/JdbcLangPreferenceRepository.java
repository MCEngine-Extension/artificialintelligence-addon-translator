package io.github.mcengine.extension.addon.translator.player.orm;

import io.github.mcengine.extension.addon.translator.storage.SqlDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Logger;

/**
 * JDBC implementation of {@link LangPreferenceRepository}.
 */
public final class JdbcLangPreferenceRepository implements LangPreferenceRepository {

    /** Shared JDBC connection (managed externally). */
    private final Connection conn;
    /** SQL dialect handling DDL/DML differences. */
    private final SqlDialect dialect;
    /** Logger for diagnostics. */
    private final Logger logger;
    /** Table name for preferences. */
    private final String table = "translator_player_lang";

    public JdbcLangPreferenceRepository(Connection conn, SqlDialect dialect, Logger logger) {
        this.conn = conn;
        this.dialect = dialect;
        this.logger = logger;
        createTable();
    }

    private void createTable() {
        String ddl = dialect.tableDdl(table);
        try (PreparedStatement ps = conn.prepareStatement(ddl)) {
            ps.execute();
        } catch (Exception e) {
            logger.warning("[Translator] Failed creating table: " + e.getMessage());
        }
    }

    @Override
    public void upsert(UUID uuid, String lang) {
        String sql = dialect.upsertSql(table);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, lang);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("[Translator] upsert failed: " + e.getMessage());
        }
    }

    @Override
    public String find(UUID uuid) {
        String sql = dialect.findSql(table);
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
        String sql = dialect.findAllSql(table);
        Map<UUID, String> out = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID id = UUID.fromString(rs.getString(1));
                    String lang = rs.getString(2);
                    if (lang != null && !lang.isBlank()) out.put(id, lang.toLowerCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception e) {
            logger.warning("[Translator] findAll failed: " + e.getMessage());
        }
        return out;
    }

    @Override
    public void delete(UUID uuid) {
        String sql = dialect.deleteSql(table);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("[Translator] delete failed: " + e.getMessage());
        }
    }
}
