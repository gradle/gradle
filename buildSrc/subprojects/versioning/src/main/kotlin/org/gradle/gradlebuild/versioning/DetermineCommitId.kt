/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.gradlebuild.versioning

import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.internal.ExecActionFactory
import java.io.ByteArrayOutputStream
import javax.inject.Inject


@Suppress("unused")
open class DetermineCommitId @Inject constructor(
    objects: ObjectFactory,
    layout: ProjectLayout,
    private val execActionFactory: ExecActionFactory
) : DefaultTask() {

    @get:OutputFile
    val commitIdFile = objects.fileProperty()
        .convention(layout.buildDirectory.file("determined-commit-id.txt"))

    @get:Internal
    val determinedCommitId: Provider<String>
        get() = commitIdFile.map { it.asFile.readText() }

    init {
        outputs.upToDateWhen { false }
    }

    private
    val promotionCommitId: String? =
        project.findProperty("promotionCommitId")?.toString()

    private
    val rootDir =
        project.rootDir

    @TaskAction
    fun determineCommitId() {
        val commitId = buildStrategies().mapNotNull { it() }.first()
        commitIdFile.get().asFile.writeText(commitId)
    }

    private
    fun buildStrategies(): Sequence<() -> String?> = sequenceOf(
        // For promotion builds use the commitId passed in as a project property
        { promotionCommitId },
        // Builds of Gradle happening on the CI server
        { System.getenv("BUILD_VCS_NUMBER") },
        // For the discovery builds, this points to the Gradle revision
        { firstSystemEnvStartsWithOrNull("BUILD_VCS_NUMBER_Gradle_Master") },
        // For the discovery release builds, this points to the Gradle revision
        { firstSystemEnvStartsWithOrNull("BUILD_VCS_NUMBER_Gradle_release_branch") },
        // If it's a checkout, ask Git for it
        { gitCommitId() },
        // It's a source distribution, we don't know.
        { "<unknown>" }
    )

    private
    fun firstSystemEnvStartsWithOrNull(prefix: String) =
        System.getenv().asSequence().firstOrNull { it.key.startsWith(prefix) }?.value

    private
    fun gitCommitId(): String? {
        val gitDir = rootDir.resolve(".git")
        if (!gitDir.exists()) {
            return null
        }
        val execOutput = ByteArrayOutputStream()
        val execResult = execActionFactory.newExecAction().apply {
            workingDir = rootDir
            isIgnoreExitValue = true
            commandLine = listOf("git", "log", "-1", "--format=%H", "--no-show-signature")
            if (OperatingSystem.current().isWindows) {
                commandLine = listOf("cmd", "/c") + commandLine
            }
            standardOutput = execOutput
        }.execute()
        return when {
            execResult.exitValue == 0 -> String(execOutput.toByteArray()).trim()
            gitDir.resolve("HEAD").exists() -> {
                // Read commit id directly from filesystem
                val headRef = gitDir.resolve("HEAD").readText()
                    .replace("ref: ", "").trim()
                gitDir.resolve(headRef).readText().trim()
            }
            else -> null
        }
    }
}
