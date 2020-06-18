import javax.inject.Inject

// tag::task-type[]
abstract class SomeTask : DefaultTask() {

    @get:Inject abstract val execOperations: ExecOperations // <1>

    @TaskAction
    fun action() {
        execOperations.exec {
            commandLine("echo", "hello")
        }
    }
}
// end::task-type[]

tasks.register<SomeTask>("someTaskType")

// tag::ad-hoc-task[]
interface Injected {
    @get:Inject val execOperations: ExecOperations // <1>
}
tasks.register("someTask") {
    val injected = project.objects.newInstance<Injected>() // <2>
    doLast {
        injected.execOperations.exec { // <3>
            commandLine("echo", "hello")
        }
    }
}
// end::ad-hoc-task[]
