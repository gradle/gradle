package org.gradle.client.ui.connected

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gradle.client.logic.gradle.GradleConnectionParameters
import org.gradle.client.ui.util.componentScope
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.gradle.GradleBuild
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.reflect.KClass

private val logger = LoggerFactory.getLogger(ConnectedComponent::class.java)

sealed interface ConnectionModel {
    data object Disconnected : ConnectionModel
    data class Connected(
        val events: List<String> = emptyList(),
        val result: Any? = null,
    ) : ConnectionModel
}

class ConnectedComponent(
    context: ComponentContext,
    val parameters: GradleConnectionParameters,
    private val onFinished: () -> Unit,
) : ComponentContext by context {

    private val mutableModel = MutableValue<ConnectionModel>(ConnectionModel.Disconnected)
    val model: Value<ConnectionModel> = mutableModel

    private val scope = componentScope()

    private lateinit var connection: ProjectConnection

    private val progressListener = org.gradle.tooling.events.ProgressListener { event ->
        mutableModel.value = when (val current = model.value) {
            ConnectionModel.Disconnected -> TODO("BOOM")
            is ConnectionModel.Connected -> current.copy(current.events + event.displayName)
        }
    }

    init {
        val cancel = GradleConnector.newCancellationTokenSource()
        val connector = GradleConnector.newConnector().forProjectDirectory(File(parameters.rootDir))

        lifecycle.doOnDestroy {
            cancel.cancel()
            connector.disconnect()
            logger.atInfo().log { "Disconnected from ${parameters.rootDir}" }
            mutableModel.value = ConnectionModel.Disconnected
        }

        scope.launch {
            connection = connector.connect()
            logger.atInfo().log { "Connected to ${parameters.rootDir}" }
            mutableModel.value = ConnectionModel.Connected()
        }
    }

    fun getBuildEnvironment() {
        getModel(BuildEnvironment::class)
    }

    fun getGradleBuild() {
        getModel(GradleBuild::class)
    }

    fun getGradleProject() {
        getModel(GradleProject::class)
    }

    private fun getModel(modelType: KClass<*>) {
        when (val current = model.value) {
            ConnectionModel.Disconnected -> TODO("BOOM")
            is ConnectionModel.Connected -> {
                mutableModel.value = current.copy(result = null)
                scope.launch {
                    withContext(Dispatchers.IO) {
                        logger.atDebug().log { "Get ${modelType.simpleName} model!" }
                        val result = connection.model(modelType.java)
                            .addProgressListener(progressListener, OperationType.entries.toSet())
                            .get()
                        logger.atInfo().log { "Got ${modelType.simpleName} model: $result" }
                        mutableModel.value = current.copy(result = result)
                    }
                }
            }
        }
    }

    fun onCloseClicked() {
        onFinished()
    }
}
