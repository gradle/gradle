package org.gradle.client.ui.build

import com.arkivanov.decompose.ComponentContext

class BuildComponent(
    context: ComponentContext,
    val id: String,
    private val onFinished: () -> Unit
) : ComponentContext by context {

    fun onCloseClicked() {
        onFinished()
    }
}
