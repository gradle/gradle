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

package org.gradle.kotlin.dsl.support

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.file.DefaultFileOperations
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.logging.LoggingManager
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.scripts.ScrubbableScript
import org.gradle.internal.service.ServiceRegistry
import org.gradle.kotlin.dsl.accessors.ProjectAccessorsClassPathGenerator
import org.gradle.kotlin.dsl.execution.KotlinMetadataCompatibilityChecker
import org.gradle.kotlin.dsl.invoke
import org.gradle.util.internal.ConfigureUtil.configureByMap
import java.io.File
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo


/**
 * The services and context a compiled Kotlin script needs from its target (`Project`/`Settings`/
 * `Gradle`).
 *
 * This is an interface (implemented by [DefaultKotlinScriptHost]) so that the configuration cache
 * can replace it with a broken proxy when scrubbing a captured script: every member reaches the live
 * build model or a build-scoped service, none of which survive serialization. See #22879 and
 * `ScrubbableScriptCodec`. At execution time on a scrubbed script the only reachable member is
 * `scriptHandler` (via the `buildscript`/`initscript` accessors), which then fails with a clear
 * configuration-cache problem instead of a `NullPointerException`.
 */
interface KotlinScriptHost<out T : Any> : ScrubbableScript.ScrubbedOut {
    val target: T
    val scriptSource: ScriptSource
    val scriptHandler: ScriptHandler
    val targetScope: ClassLoaderScope
    val originalScriptPath: String
    val buildTreeScriptPath: String
    val fileOperations: FileOperations
    val logger: Logger
    val logging: LoggingManager
    val processOperations: ProcessOperations
    val objectFactory: ObjectFactory
    val temporaryFileProvider: TemporaryFileProvider
    val metadataCompatibilityChecker: KotlinMetadataCompatibilityChecker
    val projectAccessorsClassPathGenerator: ProjectAccessorsClassPathGenerator
    fun applyObjectConfigurationAction(configure: Action<in ObjectConfigurationAction>)
    fun applyObjectConfigurationAction(options: Map<String, *>)
}


@Suppress("LongParameterList")
class DefaultKotlinScriptHost<out T : Any> internal constructor(
    override val target: T,
    override val scriptSource: ScriptSource,
    override val scriptHandler: ScriptHandler,
    override val targetScope: ClassLoaderScope,
    private val baseScope: ClassLoaderScope,
    private val buildTreeRootDir: Path,
    private val serviceRegistry: ServiceRegistry
) : KotlinScriptHost<T> {

    override val originalScriptPath = scriptSource.fileName!!

    override val buildTreeScriptPath: String by unsafeLazy {
        val location = scriptSource.resource.location
        location.file?.toPath()?.toAbsolutePath()
            ?.let { path ->
                // Relative path inside the build-tree root
                if (path.startsWith(buildTreeRootDir)) path.relativeTo(buildTreeRootDir).invariantSeparatorsPathString
                // Absolute path outside the build-tree root, non-relocatable
                else path.invariantSeparatorsPathString
            }
            ?: location.uri?.toASCIIString() // URI, relocatable when external
            ?: scriptSource.className // Generated class name fallback, non-relocatable
    }

    override val fileOperations: FileOperations by unsafeLazy {
        fileOperationsFor(serviceRegistry, scriptSource.resource.location.file?.parentFile)
    }

    override val logger: Logger by unsafeLazy {
        when (val t = target) {
            is Project -> t.logger
            is Settings -> Logging.getLogger(Settings::class.java)
            is Gradle -> Logging.getLogger(Gradle::class.java)
            else -> Logging.getLogger(t.javaClass)
        }
    }

    override val logging: LoggingManager by unsafeLazy {
        serviceRegistry.get()
    }

    override val processOperations: ProcessOperations by unsafeLazy {
        serviceRegistry.get()
    }

    override val objectFactory: ObjectFactory by unsafeLazy {
        serviceRegistry.get()
    }

    override val temporaryFileProvider: TemporaryFileProvider by unsafeLazy {
        // GradleUserHomeTemporaryFileProvider must be used instead of the TemporaryFileProvider.
        // In this scope the TemporaryFileProvider would be provided by the ProjectScopeServices.
        // That would generate this temporary directory inside the project build directory.
        serviceRegistry.get<GradleUserHomeTemporaryFileProvider>()
    }

    override val metadataCompatibilityChecker: KotlinMetadataCompatibilityChecker by unsafeLazy {
        serviceRegistry.get<KotlinMetadataCompatibilityChecker>()
    }

    override val projectAccessorsClassPathGenerator: ProjectAccessorsClassPathGenerator
        get() = serviceRegistry.get<ProjectAccessorsClassPathGenerator>()

    override fun applyObjectConfigurationAction(configure: Action<in ObjectConfigurationAction>) {
        executeObjectConfigurationAction { configure(it) }
    }

    override fun applyObjectConfigurationAction(options: Map<String, *>) {
        executeObjectConfigurationAction { configureByMap(options, it) }
    }

    private
    inline fun executeObjectConfigurationAction(configure: (ObjectConfigurationAction) -> Unit) {
        createObjectConfigurationAction().also(configure).execute()
    }

    private
    fun createObjectConfigurationAction() =
        DefaultObjectConfigurationAction(
            fileOperations.fileResolver,
            serviceRegistry.get(),
            serviceRegistry.get(),
            baseScope,
            serviceRegistry.get(),
            target
        )
}


internal
fun fileOperationsFor(settings: Settings): FileOperations =
    fileOperationsFor(settings.gradle, settings.rootDir)


internal
fun fileOperationsFor(gradle: Gradle, baseDir: File?): FileOperations =
    fileOperationsFor((gradle as GradleInternal).services, baseDir)


internal
fun fileOperationsFor(services: ServiceRegistry, baseDir: File?): FileOperations {
    // A script with a known base dir gets a ScriptFileOperations, so the configuration cache can
    // preserve that base dir across the cache instead of re-resolving it from the owner. See #22879.
    if (baseDir != null) {
        return DefaultFileOperations.forScript(services, baseDir)
    }
    val fileLookup = services.get<FileLookup>()
    val fileResolver = fileLookup.fileResolver
    val fileCollectionFactory = services.get<FileCollectionFactory>().withResolver(fileResolver)
    return DefaultFileOperations.createSimple(
        fileResolver,
        fileCollectionFactory,
        services
    )
}
