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

open class CheckSamples : DefaultTask() {

    override fun getDescription() =
        "Checks each sample by running `gradle help` on it."

    @get:InputDirectory
    var installation: File? = null

    @get:InputFiles
    val samples: FileCollection by lazy {
        project.fileTree(project.samplesDir()).apply {
            include("**/*.gradle.kts")
            include("**/gradle-wrapper.properties")
        }
    }

    @get:OutputDirectory
    val outputDir: File by lazy {
        File(project.buildDir, "check-samples")
    }

    @TaskAction
    fun run() {
        withUniqueDaemonRegistry {
            project.sampleDirs().forEach { sampleDir ->
                println("Checking ${relativeFile(sampleDir)}...")
                OutputStreamForResultOf(sampleDir).use { stdout ->
                    runGradleHelpOn(sampleDir, stdout)
                }
            }
        }
    }

    private fun OutputStreamForResultOf(sampleDir: File) =
        File(outputDir, "${sampleDir.name}.txt").outputStream()

    private fun relativeFile(file: File) =
        file.relativeTo(project.projectDir)

    private fun runGradleHelpOn(projectDir: File, stdout: FileOutputStream) {
        withConnectionFrom(connectorFor(projectDir).useInstallation(installation!!)) {
            newBuild()
                .forTasks("help")
                .setStandardOutput(TeeOutputStream(System.out, stdout))
                .setStandardError(TeeOutputStream(System.err, stdout))
                .run()
        }
    }
}
