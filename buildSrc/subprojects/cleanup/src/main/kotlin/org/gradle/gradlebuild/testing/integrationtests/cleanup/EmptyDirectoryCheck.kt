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
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.io.File


/**
 * Ensures that a directory is empty or writes the names of files in
 * the directory to a report file.
 */
open class EmptyDirectoryCheck : DefaultTask() {

    @InputFiles
    lateinit var targetDir: FileTree

    @OutputFile
    lateinit var report: File

    @Internal
    var isErrorWhenNotEmpty: Boolean = false

    // TODO Remove this property and move @Input annotation to isErrorWhenNotEmpty
    // once https://github.com/gradle/build-cache/issues/1030 is fixed
    @get:Input
    @Deprecated(
        "See https://github.com/gradle/build-cache/issues/1030",
        ReplaceWith("isErrorWhenNotEmpty"))
    val errorWhenNotEmpty
        get() = isErrorWhenNotEmpty

    @TaskAction
    fun ensureEmptiness() {
        var hasFile = false
        targetDir.visit {
            if (file.isFile) {
                hasFile = true
                report.appendText(file.path + "\n")
            }
        }
        if (hasFile && isErrorWhenNotEmpty) {
            throw GradleException("The directory ${targetDir.asPath} was not empty.")
        }
    }
}
