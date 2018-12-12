import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

// The implementation of a single unit of work
open class ReverseFile @Inject constructor(val fileToReverse: File, val destinationFile: File) : Runnable {

    override fun run() {
        destinationFile.writeText(fileToReverse.readText().reversed())
    }
}

open class ReverseFiles @Inject constructor(val workerExecutor: WorkerExecutor) : SourceTask() {
    @OutputDirectory
    lateinit var outputDir: File

    @TaskAction
    fun reverseFiles() {
        // tag::wait-for-completion[]
        // Create and submit a unit of work for each file
        source.forEach { file ->
            workerExecutor.submit(ReverseFile::class) {
                isolationMode = IsolationMode.NONE
                // Constructor parameters for the unit of work implementation
                params(file, project.file("$outputDir/${file.name}"))
            }
        }

        // Wait for all asynchronous work to complete before continuing
        workerExecutor.await()
        logger.lifecycle("Created ${outputDir.listFiles().size} reversed files in ${project.relativePath(outputDir)}")
        // end::wait-for-completion[]
    }
}

tasks.register<ReverseFiles>("reverseFiles") {
    outputDir = file("$buildDir/reversed")
    source("sources")
}
