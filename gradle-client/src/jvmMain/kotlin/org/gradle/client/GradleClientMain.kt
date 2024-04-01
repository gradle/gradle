package org.gradle.client

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.configureSwingGlobalsForCompose
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.gradle.client.core.Constants.APPLICATION_DISPLAY_NAME
import org.gradle.client.core.database.BuildsRepository
import org.gradle.client.core.database.sqldelight.ApplicationDatabaseFactory
import org.gradle.client.core.database.sqldelight.SqlDriverFactory
import org.gradle.client.core.files.AppDirs
import org.gradle.client.core.util.appDirs
import org.gradle.client.ui.AppDispatchers
import org.gradle.client.ui.UiComponent
import org.gradle.client.ui.UiContent
import org.slf4j.LoggerFactory
import javax.swing.SwingUtilities

private val logger = LoggerFactory.getLogger(GradleClientMain::class.java)

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

    GradleClientMain(
        appDirs,
        BuildsRepository(database.buildsQueries)
    ).run()
}

class GradleClientMain(
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
                appDispatchers = AppDispatchers(),
                appDirs = appDirs,
                buildsRepository = buildsRepository,
            )
        }

        application {
            Window(onCloseRequest = ::exitApplication, title = APPLICATION_DISPLAY_NAME) {
                LaunchedEffect(Unit) {
                    logger.atInfo().log { "$APPLICATION_DISPLAY_NAME started!" }
                }
                UiContent(uiComponent)
            }
        }
    }
}

private fun <T> runOnUiThread(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) {
        return block()
    }

    var error: Throwable? = null
    var result: T? = null

    @Suppress("TooGenericExceptionCaught")
    SwingUtilities.invokeAndWait {
        try {
            result = block()
        } catch (e: Throwable) {
            error = e
        }
    }

    error?.also { throw it }

    @Suppress("UNCHECKED_CAST")
    return result as T
}
