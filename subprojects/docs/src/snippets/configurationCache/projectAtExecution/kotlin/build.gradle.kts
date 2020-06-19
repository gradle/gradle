// tag::task-type[]
abstract class SomeTask : DefaultTask() {
    @TaskAction
    fun action() {
        project.copy { // <1>
            from("source")
            into("destination")
        }
    }
}
// end::task-type[]

tasks.register<SomeTask>("someTaskType")

// tag::ad-hoc-task[]
tasks.register("someTask") {
    doLast {
        project.copy { // <1>
            from("source")
            into("destination")
        }
    }
}
// end::ad-hoc-task[]
