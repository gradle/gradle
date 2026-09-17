// tag::do[]
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskFailureResult

abstract class FailureCollector :
    BuildService<BuildServiceParameters.None>,
    OperationCompletionListener, // <1>
    AutoCloseable {

    private val failedTasks = mutableListOf<String>() // <3>

    override fun onFinish(event: FinishEvent) { // <1>
        if (event is TaskFinishEvent && event.result is TaskFailureResult) {
            failedTasks.add(event.descriptor.taskPath)
        }
    }

    override fun close() { // <2>
        if (failedTasks.isNotEmpty()) {
            println("Failed tasks: ${failedTasks.joinToString()}")
        }
    }
}

val collector = gradle.sharedServices.registerIfAbsent("failureCollector", FailureCollector::class) {}
val eventListenerRegistry = gradle.serviceOf<BuildEventsListenerRegistry>()
eventListenerRegistry.onTaskCompletion(collector) // <4>
// end::do[]
