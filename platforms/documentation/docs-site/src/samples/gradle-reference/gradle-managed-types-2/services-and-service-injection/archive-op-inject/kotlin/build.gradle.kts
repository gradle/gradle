abstract class MyArchiveOperationsTask
@Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val layout: ProjectLayout,
    private val fs: FileSystemOperations
) : DefaultTask() {
    @TaskAction
    fun doTaskAction() {
        fs.sync {
            from(archiveOperations.zipTree(layout.projectDirectory.file("sources.jar")))
            into(layout.buildDirectory.dir("unpacked-sources"))
        }
    }
}

tasks.register("myInjectedArchiveOperationsTask", MyArchiveOperationsTask::class)
