// tag::avoid-this[]
abstract class VersionTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val outputFile = outputDirectory.file("build_version.txt")
        outputFile.get().asFile.writeText(project.version.toString()) // <1>
    }
}

tasks.register<VersionTask>("generateVersionFile") {
    outputDirectory.set(project.layout.buildDirectory)
}
// end::avoid-this[]
