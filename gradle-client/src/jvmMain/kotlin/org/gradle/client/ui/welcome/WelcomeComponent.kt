package org.gradle.client.ui.welcome

import com.arkivanov.decompose.ComponentContext
import java.io.File

class WelcomeComponent(
    context: ComponentContext,
    private val onBuildSelected: (String) -> Unit
) : ComponentContext by context {

    fun onAddBuildClicked(rootDir: File) {
        println("Adding build root dir: $rootDir")
    }

    fun onBuildClicked(id: String) {
        onBuildSelected(id)
    }
}
