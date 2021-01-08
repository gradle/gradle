plugins {
    base
}

// Fake NPM task that would normally execute npm with its provided arguments
abstract class NpmTask : DefaultTask() {

    open val args = project.objects.listProperty<String>()

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
@CacheableTask                                       // <1>
abstract class BundleTask : NpmTask() {

    @get:Internal                                    // <2>
    override val args
        get() = super.args


    @get:InputDirectory
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)     // <3>
    abstract val scripts: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)     // <4>
    abstract val configFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val bundle: RegularFileProperty

    init {
        args.addAll("run", "bundle")
        bundle.set(project.layout.buildDirectory.file("bundle.js"))
        scripts.set(project.layout.projectDirectory.dir("scripts"))
        configFiles.from(project.layout.projectDirectory.file("package.json"))
        configFiles.from(project.layout.projectDirectory.file("package-lock.json"))
    }
}

tasks.register<BundleTask>("bundle")
// end::bundle-task[]

tasks.register("printBundle") {
    dependsOn("bundle")
    doLast {
        println(project.layout.buildDirectory.file("bundle.js").get().asFile.readText())
    }
}
