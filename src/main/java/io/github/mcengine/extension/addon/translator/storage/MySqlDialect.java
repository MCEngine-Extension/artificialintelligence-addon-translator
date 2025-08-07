package io.github.mcengine.extension.addon.translator.storage;

/**
 * MySQL dialect implementation.
 */
public final class MySqlDialect implements SqlDialect {
    @Override
    public String tableDdl(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " (\n" +
               "  player_uuid CHAR(36) NOT NULL PRIMARY KEY,\n" +
               "  lang_code   VARCHAR(8) NOT NULL\n" +
               ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
    }

    @Override
    public String upsertSql(String table) {
        return "INSERT INTO " + table + " (player_uuid, lang_code) VALUES (?, ?)\n" +
               "ON DUPLICATE KEY UPDATE lang_code = VALUES(lang_code);";
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
