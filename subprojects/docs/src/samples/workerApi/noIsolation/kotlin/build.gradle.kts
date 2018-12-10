// tag::unit-of-work[]

import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

// The implementation of a single unit of work
open class ReverseFile @Inject constructor(val fileToReverse: File, val destinationFile: File) : Runnable {

    override fun run() {
        destinationFile.writeText(fileToReverse.readText().reversed())
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
        // Create and submit a unit of work for each file
        source.forEach { file ->
            workerExecutor.submit(ReverseFile::class) {
                // Use the minimum level of isolation
                isolationMode = IsolationMode.NONE

                // Constructor parameters for the unit of work implementation
                params(file, project.file("$outputDir/${file.name}"))
            }
        }
    }
}
// end::task-implementation[]

tasks.register<ReverseFiles>("reverseFiles") {
    outputDir = file("$buildDir/reversed")
    source("sources")
}
