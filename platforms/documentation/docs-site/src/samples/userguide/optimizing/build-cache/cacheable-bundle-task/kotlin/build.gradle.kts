plugins {
    base
}

// Fake NPM task that would normally execute npm with its provided arguments
abstract class NpmTask : DefaultTask() {

    open val args = project.objects.listProperty<String>()

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
        bundle = projectLayout.buildDirectory.file("bundle.js")
        scripts = projectLayout.projectDirectory.dir("scripts")
        configFiles.from(projectLayout.projectDirectory.file("package.json"))
        configFiles.from(projectLayout.projectDirectory.file("package-lock.json"))
    }
}

tasks.register<BundleTask>("bundle")
// end::bundle-task[]

tasks.register("printBundle") {
    dependsOn("bundle")

    val projectLayout: ProjectLayout = layout

    doLast {
        println(projectLayout.buildDirectory.file("bundle.js").get().asFile.readText())
    }
}
