group = "org.gradle.samples"
version = "1.0"

// tag::typedTask[]
abstract class WriteVersionStampTask : DefaultTask() {
    @get:Input
    abstract val version: Property<String> // <1>

    @get:OutputFile
    abstract val output: RegularFileProperty // <2>

    @TaskAction
    fun run() {
        output.get().asFile.writeText(version.get())
    }
}

tasks.register<WriteVersionStampTask>("writeVersionStamp") {
    version = providers.provider { project.version.toString() } // <3>
    output = layout.buildDirectory.file("version.txt")
}
// end::typedTask[]
