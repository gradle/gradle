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

package org.gradle.kotlin.dsl

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.PathValidation
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileTree
import org.gradle.api.logging.Logger
import org.gradle.api.logging.LoggingManager
import org.gradle.api.resources.ResourceHandler
import org.gradle.api.tasks.WorkResult
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import java.io.File
import java.net.URI


/**
 * Base contract for all Gradle Kotlin DSL scripts.
 *
 * This is the Kotlin flavored equivalent of [org.gradle.api.Script].
 *
 * It is not implemented directly by the IDE script templates to overcome ambiguous conflicts and Kotlin language
 * limitations.
 *
 * @since 6.0
 */
@Incubating
interface KotlinScript {

    /**
     * Logger for scripts. You can use this in your script to write log messages.
     */
    val logger: Logger

    /**
     * The [LoggingManager] which can be used to receive logging and to control the standard output/error capture for
     * this script. By default, `System.out` is redirected to the Gradle logging system at the `QUIET` log level,
     * and `System.err` is redirected at the `ERROR` log level.
     */
    val logging: LoggingManager

    /**
     * Provides access to resource-specific utility methods, for example factory methods that create various resources.
     */
    val resources: ResourceHandler

    /**
     * Returns the relative path from this script's target base directory to the given path.
     *
     * The given path object is (logically) resolved as described for [file],
     * from which a relative path is calculated.
     *
     * @param path The path to convert to a relative path.
     * @return The relative path.
     */
    fun relativePath(path: Any): String

    /**
     * Resolves a file path to a URI, relative to this script's target base directory.
     *
     * Evaluates the provided path object as described for [file],
     * with the exception that any URI scheme is supported, not just `file:` URIs.
     */
    fun uri(path: Any): URI

    /**
     * Resolves a file path relative to this script's target base directory.
     *
     * If this script targets [org.gradle.api.Project],
     * then `path` is resolved relative to the project directory.
     *
     * If this script targets [org.gradle.api.initialization.Settings],
     * then `path` is resolved relative to the build root directory.
     *
     * Otherwise the file is resolved relative to the script itself.
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
    fun file(path: Any): File

    /**
     * Resolves a file path relative to this script's target base directory.
     *
     * @param path The object to resolve as a `File`.
     * @param validation The validation to perform on the file.
     * @return The resolved file.
     * @see file
     */
    fun file(path: Any, validation: PathValidation): File

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
    fun files(vararg paths: Any): ConfigurableFileCollection

    /**
     * Creates a [ConfigurableFileCollection] containing the given files.
     *
     * @param paths The contents of the file collection. Evaluated as per [files].
     * @param configuration The block to use to configure the file collection.
     * @return The file collection.
     * @see files
     */
    fun files(paths: Any, configuration: Action<ConfigurableFileCollection>): ConfigurableFileCollection

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
    fun fileTree(baseDir: Any): ConfigurableFileTree

    /**
     * Creates a new [ConfigurableFileTree] using the given base directory.
     *
     * @param baseDir The base directory of the file tree. Evaluated as per [file].
     * @param configuration The block to use to configure the file tree.
     * @return The file tree.
     * @see [fileTree]
     */
    fun fileTree(baseDir: Any, configuration: Action<ConfigurableFileTree>): ConfigurableFileTree

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
    fun zipTree(zipPath: Any): FileTree

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
    fun tarTree(tarPath: Any): FileTree

    /**
     * Copies the specified files.
     *
     * @param configuration The block to use to configure the [CopySpec].
     * @return `WorkResult` that can be used to check if the copy did any work.
     */
    fun copy(configuration: Action<CopySpec>): WorkResult

    /**
     * Creates a {@link CopySpec} which can later be used to copy files or create an archive.
     *
     * @return The created [CopySpec]
     */
    fun copySpec(): CopySpec

    /**
     * Creates a {@link CopySpec} which can later be used to copy files or create an archive.
     *
     * @param configuration The block to use to configure the [CopySpec].
     * @return The configured [CopySpec]
     */
    fun copySpec(configuration: Action<CopySpec>): CopySpec

    /**
     * Creates a directory and returns a file pointing to it.
     *
     * @param path The path for the directory to be created. Evaluated as per [file].
     * @return The created directory.
     * @throws org.gradle.api.InvalidUserDataException If the path points to an existing file.
     */
    fun mkdir(path: Any): File

    /**
     * Deletes files and directories.
     *
     * This will not follow symlinks. If you need to follow symlinks too use [delete].
     *
     * @param paths Any type of object accepted by [file]
     * @return true if anything got deleted, false otherwise
     */
    fun delete(vararg paths: Any): Boolean

    /**
     * Deletes the specified files.
     *
     * @param configuration The block to use to configure the [DeleteSpec].
     * @return `WorkResult` that can be used to check if delete did any work.
     */
    fun delete(configuration: Action<DeleteSpec>): WorkResult

    /**
     * Executes an external command.
     *
     * This method blocks until the process terminates, with its result being returned.
     *
     * @param configuration The block to use to configure the [ExecSpec].
     * @return The result of the execution.
     */
    fun exec(configuration: Action<ExecSpec>): ExecResult

    /**
     * Executes an external Java process.
     *
     * This method blocks until the process terminates, with its result being returned.
     *
     * @param configuration The block to use to configure the [JavaExecSpec].
     * @return The result of the execution.
     */
    fun javaexec(configuration: Action<JavaExecSpec>): ExecResult
}
