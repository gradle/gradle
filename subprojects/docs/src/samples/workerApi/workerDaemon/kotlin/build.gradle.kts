/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        val fileToReverse = parameters.fileToReverse.get()
        getParameters().destinationFile.get().writeText(fileToReverse.readText().reversed())
        if (java.lang.Boolean.getBoolean("org.gradle.sample.showFileSize")) {
            println("Reversed ${fileToReverse.length()} bytes from ${fileToReverse.name}")
        }
    }
}

open class ReverseFiles @Inject constructor(val workerExecutor: WorkerExecutor) : SourceTask() {
    @OutputDirectory
    lateinit var outputDir: File

    @TaskAction
    fun reverseFiles() {
        // tag::worker-daemon[]
        // Create a WorkQueue with process isolation
        val workQueue = workerExecutor.processIsolation() {
            // Configure the options for the forked process
            forkOptions {
                maxHeapSize = "512m"
                systemProperty("org.gradle.sample.showFileSize", "true")
            }
        }

        // Create and submit a unit of work for each file
        source.forEach { file ->
            workQueue.submit(ReverseFile::class) {
                fileToReverse.set(file)
                destinationFile.set(project.file("$outputDir/${file.name}"))
            }
        }
        // end::worker-daemon[]
    }
}

tasks.register<ReverseFiles>("reverseFiles") {
    outputDir = file("$buildDir/reversed")
    source("sources")
}
