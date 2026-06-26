// tag::do-this[]
abstract class VersionTask : DefaultTask() {
    @get:Input
    abstract val version: Property<String> // <1>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        outputDirectory.file("build_version.txt").get().asFile.writeText(version.get())
    }
}

tasks.register<VersionTask>("generateVersionFile") {
    version.set(project.version.toString()) // <2>
    outputDirectory.set(project.layout.buildDirectory.dir("build-info")) // <3>
}
// end::do-this[]
