import javax.inject.Inject

abstract class MyExecTask : DefaultTask() {

    @get:Input
    abstract val message: Property<String> // <1>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun action() {
        execOperations.exec {
            executable("echo")
            args(message.get())
        }
    }
}

tasks.register<MyExecTask>("someTask") {
    message.set(providers.systemProperty("someMessage")) // <2>
}
