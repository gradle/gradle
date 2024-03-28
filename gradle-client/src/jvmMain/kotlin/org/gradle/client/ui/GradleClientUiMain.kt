package org.gradle.client.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.configureSwingGlobalsForCompose
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.gradle.client.logic.Constants.APPLICATION_DISPLAY_NAME
import org.gradle.client.logic.database.BuildsRepository
import org.gradle.client.logic.database.sqldelight.ApplicationDatabaseFactory
import org.gradle.client.logic.database.sqldelight.SqlDriverFactory
import org.gradle.client.logic.files.AppDirs
import org.gradle.client.ui.theme.GradleClientTheme
import org.gradle.client.ui.util.appDirs
import org.gradle.client.ui.util.runOnUiThread
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
    val database = ApplicationDatabaseFactory().createDatabase(sqlDriver).also {
        logger.atInfo().log { "DB started" }
    }

    GradleClientUiMain(
        appDirs,
        BuildsRepository(database.buildsQueries)
    ).run()
}

class GradleClientUiMain(
    private val appDirs: AppDirs,
    private val buildsRepository: BuildsRepository
) : Runnable {

    @OptIn(ExperimentalComposeUiApi::class)
    override fun run() {

        // Should be called before using any class from java.swing.*
        // (even before SwingUtilities.invokeLater or MainUIDispatcher)
        configureSwingGlobalsForCompose()

        val lifecycle = LifecycleRegistry()
        val uiComponent = runOnUiThread {
            UiComponent(
                context = DefaultComponentContext(lifecycle = lifecycle),
                appDirs = appDirs,
                buildsRepository = buildsRepository,
            )
        }

        application {
            Window(onCloseRequest = ::exitApplication, title = APPLICATION_DISPLAY_NAME) {
                LaunchedEffect(Unit) {
                    logger.atInfo().log { "$APPLICATION_DISPLAY_NAME started!" }
                }
                GradleClientTheme {
                    UiContent(uiComponent)
                }
            }
        }
    }
}
