package org.gradle.client.logic.database.sqldelight

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.logs.LogSqliteDriver
import org.gradle.client.logic.files.AppDirs
import org.slf4j.LoggerFactory
import java.util.*

private val logger = LoggerFactory.getLogger(SqlDriverFactory::class.java)
private val sqlDriverLogger = LoggerFactory.getLogger("app.cash.sqldelight.db.SqlDriver")

class SqlDriverFactory(
    private val appDirs: AppDirs
) {

    companion object {
        const val DB_NAME = "gradle-client.db"
    }

    fun createDriver(): SqlDriver =
        LogSqliteDriver(doCreateDriver()) { log ->
            sqlDriverLogger.atTrace().log { log }
        }.also {
            logger.atDebug().log { "Database '$DB_NAME' opened" }
        }

    fun stopDriver(driver: SqlDriver) {
        driver.tryExecutePragma("PRAGMA analysis_limit = 1024;")
        driver.tryExecutePragma("PRAGMA optimize;")
        driver.close()
        logger.atDebug().log() { "Database '$DB_NAME' closed" }
    }

    private fun doCreateDriver(): SqlDriver {
        val jdbcUrl = "jdbc:sqlite:${appDirs.dataDirectory.resolve(DB_NAME)}"
        logger.atDebug().log { "Connecting to $jdbcUrl" }
        return JdbcSqliteDriver(
            url = jdbcUrl,
            properties = Properties().apply {
                put("journal_mode", "wal")
                put("synchronous", "normal")
                put("foreign_keys", "true")
            }
        )
    }

    /**
     * These PRAGMA are not mandatory and fail in some environments.
     */
    private fun SqlDriver.tryExecutePragma(pragma: String) {
        execute(identifier = null, sql = pragma, parameters = 0)
    }
}
