// tag::avoid-this[]
abstract class VersionTask : DefaultTask() {

    init {
        outputDirectory.convention(project.layout.buildDirectory) // <1>
    }

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val outFileName = "build_version.txt"
        val outputFile = outputDirectory.file(outFileName)
        outputFile.get().asFile.writeText(project.version.toString()) // <2>
    }
}

tasks.register<VersionTask>("generateVersionFile")
// end::avoid-this[]
