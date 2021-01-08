// tag::task-type[]
abstract class SomeTask : DefaultTask() {

    @get:Inject abstract val fs: FileSystemOperations // <1>

    @TaskAction
    fun action() {
        fs.copy {
            from("source")
            into("destination")
        }
    }
}
// end::task-type[]

tasks.register<SomeTask>("someTaskType")

// tag::ad-hoc-task[]
interface Injected {
    @get:Inject val fs: FileSystemOperations // <1>
}
tasks.register("someTask") {
    val injected = project.objects.newInstance<Injected>() // <2>
    doLast {
        injected.fs.copy { // <3>
            from("source")
            into("destination")
        }
    }
}
// end::ad-hoc-task[]
