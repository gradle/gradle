package org.gradle.client.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.gradle.client.logic.Constants.APPLICATION_DISPLAY_NAME
import org.gradle.client.logic.files.AppDirs
import org.slf4j.LoggerFactory

private val LOGGER = LoggerFactory.getLogger(GradleClientUiMain::class.java)

fun main() {
    AppDirs.logApplicationDirectories()
    GradleClientUiMain().run()
}

class GradleClientUiMain : Runnable {
    override fun run() =
        application {
            Window(onCloseRequest = ::exitApplication, title = APPLICATION_DISPLAY_NAME) {
                LaunchedEffect(Unit) {
                    LOGGER.atInfo().log { "$APPLICATION_DISPLAY_NAME started!" }
                }
                UiRoot()
            }
        }
}
