package io.github.mcengine.extension.addon.translator.storage;

/**
 * SQLite dialect implementation.
 */
public final class SqliteDialect implements SqlDialect {
    @Override
    public String tableDdl(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " (\n" +
               "  player_uuid TEXT PRIMARY KEY,\n" +
               "  lang_code   TEXT NOT NULL\n" +
               ");";
    }

    @Override
    public String upsertSql(String table) {
        return "INSERT INTO " + table + " (player_uuid, lang_code) VALUES (?, ?)\n" +
               "ON CONFLICT(player_uuid) DO UPDATE SET lang_code = excluded.lang_code;";
    }

    @Override
    public String findSql(String table) {
        return "SELECT lang_code FROM " + table + " WHERE player_uuid = ?;";
    }

    @Override
    public String findAllSql(String table) {
        return "SELECT player_uuid, lang_code FROM " + table + ";";
    }

    @Override
    public String deleteSql(String table) {
        return "DELETE FROM " + table + " WHERE player_uuid = ?;";
    }
}
