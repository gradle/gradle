/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.kotlin.dsl

import org.gradle.api.Action
import org.gradle.api.PathValidation
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileTree
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.file.DefaultFileOperations
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.logging.LoggingManager
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.resources.ResourceHandler
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.service.ServiceRegistry
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.delegates.SettingsDelegate
import org.gradle.kotlin.dsl.support.get
import org.gradle.kotlin.dsl.support.internalError
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.support.unsafeLazy
import org.gradle.plugin.management.PluginManagementSpec
import org.gradle.plugin.use.PluginDependenciesSpec

import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import java.io.File
import java.net.URI
import kotlin.script.extensions.SamWithReceiverAnnotations
import kotlin.script.templates.ScriptTemplateAdditionalCompilerArguments
import kotlin.script.templates.ScriptTemplateDefinition


/**
 * Base class for Kotlin settings scripts.
 */
@ScriptTemplateDefinition(
    resolver = KotlinBuildScriptDependenciesResolver::class,
    scriptFilePattern = "(?:.+\\.)?settings\\.gradle\\.kts"
)
@ScriptTemplateAdditionalCompilerArguments(
    [
        "-language-version", "1.4",
        "-api-version", "1.4",
        "-jvm-target", "1.8",
        "-Xjvm-default=all",
        "-Xjsr305=strict",
        "-XXLanguage:+DisableCompatibilityModeForNewInference",
    ]
)
@SamWithReceiverAnnotations("org.gradle.api.HasImplicitReceiver")
@GradleDsl
abstract class KotlinSettingsScript(
    private val host: KotlinScriptHost<Settings>
) : @Suppress("deprecation") SettingsScriptApi(host.target) /* TODO:kotlin-dsl configure implicit receiver */ {

    /**
     * The [ScriptHandler] for this script.
     */
    override fun getBuildscript(): ScriptHandler =
        host.scriptHandler

    override val fileOperations
        get() = host.fileOperations

    override val processOperations
        get() = host.processOperations

    override fun apply(action: Action<in ObjectConfigurationAction>) =
        host.applyObjectConfigurationAction(action)

    /**
     * Configures the plugin dependencies for the project's settings.
     *
     * @see [PluginDependenciesSpec]
     * @since 6.0
     */
    @Suppress("unused")
    open fun plugins(@Suppress("unused_parameter") block: PluginDependenciesSpecScope.() -> Unit): Unit =
        throw Exception(
            "The plugins {} block must not be used here. "
                + "If you need to apply a plugin imperatively, please use apply<PluginType>() or apply(plugin = \"id\") instead."
        )
}


/**
 * Standard implementation of the API exposed to all types of [Settings] scripts,
 * precompiled and otherwise.
 */
@Deprecated(
    "Kept for compatibility with precompiled script plugins published with Gradle versions prior to 6.0",
    replaceWith = ReplaceWith("Settings", "org.gradle.api.initialization.Settings")
)
abstract class SettingsScriptApi(
    override val delegate: Settings
) : SettingsDelegate() {

    protected
    abstract val fileOperations: FileOperations

    protected
    abstract val processOperations: ProcessOperations

    /**
     * Logger for settings. You can use this in your settings file to write log messages.
     */
    @Suppress("unused")
    val logger: Logger by unsafeLazy { Logging.getLogger(Settings::class.java) }

    /**
     * The [LoggingManager] which can be used to receive logging and to control the standard output/error capture for
     * this script. By default, `System.out` is redirected to the Gradle logging system at the `QUIET` log level,
     * and `System.err` is redirected at the `ERROR` log level.
     */
    @Suppress("unused")
    val logging by unsafeLazy { settings.serviceOf<LoggingManager>() }

    /**
     * Provides access to resource-specific utility methods, for example factory methods that create various resources.
     */
    @Suppress("unused")
    val resources: ResourceHandler by unsafeLazy { fileOperations.resources }

    /**
     * Returns the relative path from this script's target base directory to the given path.
     *
     * The given path object is (logically) resolved as described for [file],
     * from which a relative path is calculated.
     *
     * @param path The path to convert to a relative path.
     * @return The relative path.
     */
    @Suppress("unused")
    fun relativePath(path: Any): String =
        fileOperations.relativePath(path)

    /**
     * Resolves a file path to a URI, relative to this script's target base directory.
     *
     * Evaluates the provided path object as described for [file],
     * with the exception that any URI scheme is supported, not just `file:` URIs.
     */
    @Suppress("unused")
    fun uri(path: Any): URI =
        fileOperations.uri(path)

    /**
     * Resolves a file path relative to this script's target base directory.
     *
     * If this script targets [org.gradle.api.Project],
     * then `path` is resolved relative to the project directory.
     *
     * If this script targets [org.gradle.api.initialization.Settings],
     * then `path` is resolved relative to the build root directory.
     *
     * This method converts the supplied path based on its type:
     *
     * - A [CharSequence], including [String].
     *   A string that starts with `file:` is treated as a file URL.
     * - A [File].
     *   If the file is an absolute file, it is returned as is. Otherwise it is resolved.
     * - A [java.nio.file.Path].
     *   The path must be associated with the default provider and is treated the same way as an instance of `File`.
     * - A [URI] or [java.net.URL].
     *   The URL's path is interpreted as the file path. Only `file:` URLs are supported.
     * - A [org.gradle.api.file.Directory] or [org.gradle.api.file.RegularFile].
     * - A [org.gradle.api.provider.Provider] of any supported type.
     *   The provider's value is resolved recursively.
     * - A [java.util.concurrent.Callable] that returns any supported type.
     *   The callable's return value is resolved recursively.
     *
     * @param path The object to resolve as a `File`.
     * @return The resolved file.
     */
    @Suppress("unused")
    fun file(path: Any): File =
        fileOperations.file(path)

    /**
     * Resolves a file path relative to this script's target base directory.
     *
     * @param path The object to resolve as a `File`.
     * @param validation The validation to perform on the file.
     * @return The resolved file.
     * @see file
     */
    @Suppress("unused")
    fun file(path: Any, validation: PathValidation): File =
        fileOperations.file(path, validation)

    /**
     * Creates a [ConfigurableFileCollection] containing the given files.
     *
     * You can pass any of the following types to this method:
     *
     * - A [CharSequence], including [String] as defined by [file].
     * - A [File] as defined by [file].
     * - A [java.nio.file.Path] as defined by [file].
     * - A [URI] or [java.net.URL] as defined by [file].
     * - A [org.gradle.api.file.Directory] or [org.gradle.api.file.RegularFile]
     *   as defined by [file].
     * - A [Sequence], [Array] or [Iterable] that contains objects of any supported type.
     *   The elements of the collection are recursively converted to files.
     * - A [org.gradle.api.file.FileCollection].
     *   The contents of the collection are included in the returned collection.
     * - A [org.gradle.api.provider.Provider] of any supported type.
     *   The provider's value is recursively converted to files. If the provider represents an output of a task,
     *   that task is executed if the file collection is used as an input to another task.
     * - A [java.util.concurrent.Callable] that returns any supported type.
     *   The callable's return value is recursively converted to files.
     *   A `null` return value is treated as an empty collection.
     * - A [org.gradle.api.Task].
     *   Converted to the task's output files.
     *   The task is executed if the file collection is used as an input to another task.
     * - A [org.gradle.api.tasks.TaskOutputs].
     *   Converted to the output files the related task.
     *   The task is executed if the file collection is used as an input to another task.
     * - Anything else is treated as a failure.
     *
     * The returned file collection is lazy, so that the paths are evaluated only when the contents of the file
     * collection are queried. The file collection is also live, so that it evaluates the above each time the contents
     * of the collection is queried.
     *
     * The returned file collection maintains the iteration order of the supplied paths.
     *
     * The returned file collection maintains the details of the tasks that produce the files,
     * so that these tasks are executed if this file collection is used as an input to some task.
     *
     * This method can also be used to create an empty collection, which can later be mutated to add elements.
     *
     * @param paths The paths to the files. May be empty.
     * @return The file collection.
     */
    @Suppress("unused")
    fun files(vararg paths: Any): ConfigurableFileCollection =
        fileOperations.configurableFiles(paths)

    /**
     * Creates a [ConfigurableFileCollection] containing the given files.
     *
     * @param paths The contents of the file collection. Evaluated as per [files].
     * @param configuration The block to use to configure the file collection.
     * @return The file collection.
     * @see files
     */
    @Suppress("unused")
    fun files(paths: Any, configuration: ConfigurableFileCollection.() -> Unit): ConfigurableFileCollection =
        fileOperations.configurableFiles(paths).also(configuration)

    /**
     * Creates a new [ConfigurableFileTree] using the given base directory.
     *
     * The given `baseDir` path is evaluated as per [file].
     *
     * The returned file tree is lazy, so that it scans for files only when the contents of the file tree are
     * queried. The file tree is also live, so that it scans for files each time the contents of the file tree are
     * queried.
     *
     * @param baseDir The base directory of the file tree. Evaluated as per [file].
     * @return The file tree.
     */
    @Suppress("unused")
    fun fileTree(baseDir: Any): ConfigurableFileTree =
        fileOperations.fileTree(baseDir)

    /**
     * Creates a new [ConfigurableFileTree] using the given base directory.
     *
     * @param baseDir The base directory of the file tree. Evaluated as per [file].
     * @param configuration The block to use to configure the file tree.
     * @return The file tree.
     * @see [fileTree]
     */
    @Suppress("unused")
    fun fileTree(baseDir: Any, configuration: ConfigurableFileTree.() -> Unit): ConfigurableFileTree =
        fileOperations.fileTree(baseDir).also(configuration)

    /**
     * Creates a new [FileTree] which contains the contents of the given ZIP file.
     *
     * The given `zipPath` path is evaluated as per [file]
     *
     * The returned file tree is lazy, so that it scans for files only when the contents of the file tree are
     * queried. The file tree is also live, so that it scans for files each time the contents of the file tree are
     * queried.
     *
     * You can combine this method with the [copy] method to unzip a ZIP file.
     *
     * @param zipPath The ZIP file. Evaluated as per [file].
     * @return The file tree.
     */
    @Suppress("unused")
    fun zipTree(zipPath: Any): FileTree =
        fileOperations.zipTree(zipPath)

    /**
     * Creates a new [FileTree] which contains the contents of the given TAR file.
     *
     * The given tarPath path can be:
     * - an instance of [org.gradle.api.resources.Resource],
     * - any other object is evaluated as per [file].
     *
     * The returned file tree is lazy, so that it scans for files only when the contents of the file tree are
     * queried. The file tree is also live, so that it scans for files each time the contents of the file tree are
     * queried.
     *
     * Unless custom implementation of resources is passed,
     * the tar tree attempts to guess the compression based on the file extension.
     *
     * You can combine this method with the [copy] method to unzip a ZIP file.
     *
     * @param tarPath The TAR file or an instance of [org.gradle.api.resources.Resource].
     * @return The file tree.
     */
    @Suppress("unused")
    fun tarTree(tarPath: Any): FileTree =
        fileOperations.tarTree(tarPath)

    /**
     * Copies the specified files.
     *
     * @param configuration The block to use to configure the [CopySpec].
     * @return `WorkResult` that can be used to check if the copy did any work.
     */
    @Suppress("unused")
    fun copy(configuration: CopySpec.() -> Unit): WorkResult =
        fileOperations.copy(configuration)

    /**
     * Creates a {@link CopySpec} which can later be used to copy files or create an archive.
     *
     * @return The created [CopySpec]
     */
    @Suppress("unused")
    fun copySpec(): CopySpec =
        fileOperations.copySpec()

    /**
     * Creates a {@link CopySpec} which can later be used to copy files or create an archive.
     *
     * @param configuration The block to use to configure the [CopySpec].
     * @return The configured [CopySpec]
     */
    @Suppress("unused")
    fun copySpec(configuration: CopySpec.() -> Unit): CopySpec =
        fileOperations.copySpec().also(configuration)

    /**
     * Creates a directory and returns a file pointing to it.
     *
     * @param path The path for the directory to be created. Evaluated as per [file].
     * @return The created directory.
     * @throws org.gradle.api.InvalidUserDataException If the path points to an existing file.
     */
    @Suppress("unused")
    fun mkdir(path: Any): File =
        fileOperations.mkdir(path)

    /**
     * Deletes files and directories.
     *
     * This will not follow symlinks. If you need to follow symlinks too use [delete].
     *
     * @param paths Any type of object accepted by [file]
     * @return true if anything got deleted, false otherwise
     */
    @Suppress("unused")
    fun delete(vararg paths: Any): Boolean =
        fileOperations.delete(*paths)

    /**
     * Deletes the specified files.
     *
     * @param configuration The block to use to configure the [DeleteSpec].
     * @return `WorkResult` that can be used to check if delete did any work.
     */
    @Suppress("unused")
    fun delete(configuration: DeleteSpec.() -> Unit): WorkResult =
        fileOperations.delete(configuration)

    /**
     * Executes an external command.
     *
     * This method blocks until the process terminates, with its result being returned.
     *
     * @param configuration The block to use to configure the [ExecSpec].
     * @return The result of the execution.
     */
    @Suppress("unused")
    fun exec(configuration: ExecSpec.() -> Unit): ExecResult =
        processOperations.exec(configuration)

    /**
     * Executes an external Java process.
     *
     * This method blocks until the process terminates, with its result being returned.
     *
     * @param configuration The block to use to configure the [JavaExecSpec].
     * @return The result of the execution.
     */
    @Suppress("unused")
    fun javaexec(configuration: JavaExecSpec.() -> Unit): ExecResult =
        processOperations.javaexec(configuration)

    /**
     * Configures the plugin management for the entire build.
     *
     * @see [Settings.getPluginManagement]
     * @since 6.0
     */
    @Suppress("unused")
    open fun pluginManagement(@Suppress("unused_parameter") block: PluginManagementSpec.() -> Unit): Unit =
        internalError()

    /**
     * Configures the build script classpath for settings.
     *
     * @see [Settings.getBuildscript]
     */
    @Suppress("unused")
    open fun buildscript(@Suppress("unused_parameter") block: ScriptHandlerScope.() -> Unit): Unit =
        internalError()
}


internal
fun fileOperationsFor(settings: Settings): FileOperations =
    fileOperationsFor(settings.gradle, settings.rootDir)


internal
fun fileOperationsFor(gradle: Gradle, baseDir: File?): FileOperations =
    fileOperationsFor((gradle as GradleInternal).services, baseDir)


internal
fun fileOperationsFor(services: ServiceRegistry, baseDir: File?): FileOperations {
    val fileLookup = services.get<FileLookup>()
    val fileResolver = baseDir?.let { fileLookup.getFileResolver(it) } ?: fileLookup.fileResolver
    val fileCollectionFactory = services.get<FileCollectionFactory>().withResolver(fileResolver)
    return DefaultFileOperations.createSimple(
        fileResolver,
        fileCollectionFactory,
        services
    )
}
