import java.util.concurrent.ConcurrentHashMap

// tag::task-lifecycle-listeners[]
abstract class TaskTimingsService : BuildService<BuildServiceParameters.None> {
    private val timings = ConcurrentHashMap<String, Long>() // <1>

    fun record(path: String, nanos: Long) {
        timings[path] = nanos
    }
}

abstract class TaskTimingListener : TaskLifecycleListener<Task> {
    @get:ServiceReference("taskTimings")
    abstract val timings: Property<TaskTimingsService> // <2>

    private var startNanos = 0L // <3>

    override fun beforeExecution(task: Task) {
        startNanos = System.nanoTime()
    }

    override fun afterExecution(task: Task, state: TaskState) { // <4>
        timings.get().record(task.path, System.nanoTime() - startNanos)
    }
}

abstract class CopyReporter : TaskLifecycleListener<Copy> {
    override fun afterExecution(task: Copy, state: TaskState) {
        println("${task.name} copied files to ${task.destinationDir.name}")
    }
}

gradle.sharedServices.registerIfAbsent("taskTimings", TaskTimingsService::class.java)

tasks.configureEach {
    addLifecycleListener(TaskTimingListener::class.java) // <5>
}

tasks.withType<Copy>().addLifecycleListener(CopyReporter::class.java) // <6>
// end::task-lifecycle-listeners[]

tasks.register<Copy>("copyDocs") {
    from("src/docs")
    into(layout.buildDirectory.dir("docs"))
}
