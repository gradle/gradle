plugins {
    java
}

// tag::task-type[]
abstract class SomeTask : DefaultTask() {

    @get:Input lateinit var sourceSet: SourceSet // <1>

    @TaskAction
    fun action() {
        val classpathFiles = sourceSet.compileClasspath.files
        // ...
    }
}
// end::task-type[]

tasks.register<SomeTask>("someTaskType") {
    sourceSet = sourceSets.main.get()
}

// tag::ad-hoc-task[]
tasks.register("someTask") {
    doLast {
        val classpathFiles = sourceSets.main.get().compileClasspath.files // <1>
    }
}
// end::ad-hoc-task[]
