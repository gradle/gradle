package org.gradle.client.core.database.sqldelight


import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("SqlDelightMigrations")

const val META_TABLE_NAME = "__sqldelight__"
const val VERSION_COLUMN_NAME = "schema_version"

// TODO add transaction once https://github.com/cashapp/sqldelight/issues/1856 is fixed
fun SqlDriver.migrateTo(schema: SqlSchema<QueryResult.Value<Unit>>, vararg codeVersions: AfterVersion) {

    var needsMetaTable = false

    @Suppress("TooGenericExceptionCaught")
    val version: Long = try {
        fetchCurrentVersion()
    } catch (e: Exception) {
        logger.atDebug().log {
            "Unable to get database schema version, will create the meta table (" +
                    "${e::class.qualifiedName}: ${e.message})"
        }
        needsMetaTable = true
        0L
    }

    if (version < schema.version) {
        logger.atInfo().log { "Migrating database from schema version $version to version ${schema.version}" }

        schema.migrate(this, version, schema.version, *codeVersions)
        logger.atDebug().log { "Database migrated" }

        if (needsMetaTable) createMetaTable()

        when (version) {
            0L -> insertSchemaVersion(schema.version)
            else -> updateSchemaVersion(schema.version)
        }
    } else {
        logger.atDebug().log { "Database schema is up to date" }
    }
}

private fun SqlDriver.fetchCurrentVersion(): Long =
    executeQuery(null, "SELECT value FROM $META_TABLE_NAME WHERE name = '$VERSION_COLUMN_NAME'", {
        QueryResult.Value(
            if (it.next().value) it.getLong(0) ?: 0L
            else 0L
        )
    }, 0).value

private fun SqlDriver.createMetaTable() {
    execute(null, "CREATE TABLE $META_TABLE_NAME(name VARCHAR NOT NULL PRIMARY KEY, value VARCHAR)", 0)
    logger.atDebug().log { "Meta table created" }
}

private fun SqlDriver.insertSchemaVersion(version: Long) {
    execute(null, "INSERT INTO $META_TABLE_NAME(name, value) VALUES(?, ?)", 2) {
        bindString(0, VERSION_COLUMN_NAME)
        bindLong(1, version)
    }
    logger.atDebug().log { "Initial schema version $version stored" }
}

private fun SqlDriver.updateSchemaVersion(version: Long) {
    execute(null, "UPDATE $META_TABLE_NAME SET value=? WHERE name=?", 2) {
        bindLong(0, version)
        bindString(1, VERSION_COLUMN_NAME)
    }
    logger.atDebug().log { "Schema version updated to $version" }
}
