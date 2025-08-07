package io.github.mcengine.extension.addon.translator.storage;

/**
 * Small SQL dialect abstraction for DDL/DML differences.
 */
public interface SqlDialect {

    /**
     * @param table target table
     * @return DDL that creates the preferences table if missing
     */
    String tableDdl(String table);

    /**
     * @param table target table
     * @return parameterized UPSERT statement: (uuid, lang)
     */
    String upsertSql(String table);

    /**
     * @param table target table
     * @return parameterized SELECT of a single row by uuid, returning lang
     */
    String findSql(String table);

    /**
     * @param table target table
     * @return SELECT of all rows: uuid, lang
     */
    String findAllSql(String table);

    /**
     * @param table target table
     * @return parameterized DELETE by uuid
     */
    String deleteSql(String table);
}
