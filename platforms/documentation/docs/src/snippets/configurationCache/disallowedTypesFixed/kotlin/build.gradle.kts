plugins {
    java
}

// tag::task-type[]
abstract class SomeTask : DefaultTask() {

    @get:InputFiles @get:Classpath
    abstract val classpath: ConfigurableFileCollection // <1>

    @TaskAction
    fun action() {
        val classpathFiles = classpath.files
        // ...
    }
}
// end::task-type[]

tasks.register<SomeTask>("someTaskType") {
    classpath.from(sourceSets.main.get().compileClasspath)
}

// tag::ad-hoc-task[]
tasks.register("someTask") {
    val classpath = sourceSets.main.get().compileClasspath // <1>
    doLast {
        val classpathFiles = classpath.files
    }
}
// end::ad-hoc-task[]
