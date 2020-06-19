import javax.inject.Inject

abstract class MyCopyTask : DefaultTask() {

    @get:InputDirectory abstract val source: DirectoryProperty

    @get:OutputDirectory abstract val destination: DirectoryProperty // <1>

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
    source.set(projectDir.dir("source"))
    destination.set(providers.systemProperty("someDestination").map { path -> // <2>
        projectDir.dir(path)
    })
}
// end::fixed-reuse[]
