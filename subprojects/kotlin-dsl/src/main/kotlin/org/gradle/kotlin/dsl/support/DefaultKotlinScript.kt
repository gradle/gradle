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

package org.gradle.kotlin.dsl.support

import org.gradle.api.Action
import org.gradle.api.PathValidation
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileTree
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logger
import org.gradle.api.logging.LoggingManager
import org.gradle.api.resources.ResourceHandler
import org.gradle.api.tasks.WorkResult
import org.gradle.kotlin.dsl.*
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec

import java.io.File
import java.net.URI


/**
 * Common implementation of [the Kotlin script API][KotlinScript] on top of a few services provided by
 * a suitable [host][Host].
 */
open class DefaultKotlinScript internal constructor(
    host: Host
) : KotlinScript {

    internal
    interface Host {
        fun getLogger(): Logger
        fun getLogging(): LoggingManager
        fun getFileOperations(): FileOperations
        fun getProcessOperations(): ProcessOperations
    }

    override val logging: LoggingManager by unsafeLazy(host::getLogging)

    override val logger: Logger by unsafeLazy(host::getLogger)

    override val resources: ResourceHandler by unsafeLazy { fileOperations.resources }

    override fun relativePath(path: Any): String =
        fileOperations.relativePath(path)

    override fun uri(path: Any): URI =
        fileOperations.uri(path)

    override fun file(path: Any): File =
        fileOperations.file(path)

    override fun file(path: Any, validation: PathValidation): File =
        fileOperations.file(path, validation)

    override fun files(vararg paths: Any): ConfigurableFileCollection =
        fileOperations.configurableFiles(paths)

    override fun files(paths: Any, configuration: Action<ConfigurableFileCollection>): ConfigurableFileCollection =
        fileOperations.configurableFiles(paths).also(configuration::execute)

    override fun fileTree(baseDir: Any): ConfigurableFileTree =
        when (baseDir) {
            is Map<*, *> -> {
                @Suppress("unchecked_cast")
                fileOperations.fileTree(baseDir as Map<String, *>)
            }
            else -> fileOperations.fileTree(baseDir)
        }

    override fun fileTree(baseDir: Any, configuration: Action<ConfigurableFileTree>): ConfigurableFileTree =
        fileOperations.fileTree(baseDir).also(configuration::execute)

    override fun zipTree(zipPath: Any): FileTree =
        fileOperations.zipTree(zipPath)

    override fun tarTree(tarPath: Any): FileTree =
        fileOperations.tarTree(tarPath)

    override fun copy(configuration: Action<CopySpec>): WorkResult =
        fileOperations.copy(configuration)

    override fun copySpec(): CopySpec =
        fileOperations.copySpec()

    override fun copySpec(configuration: Action<CopySpec>): CopySpec =
        copySpec().also(configuration::execute)

    override fun mkdir(path: Any): File =
        fileOperations.mkdir(path)

    override fun delete(vararg paths: Any): Boolean =
        fileOperations.delete(*paths)

    override fun delete(configuration: Action<DeleteSpec>): WorkResult =
        fileOperations.delete(configuration)

    override fun exec(configuration: Action<ExecSpec>): ExecResult =
        processOperations.exec(configuration)

    override fun javaexec(configuration: Action<JavaExecSpec>): ExecResult =
        processOperations.javaexec(configuration)

    private
    val fileOperations by unsafeLazy(host::getFileOperations)

    private
    val processOperations by unsafeLazy(host::getProcessOperations)
}


internal
fun defaultKotlinScriptHostForProject(project: Project): DefaultKotlinScript.Host =
    ProjectScriptHost(project)


private
class ProjectScriptHost(val project: Project) : DefaultKotlinScript.Host {
    override fun getLogger(): Logger = project.logger
    override fun getLogging(): LoggingManager = project.logging
    override fun getFileOperations(): FileOperations = projectInternal().fileOperations
    override fun getProcessOperations(): ProcessOperations = projectInternal().processOperations
    fun projectInternal() = (project as ProjectInternal)
}
