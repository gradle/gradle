package org.gradle.client.ui

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.gradle.client.ui.UiRoot

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Gradle Client") {
        UiRoot()
    }
}
