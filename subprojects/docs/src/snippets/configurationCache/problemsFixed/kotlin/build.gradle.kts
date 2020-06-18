import javax.inject.Inject

abstract class MyExecTask : DefaultTask() { // <1>

    @get:Input
    lateinit var message: String

    @get:Inject
    abstract val execOperations: ExecOperations // <2>

    @TaskAction
    fun action() {
        execOperations.exec {
            executable("echo")
            args(message)
        }
    }
}

tasks.register<MyExecTask>("someTask") {
    message = providers.systemProperty("someMessage").forUseAtConfigurationTime().get() // <3>
}
