abstract class MyFileSystemOperationsTask
@Inject constructor(private var fileSystemOperations: FileSystemOperations) : DefaultTask() {

    @TaskAction
    fun doTaskAction() {
        fileSystemOperations.sync {
            from("src")
            into("dest")
        }
    }
}

tasks.register("myInjectedFileSystemOperationsTask", MyFileSystemOperationsTask::class)
