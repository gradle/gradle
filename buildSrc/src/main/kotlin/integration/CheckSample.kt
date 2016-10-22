/*
 * Copyright 2016 the original author or authors.
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

package integration

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import org.apache.tools.ant.util.TeeOutputStream

import java.io.File
import java.io.FileOutputStream

/**
 * Checks a single sample project.
 */
open class CheckSample() : DefaultTask() {

    var sampleDir: File? = null

    @get:InputDirectory
    var installation: File? = null

    @Suppress("unused")
    @get:InputFiles
    val inputFiles: FileCollection by lazy {
        project.fileTree(sampleDir!!).apply {
            include("**/*.gradle")
            include("**/*.gradle.kts")
        }
    }

    @get:OutputDirectory
    val outputDir: File by lazy {
        File(buildDir, "check-samples")
    }

    @TaskAction
    fun run() {
        withDaemonRegistry(customDaemonRegistry()) {
            check(sampleDir!!)
        }
    }

    private fun customDaemonRegistry() =
        File(buildDir, "custom/daemon-registry")

    private val buildDir: File?
        get() = project.buildDir

    private fun check(sampleDir: File) {
        outputStreamForResultOf(sampleDir).use { stdout ->
            runGradleHelpOn(sampleDir, stdout)
        }
    }

    private fun outputStreamForResultOf(sampleDir: File) =
        File(outputDir, "${sampleDir.name}.txt").outputStream()

    private fun runGradleHelpOn(projectDir: File, stdout: FileOutputStream) {
        withConnectionFrom(connectorFor(projectDir).useInstallation(installation!!)) {
            newBuild()
                .forTasks("tasks")
                .setStandardOutput(TeeOutputStream(System.out, stdout))
                .setStandardError(TeeOutputStream(System.err, stdout))
                .run()
        }
    }
}
