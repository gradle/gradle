// Fake NPM task that would normally execute npm with its provided arguments
open class NpmTask : DefaultTask() {

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
task<NpmTask>("bundle") {
    args.set(listOf("run", "bundle"))

    inputs.dir(file("scripts"))
    inputs.files("package.json", "package-lock.json")

    outputs.file("$buildDir/bundle.js")
}
// end::bundle-task[]

task("printBundle") {
    dependsOn("bundle")
    doLast {
        println(file("$buildDir/bundle.js").readText())
    }
}
