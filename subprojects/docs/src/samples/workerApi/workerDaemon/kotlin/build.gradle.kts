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

// The implementation of a single unit of work
open class ReverseFile @Inject constructor(val fileToReverse: File, val destinationFile: File) : Runnable {

    override fun run() {
        destinationFile.writeText(fileToReverse.readText().reversed())
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
        // Create and submit a unit of work for each file
        source.forEach { file ->
            // tag::worker-daemon[]
            workerExecutor.submit(ReverseFile::class) {
                // Run this work in an isolated process
                isolationMode = IsolationMode.PROCESS

                // Configure the options for the forked process
                forkOptions {
                    maxHeapSize = "512m"
                    systemProperty("org.gradle.sample.showFileSize", "true")
                }

                // Constructor parameters for the unit of work implementation
                params(file, project.file("$outputDir/${file.name}"))
            }
            // end::worker-daemon[]
        }
    }
}

tasks.register<ReverseFiles>("reverseFiles") {
    outputDir = file("$buildDir/reversed")
    source("sources")
}
