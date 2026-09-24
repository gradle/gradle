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

// tag::do-this[]
abstract class Report : DefaultTask() {
    @get:Input
    abstract val messages: ListProperty<String> // <1>

    @TaskAction
    fun report() {
        messages.get().forEach { logger.lifecycle(it) }
    }
}

val report = tasks.register<Report>("report") {
    messages.add("Project: ${project.name}")
}

report.configure {
    messages.add("Built with Gradle") // <2>
    messages.add(writeVersion.flatMap { it.versionFile }
        .map { "Version: ${it.asFile.readText()}" }) // <3>
}
// end::do-this[]
