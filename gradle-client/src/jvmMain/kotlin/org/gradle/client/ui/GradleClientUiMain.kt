package org.gradle.client.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.gradle.client.logic.Constants.APPLICATION_DISPLAY_NAME
import org.gradle.client.logic.database.sqldelight.ApplicationDatabaseFactory
import org.gradle.client.logic.database.sqldelight.SqlDriverFactory
import org.gradle.client.ui.theme.GradleClientTheme
import org.gradle.client.ui.util.appDirs
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(GradleClientUiMain::class.java)

fun main() {

    appDirs.logApplicationDirectories()

    val sqlDriverFactory = SqlDriverFactory(appDirs)
    val sqlDriver = sqlDriverFactory.createDriver()
    Runtime.getRuntime().addShutdownHook(
        Thread {
            sqlDriverFactory.stopDriver(sqlDriver)
            logger.atInfo().log { "DB stopped" }
        }
    )
    val db = ApplicationDatabaseFactory().createDatabase(sqlDriver).also {
        logger.atInfo().log { "DB started" }
    }

    GradleClientUiMain().run()
}

class GradleClientUiMain : Runnable {

    override fun run() =
        application {
            Window(onCloseRequest = ::exitApplication, title = APPLICATION_DISPLAY_NAME) {
                LaunchedEffect(Unit) {
                    logger.atInfo().log { "$APPLICATION_DISPLAY_NAME started!" }
                }
                GradleClientTheme {
                    UiContent()
                }
            }
        }
}
