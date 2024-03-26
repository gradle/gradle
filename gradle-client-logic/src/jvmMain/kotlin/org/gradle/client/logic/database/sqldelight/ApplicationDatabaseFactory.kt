package org.gradle.client.logic.database.sqldelight

import app.cash.sqldelight.db.SqlDriver
import org.gradle.client.logic.database.sqldelight.generated.ApplicationDatabase

class ApplicationDatabaseFactory {
    fun createDatabase(driver: SqlDriver): ApplicationDatabase {
        driver.migrateTo(ApplicationDatabase.Schema)
        return ApplicationDatabase(driver)
    }
}
