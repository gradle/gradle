package org.gradle.client.ui.connected

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gradle.client.core.gradle.GradleConnectionParameters
import org.gradle.client.core.gradle.GradleDistribution
import org.gradle.client.ui.AppDispatchers
import org.gradle.client.ui.connected.actions.GetBuildEnvironment
import org.gradle.client.ui.connected.actions.GetGradleBuild
import org.gradle.client.ui.connected.actions.GetGradleProject
import org.gradle.client.ui.connected.actions.GetModelAction
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

private val logger = LoggerFactory.getLogger(ConnectedComponent::class.java)

sealed interface ConnectionModel {
    data object Connecting : ConnectionModel
    data class ConnectionFailure(val exception: Exception) : ConnectionModel
    data class Connected(
        val events: List<Event> = emptyList(),
        val outcome: Outcome = Outcome.None,
    ) : ConnectionModel
}

// Hashcode is necessary to distinguish similar events in the stream
// for presentation in LazyColumn
data class Event(val text: String, private val hashCode: Int)

sealed interface Outcome {
    data object None : Outcome
    data object Building : Outcome
    data class Result(val model: Any) : Outcome
    data class Failure(val exception: Exception) : Outcome
}

class ConnectedComponent(
    context: ComponentContext,
    private val appDispatchers: AppDispatchers,
    val parameters: GradleConnectionParameters,
    private val onFinished: () -> Unit,
) : ComponentContext by context {

    private val mutableModel = MutableValue<ConnectionModel>(ConnectionModel.Connecting)
    val model: Value<ConnectionModel> = mutableModel

    val modelActions = listOf(
        GetBuildEnvironment(),
        GetGradleBuild(),
        GetGradleProject()
    )

    private val scope = coroutineScope(appDispatchers.main + SupervisorJob())

    private lateinit var connection: ProjectConnection

    init {
        val cancel = GradleConnector.newCancellationTokenSource()
        val connector = GradleConnector.newConnector()
            .forProjectDirectory(File(parameters.rootDir))
            .let { c ->
                when (parameters.gradleUserHomeDir) {
                    null -> c
                    else -> c.useGradleUserHomeDir(File(parameters.gradleUserHomeDir))
                }
            }
            .let { c ->
                when (parameters.distribution) {
                    GradleDistribution.Default -> c.useBuildDistribution()
                    is GradleDistribution.Local -> c.useInstallation(File(parameters.distribution.installDir))
                    is GradleDistribution.Version -> c.useGradleVersion(parameters.distribution.version)
                }
            }

        lifecycle.doOnDestroy {
            cancel.cancel()
            connector.disconnect()
            logger.atInfo().log { "Disconnected from ${parameters.rootDir}" }
            mutableModel.value = ConnectionModel.Connecting
        }

        scope.launch {
            try {
                connection = connector.connect()
                logger.atInfo().log { "Connected to ${parameters.rootDir}" }
                mutableModel.value = ConnectionModel.Connected()
            } catch (ex: Exception) {
                logger.atError().log("Connection to ${parameters.rootDir} failed", ex)
                mutableModel.value = ConnectionModel.ConnectionFailure(ex)
            }
        }
    }

    fun getModel(modelType: KClass<*>) {
        scope.launch {
            model.value.requireConnected { current ->
                mutableModel.value = current.copy(events = emptyList(), outcome = Outcome.Building)
                withContext(appDispatchers.io) {
                    logger.atDebug().log { "Get ${modelType.simpleName} model!" }
                    try {
                        val result = connection.model(modelType.java)
                            .let { b ->
                                when (parameters.javaHomeDir) {
                                    null -> b
                                    else -> b.addArguments("-Dorg.gradle.java.home=${parameters.javaHomeDir}")
                                }
                            }
                            .addProgressListener(
                                newEventListener(),
                                OperationType.entries.toSet() - OperationType.GENERIC
                            )
                            .get()
                        logger.atInfo().log { "Got ${modelType.simpleName} model: $result" }
                        model.value.requireConnected { model ->
                            withContext(appDispatchers.main) {
                                mutableModel.value = model.copy(outcome = Outcome.Result(result))
                            }
                        }
                    } catch (ex: Exception) {
                        model.value.requireConnected { model ->
                            withContext(appDispatchers.main) {
                                mutableModel.value = model.copy(outcome = Outcome.Failure(ex))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun newEventListener(): ProgressListener =
        object : ProgressListener {
            private val start = Instant.now()
            override fun statusChanged(event: ProgressEvent) {
                val eventTimeSinceStart = Duration.between(start, Instant.ofEpochMilli(event.eventTime))
                val uiEvent = Event(
                    "${eventTimeSinceStart.toPrettyString().padStart(10)}  ${event.displayName}",
                    event.hashCode()
                )
                scope.launch {
                    model.value.requireConnected { current ->
                        mutableModel.value = current.copy(events = current.events + uiEvent)
                    }
                }
            }
        }

    private suspend fun ConnectionModel.requireConnected(
        action: suspend (connected: ConnectionModel.Connected) -> Unit
    ) {
        when (this) {
            is ConnectionModel.Connected -> action(this)
            else -> {
                val ex = IllegalStateException("Not connected! (was ${this@requireConnected})")
                withContext(appDispatchers.main) {
                    mutableModel.value = ConnectionModel.ConnectionFailure(ex)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> actionFor(model: T): GetModelAction<T>? =
        modelActions.find { it.modelType.java.isAssignableFrom(model::class.java) } as? GetModelAction<T>

    fun onCloseClicked() {
        onFinished()
    }
}

private fun Duration.toPrettyString() = buildString {
    val hours = toHours()
    if (hours > 0) append("${hours}h ")
    val minutes = minusHours(hours).toMinutes()
    if (minutes > 0) append("${minutes.toString().padStart(2)}m ")
    if (hours <= 0) {
        val seconds = minusHours(hours).minusMinutes(minutes).toSeconds()
        if (seconds > 0) append("${seconds.toString().padStart(2)}s ")
        if (minutes <= 0) {
            val milliseconds = minusHours(hours).minusMinutes(minutes).minusSeconds(seconds).toMillis()
            if (milliseconds > 0) append("${milliseconds.toString().padStart(3)}ms")
        }
    }
}
