abstract class MyWorkAction : WorkAction<WorkParameters.None> {
    private val greeting: String = "Hello from a Worker!"

    override fun execute() {
        println(greeting)
    }
}

abstract class MyWorkerTask
@Inject constructor(private var workerExecutor: WorkerExecutor) : DefaultTask() {
    @get:Input
    abstract val booleanFlag: Property<Boolean>
    @TaskAction
    fun doThings() {
        workerExecutor.noIsolation().submit(MyWorkAction::class.java) {}
    }
}

tasks.register("myWorkTask", MyWorkerTask::class) {}
