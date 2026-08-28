// tag::avoid[]
abstract class StampBuildTask : DefaultTask() {
    @get:Input
    abstract val buildNumber: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun run() {
        output.get().asFile.writeText("Build ${buildNumber.get()}")
    }
}

tasks.register<StampBuildTask>("stampBuild") {
    // Eager: reads the env var at configuration time; still tracked correctly, but re-runs configuration whenever the value changes
    buildNumber = System.getenv("BUILD_NUMBER") ?: "local"
    output = layout.buildDirectory.file("build-stamp.txt")
}
// end::avoid[]
