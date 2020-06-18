// tag::task-type[]
abstract class SomeTask : DefaultTask() {
    @TaskAction
    fun action() {
        project.exec { // <1>
            commandLine("echo", "hello")
        }
    }
}
// end::task-type[]

tasks.register<SomeTask>("someTaskType")

// tag::ad-hoc-task[]
tasks.register("someTask") {
    doLast {
        project.exec { // <1>
            commandLine("echo", "hello")
        }
    }
}
// end::ad-hoc-task[]
