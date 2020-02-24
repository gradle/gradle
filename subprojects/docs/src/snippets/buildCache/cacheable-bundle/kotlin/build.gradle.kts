plugins {
    base
}

// Fake NPM task that would normally execute npm with its provided arguments
open class NpmTask : DefaultTask() {

    @get:Input
    val args = project.objects.listProperty<String>()

    @TaskAction
    fun run() {
        project.file("${project.buildDir}/bundle.js").outputStream().use { stream ->
            project.file("scripts").listFiles().sorted().forEach {
                stream.write(it.readBytes())
            }
        }
    }
}

// tag::bundle-task[]
tasks.register<NpmTask>("bundle") {
    args.set(listOf("run", "bundle"))

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
    doLast {
        println(file("$buildDir/bundle.js").readText())
    }
}
