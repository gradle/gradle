import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor

abstract class UrlProcess : DefaultTask() {
    // Use an abstract property
    // Note that the @Inject annotation must be attached to the getter
    @get:Inject
    abstract val objectFactory: ObjectFactory

    // Alternatively, use a property getter with a dummy implementation
    // Note that the property must be open and the @Inject annotation must be attached to the getter
    @get:Inject
    open val workerExecutor: WorkerExecutor
        get() {
            // Getter body is ignored
            throw UnsupportedOperationException()
        }

    @TaskAction
    fun run() {
        // Use the executor and factory ...
    }
}
