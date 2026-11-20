import java.io.ByteArrayOutputStream
import java.io.OutputStream

// tag::task-lifecycle-listeners[]
abstract class ExecFailureDetails : TaskLifecycleListener<Exec> { // <1>
    private val stderr = ByteArrayOutputStream() // <2>

    override fun beforeExecution(task: Exec) { // <3>
        task.errorOutput = object : OutputStream() {
            override fun write(b: Int) {
                System.err.write(b)
                stderr.write(b)
            }
        }
    }

    override fun afterExecution(task: Exec, state: TaskState) { // <4>
        if (state.failure != null) {
            println("Command: ${task.commandLine.joinToString(" ")}")
            println("Working directory: ${task.workingDir}")
            println("Error output:")
            stderr.toString().trim().lines().forEach { println("  $it") }
        }
    }
}

tasks.withType<Exec>().addLifecycleListener(ExecFailureDetails::class.java) // <5>
// end::task-lifecycle-listeners[]

tasks.register<Exec>("runTool") {
    // Runs the JVM of the build with a missing main class, so the task fails the same way on every platform
    executable = ProcessHandle.current().info().command().get()
    args("-cp", "missing", "Tool")
}
