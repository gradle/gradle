group = "org.gradle.samples"
version = "1.0"

// tag::adhoc-do[]
tasks.register("writeVersionStamp") {
    val versionProvider = providers.provider { project.version.toString() } // <1>
    val output = layout.buildDirectory.file("version.txt")
    outputs.file(output)
    doLast {
        output.get().asFile.writeText(versionProvider.get()) // <2>
    }
}
// end::adhoc-do[]
