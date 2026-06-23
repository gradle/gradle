abstract class MyCopyTask : DefaultTask() {

    @get:InputDirectory abstract val source: DirectoryProperty

    @get:OutputDirectory abstract val destination: DirectoryProperty

    @get:Inject abstract val fs: FileSystemOperations

    @TaskAction
    fun action() {
        fs.copy {
            from(source)
            into(destination)
        }
    }
}

// tag::fixed-reuse[]
tasks.register<MyCopyTask>("someTask") {
    val projectDir = layout.projectDirectory
    source = projectDir.dir("source")
    destination = projectDir.dir(providers.systemProperty("someDestination")) // <1>
}
// end::fixed-reuse[]
