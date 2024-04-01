package org.gradle.client.ui.connected

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gradle.client.logic.gradle.GradleConnectionParameters
import org.gradle.client.ui.connected.actions.GetBuildEnvironment
import org.gradle.client.ui.connected.actions.GetGradleBuild
import org.gradle.client.ui.connected.actions.GetGradleProject
import org.gradle.client.ui.connected.actions.GetModelAction
import org.gradle.client.ui.util.componentScope
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

// TODO revisit event stream filtering

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

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> actionFor(model: T): GetModelAction<T>? =
        modelActions.find { action -> action.modelType.java.isAssignableFrom(model::class.java) } as? GetModelAction<T>

    private val scope = componentScope()

    private lateinit var connection: ProjectConnection

    private fun newEventListener(): org.gradle.tooling.events.ProgressListener =
        object : org.gradle.tooling.events.ProgressListener {
            private val start = Instant.now()
            override fun statusChanged(event: ProgressEvent) {
                val eventInstant = Instant.ofEpochMilli(event.eventTime)
                val eventTimeSinceStart = Duration.between(start, eventInstant)
                mutableModel.value = when (val current = model.value) {

                    is ConnectionModel.Connected -> current.copy(
                        current.events + Event(
                            "${eventTimeSinceStart.toPrettyString().padEnd(12)} ${event.displayName}",
                            event.hashCode()
                        )
                    )

                    else -> TODO("BOOM")
                }
            }
        }

    init {
        val cancel = GradleConnector.newCancellationTokenSource()
        val connector = GradleConnector.newConnector().forProjectDirectory(File(parameters.rootDir))

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
        when (val current = model.value) {
            is ConnectionModel.Connected -> {
                mutableModel.value = current.copy(events = emptyList(), outcome = Outcome.Building)
                scope.launch {
                    withContext(Dispatchers.IO) {
                        logger.atDebug().log { "Get ${modelType.simpleName} model!" }
                        try {
                            val result = connection.model(modelType.java)
                                .addProgressListener(
                                    newEventListener(),
                                    OperationType.entries.toSet() - OperationType.GENERIC
                                )
                                .get()
                            logger.atInfo().log { "Got ${modelType.simpleName} model: $result" }
                            when (val c = model.value) {
                                is ConnectionModel.Connected ->
                                    mutableModel.value = c.copy(outcome = Outcome.Result(result))

                                else -> TODO("BOOM")
                            }
                        } catch (ex: Exception) {
                            when (val c = model.value) {
                                is ConnectionModel.Connected ->
                                    mutableModel.value = c.copy(outcome = Outcome.Failure(ex))

                                else -> TODO("BOOM")
                            }
                        }
                    }
                }
            }

            else -> TODO("BOOM")

        }
    }

    fun onCloseClicked() {
        onFinished()
    }
}

private fun Duration.toPrettyString() = buildString {
    val hours = toHours()
    if (hours > 0) append("${hours}h ")
    val minutes = minusHours(hours).toMinutes()
    if (minutes > 0) append("${minutes}m ")
    if (hours <= 0) {
        val seconds = minusHours(hours).minusMinutes(minutes).toSeconds()
        if (seconds > 0) append("${seconds}s ")
        if (minutes <= 0) {
            val milliseconds = minusHours(hours).minusMinutes(minutes).minusSeconds(seconds).toMillis()
            if (milliseconds > 0) append("${milliseconds}ms ")
        }
    }
}
