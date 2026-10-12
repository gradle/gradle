group = "org.gradle.samples"
version = "1.0"

// tag::adhoc-avoid[]
tasks.register("writeVersionStamp") {
    val output = layout.buildDirectory.file("version.txt")
    outputs.file(output)
    doLast {
        // BAD: project.version is the live Project model, accessed at execution time
        output.get().asFile.writeText(project.version.toString())
    }
}
// end::adhoc-avoid[]
