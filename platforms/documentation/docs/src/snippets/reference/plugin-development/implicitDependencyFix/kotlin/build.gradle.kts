plugins {
    java
}

abstract class Consumer : DefaultTask() {

    @get:InputFiles
    abstract val inputFile: ConfigurableFileCollection

    @TaskAction
    fun run() {
        println("input files: ${inputFile.files.size}")
    }
}

// tag::from-archive-file[]
tasks.register<Consumer>("consumerA") {
    inputFile.from(tasks.jar.flatMap { it.archiveFile })
}
// end::from-archive-file[]

// tag::from-task[]
tasks.register<Consumer>("consumerB") {
    inputFile.from(tasks.jar)
}
// end::from-task[]

// tag::depends-on[]
tasks.register<Consumer>("consumerC") {
    dependsOn(tasks.jar)
    inputFile.from(tasks.jar.get().archiveFile)
}
// end::depends-on[]
