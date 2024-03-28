package org.gradle.client.ui.connected

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.gradle.client.logic.gradle.GradleConnectionParameters
import org.gradle.client.logic.gradle.GradleDistribution
import org.gradle.client.ui.util.componentScope

sealed interface ConnectedModel {
    data object Disconnected : ConnectedModel
    data object Connected : ConnectedModel
}

class ConnectedComponent(
    context: ComponentContext,
    val gradleConnectionParameters: GradleConnectionParameters,
    private val onFinished: () -> Unit,
) : ComponentContext by context {

    private val mutableModel = MutableValue<ConnectedModel>(ConnectedModel.Disconnected)
    val model: Value<ConnectedModel> = mutableModel

    private val scope = componentScope()

    init {
        scope.launch {

        }
    }

    fun onCloseClicked() {
        onFinished()
    }
}
