// tag::setup[]
abstract class WriteVersion : DefaultTask() { // <1>
    @get:OutputFile
    abstract val versionFile: RegularFileProperty

    @TaskAction
    fun write() {
        versionFile.get().asFile.writeText("1.0")
    }
}

val writeVersion = tasks.register<WriteVersion>("writeVersion") {
    versionFile = layout.buildDirectory.file("version.txt")
}
// end::setup[]

// tag::avoid-this[]
abstract class Report : DefaultTask() {
    @get:Input
    abstract val messages: Property<Collection<String>> // <1>

    @TaskAction
    fun report() {
        messages.get().forEach { logger.lifecycle(it) }
    }
}

val report = tasks.register<Report>("report") {
    messages = listOf("Project: ${project.name}")
}

report.configure {
    messages = messages.get() + "Built with Gradle" // <2>
    messages = writeVersion.flatMap { it.versionFile }
        .map { listOf("Version: ${it.asFile.readText()}") } // <3>
}
// end::avoid-this[]
