
// The parameters for a single unit of work
interface ReverseParameters : WorkParameters {
    val fileToReverse : RegularFileProperty
    val destinationDir : DirectoryProperty
}

// The implementation of a single unit of work
abstract class ReverseFile @Inject constructor(val fileSystemOperations: FileSystemOperations) : WorkAction<ReverseParameters> {
    override fun execute() {
        fileSystemOperations.copy {
            from(parameters.fileToReverse)
            into(parameters.destinationDir)
            filter { line: String -> line.reversed() }
        }
    }
}

// The WorkerExecutor will be injected by Gradle at runtime
abstract class ReverseFiles @Inject constructor(
    private val projectLayout: ProjectLayout,
    private val workerExecutor: WorkerExecutor
) : SourceTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun reverseFiles() {
        // tag::wait-for-completion[]
        // Create a WorkQueue to submit work items
        val workQueue = workerExecutor.noIsolation()

        // Create and submit a unit of work for each file
        source.forEach { file ->
            workQueue.submit(ReverseFile::class) {
                fileToReverse = file
                destinationDir = outputDir
            }
        }

        // Wait for all asynchronous work submitted to this queue to complete before continuing
        workQueue.await()
        logger.lifecycle("Created ${outputDir.get().asFile.listFiles().size} reversed files in ${outputDir.get().asFile.toRelativeString(projectLayout.projectDirectory.asFile)}")
        // end::wait-for-completion[]
    }
}

tasks.register<ReverseFiles>("reverseFiles") {
    outputDir = layout.buildDirectory.dir("reversed")
    source("sources")
}
