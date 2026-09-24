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

// tag::do[]
tasks.register<StampBuildTask>("stampBuild") {
    buildNumber = providers.environmentVariable("BUILD_NUMBER").orElse("local") // <1> <2>
    output = layout.buildDirectory.file("build-stamp.txt")
}
// end::do[]
