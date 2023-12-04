// tag::unit-of-work[]

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
// end::unit-of-work[]

// tag::task-implementation[]
// The WorkerExecutor will be injected by Gradle at runtime
abstract class ReverseFiles @Inject constructor(private val workerExecutor: WorkerExecutor) : SourceTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun reverseFiles() {
        // Create a WorkQueue to submit work items
        val workQueue = workerExecutor.noIsolation()

        // Create and submit a unit of work for each file
        source.forEach { file ->
            workQueue.submit(ReverseFile::class) {
                fileToReverse = file
                destinationDir = outputDir
            }
        }
    }
}
// end::task-implementation[]

tasks.register<ReverseFiles>("reverseFiles") {
    outputDir = layout.buildDirectory.dir("reversed")
    source("sources")
}
