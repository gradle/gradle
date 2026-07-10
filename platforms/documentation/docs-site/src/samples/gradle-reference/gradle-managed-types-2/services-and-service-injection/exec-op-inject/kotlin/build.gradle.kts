abstract class MyExecOperationsTask
@Inject constructor(private var execOperations: ExecOperations) : DefaultTask() {

    @TaskAction
    fun doTaskAction() {
        execOperations.exec {
            commandLine("ls", "-la")
        }
    }
}

tasks.register("myInjectedExecOperationsTask", MyExecOperationsTask::class)
