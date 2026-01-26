import javax.inject.Inject

// tag::do-this[]
abstract class VersionTask @Inject constructor(projectLayout: ProjectLayout) : DefaultTask() {
    @get:Input
    abstract val version: Property<String> // <1>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        outputDirectory.convention(projectLayout.buildDirectory) // <2>
    }

    @TaskAction
    fun run() {
        val outFileName = "build_version.txt"
        outputDirectory.file(outFileName).get().asFile.writeText(version.get())
    }
}

tasks.register<VersionTask>("generateVersionFile") {
    version.set(project.version.toString()) // <3>
    outputDirectory.set(layout.buildDirectory.dir("build-info")) // <4>
}
// end::do-this[]
