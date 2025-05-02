// tag::fixed[]
abstract class MyCopyTask : DefaultTask() { // <1>

    @get:InputDirectory abstract val source: DirectoryProperty // <2>

    @get:OutputDirectory abstract val destination: DirectoryProperty // <2>

    @get:Inject abstract val fs: FileSystemOperations // <3>

    @TaskAction
    fun action() {
        fs.copy { // <3>
            from(source)
            into(destination)
        }
    }
}

tasks.register<MyCopyTask>("someTask") {
    val projectDir = layout.projectDirectory
    source = projectDir.dir("source")
    destination = projectDir.dir(System.getProperty("someDestination"))
}
// end::fixed[]
