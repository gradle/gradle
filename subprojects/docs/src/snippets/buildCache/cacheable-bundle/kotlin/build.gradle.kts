plugins {
    base
}

// Fake NPM task that would normally execute npm with its provided arguments
abstract class NpmTask : DefaultTask() {

    @get:Input
    val args = project.objects.listProperty<String>()

    @get:Inject
    abstract val projectLayout: ProjectLayout

    @TaskAction
    fun run() {
        val bundleFile = projectLayout.buildDirectory.file("bundle.js").get().asFile
        val scriptsFiles = projectLayout.projectDirectory.dir("scripts").asFile.listFiles()

        bundleFile.outputStream().use { stream ->
            scriptsFiles.sorted().forEach {
                stream.write(it.readBytes())
            }
        }
    }
}

// tag::bundle-task[]
tasks.register<NpmTask>("bundle") {
    args = listOf("run", "bundle")

    outputs.cacheIf { true }

    inputs.dir(file("scripts"))
        .withPropertyName("scripts")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    inputs.files("package.json", "package-lock.json")
        .withPropertyName("configFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    outputs.file("$buildDir/bundle.js")
        .withPropertyName("bundle")
}
// end::bundle-task[]

tasks.register("printBundle") {
    dependsOn("bundle")

    val projectLayout = layout

    doLast {
        println(projectLayout.buildDirectory.file("bundle.js").get().asFile.readText())
    }
}
