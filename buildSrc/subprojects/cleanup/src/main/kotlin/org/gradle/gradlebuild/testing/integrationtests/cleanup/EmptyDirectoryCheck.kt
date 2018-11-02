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
package org.gradle.gradlebuild.testing.integrationtests.cleanup

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.kotlin.dsl.*
import java.io.File
import javax.inject.Inject


/**
 * Ensures that a directory is empty or writes the names of files in
 * the directory to a report file.
 */
open class EmptyDirectoryCheck @Inject constructor(objects: ObjectFactory) : DefaultTask() {

    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:InputFiles
    val targetDirectory: DirectoryProperty = objects.directoryProperty()

    @get:OutputFile
    val reportFile: RegularFileProperty = objects.fileProperty()

    @get:Input
    val policy: Property<WhenNotEmpty> = objects.property()

    @TaskAction
    fun ensureDirectoryEmpty() {
        var notEmpty = false
        val report = reportFile.get().asFile
        val targetDir = targetDirectory.get().asFile
        project.fileTree(targetDir).visit {
            if (!isDirectory) {
                notEmpty = true
                report.appendText(file.absolutePath + "\n")
            }
        }

        if (notEmpty) {
            when (policy.get()) {
                WhenNotEmpty.FAIL -> throw GradleException(createMessage(targetDir, report))
                WhenNotEmpty.REPORT -> logger.warn(createMessage(targetDir, report))
            }
        }
    }

    private
    fun createMessage(targetDir: File, report: File) =
        "The directory $targetDir was not empty. Report: ${ConsoleRenderer().asClickableFileUrl(report)}\n${report.readText()}"
}
