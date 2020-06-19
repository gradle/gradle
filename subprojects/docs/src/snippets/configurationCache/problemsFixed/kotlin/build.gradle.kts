import javax.inject.Inject

// tag::fixed[]
abstract class MyCopyTask : DefaultTask() { // <1>

    @get:InputDirectory abstract val source: DirectoryProperty

    @get:OutputDirectory abstract val destination: DirectoryProperty

    @get:Inject abstract val fs: FileSystemOperations // <2>

    @TaskAction
    fun action() {
        fs.copy {
            from(source)
            into(destination)
        }
    }
}

tasks.register<MyCopyTask>("someTask") {
    val projectDir = layout.projectDirectory
    source.set(projectDir.dir("source"))
    destination.set(projectDir.dir(
        providers.systemProperty("someDestination").forUseAtConfigurationTime().get() // <2>
    ))
}
// end::fixed[]
