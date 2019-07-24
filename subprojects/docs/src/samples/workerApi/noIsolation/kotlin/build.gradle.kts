// tag::unit-of-work[]

import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

// The parameters for a single unit of work
interface ReverseParameters : WorkParameters {
    val fileToReverse : Property<File>
    val destinationFile : Property<File>
}

// The implementation of a single unit of work
abstract class ReverseFile : WorkAction<ReverseParameters> {
    override fun execute() {
        getParameters().destinationFile.get().writeText(getParameters().fileToReverse.get().readText().reversed())
    }
}
// end::unit-of-work[]

// tag::task-implementation[]
// The WorkerExecutor will be injected by Gradle at runtime
open class ReverseFiles @Inject constructor(val workerExecutor: WorkerExecutor) : SourceTask() {
    @OutputDirectory
    lateinit var outputDir: File

    @TaskAction
    fun reverseFiles() {
        // Create a WorkQueue to submit work items
        val workQueue = workerExecutor.noIsolation()

        // Create and submit a unit of work for each file
        source.forEach { file ->
            workQueue.submit(ReverseFile::class) {
                fileToReverse.set(file)
                destinationFile.set(project.file("$outputDir/${file.name}"))
            }
        }
    }
}
// end::task-implementation[]

tasks.register<ReverseFiles>("reverseFiles") {
    outputDir = file("$buildDir/reversed")
    source("sources")
}
