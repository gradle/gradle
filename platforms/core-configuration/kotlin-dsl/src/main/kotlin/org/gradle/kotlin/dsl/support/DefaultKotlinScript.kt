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
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.logging.LoggingManager
import org.gradle.api.resources.ResourceHandler
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.scripts.ScrubbableScript
import org.gradle.kotlin.dsl.*
import java.io.File
import java.net.URI


/**
 * Common implementation of [the Kotlin script API][KotlinScript] on top of a few services provided by
 * a suitable [host][Host].
 */
open class DefaultKotlinScript internal constructor(
    host: Host
) : KotlinScript, ScrubbableScript {

    internal
    interface Host {
        fun getLogger(): Logger
        fun getLogging(): LoggingManager
        fun getFileOperations(): FileOperations
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
        fileOperations.configurableFiles(*paths)

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

    private
    val fileOperations by unsafeLazy(host::getFileOperations)
}


internal
fun defaultKotlinScriptHostForProject(project: Project): DefaultKotlinScript.Host =
    object : DefaultKotlinScript.Host {
        override fun getLogger(): Logger = project.logger
        override fun getLogging(): LoggingManager = project.logging
        override fun getFileOperations(): FileOperations = (project as ProjectInternal).fileOperations
    }


internal
fun defaultKotlinScriptHostForSettings(settings: Settings): DefaultKotlinScript.Host =
    object : DefaultKotlinScript.Host {
        override fun getLogger(): Logger = Logging.getLogger(Settings::class.java)
        override fun getLogging(): LoggingManager = settings.serviceOf()
        override fun getFileOperations(): FileOperations = fileOperationsFor(settings)
    }


internal
fun defaultKotlinScriptHostForGradle(gradle: Gradle): DefaultKotlinScript.Host =
    object : DefaultKotlinScript.Host {
        override fun getLogger(): Logger = Logging.getLogger(Gradle::class.java)
        override fun getLogging(): LoggingManager = gradle.serviceOf()
        override fun getFileOperations(): FileOperations = fileOperationsFor(gradle, null)
    }


/**
 * Builds the [DefaultKotlinScript.Host] for a compiled script by sourcing its services from the
 * [host] (a [KotlinScriptHost]) rather than from the raw target. Because [KotlinScriptHost] is an
 * interface, this keeps script construction free of the target's service graph — tests can supply a
 * mock host, and there is a single seam through which the services flow.
 *
 * The services are read lazily, not eagerly: [DefaultKotlinScript]'s delegates only pull a service
 * when the script actually uses it, or when the configuration cache serializes the (`Serializable`)
 * delegate and its `writeReplace` forces the value. So a script that never touches `file(...)`,
 * `logger` etc. and is never stored never resolves them — which is what keeps lightweight,
 * partial service registries (test evaluators) working. On a cache store the forced value is
 * captured and the closure holding this `host` is dropped, so nothing reaches the build model. See
 * #22879.
 *
 * (The `defaultKotlinScriptHostFor*` functions above are the same lazy shape but source their
 * services from a raw target — used by precompiled script plugins, which are applied as plain
 * `Plugin`s and have no [KotlinScriptHost].)
 */
internal
fun scriptHostServicesFor(host: KotlinScriptHost<*>): DefaultKotlinScript.Host =
    object : DefaultKotlinScript.Host {
        override fun getLogger(): Logger = host.logger
        override fun getLogging(): LoggingManager = host.logging
        override fun getFileOperations(): FileOperations = host.fileOperations
    }
