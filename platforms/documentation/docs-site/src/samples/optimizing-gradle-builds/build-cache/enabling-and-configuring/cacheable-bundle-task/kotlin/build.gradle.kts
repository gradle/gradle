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
