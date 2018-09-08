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
package org.gradle.api

import groovy.lang.Closure
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileTree
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.logging.Logger
import org.gradle.api.logging.LoggingManager
import org.gradle.api.provider.Provider
import org.gradle.api.resources.ResourceHandler
import org.gradle.api.tasks.WorkResult
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import java.io.File
import java.net.URI
import java.util.concurrent.Callable

/**
 *
 * This interface is implemented by all Gradle scripts to add in some Gradle-specific methods. As your compiled
 * script class will implement this interface, you can use the methods and properties declared by this interface
 * directly in your script.
 *
 *
 * Generally, a `Script` object will have a delegate object attached to it. For example, a build script will
 * have a [Project] instance attached to it, and an initialization script will have a
 * [org.gradle.api.invocation.Gradle] instance attached to it. Any property reference or method call which is not found
 * on this `Script` object is forwarded to the delegate object.
 */
interface Script {

    /**
     * Returns the script handler for this script. You can use this handler to manage the classpath used to compile and
     * execute this script.
     *
     * @return the classpath handler. Never returns null.
     */
    val buildscript: ScriptHandler

    /**
     * Returns the [org.gradle.api.logging.LoggingManager] which can be used to receive logging and to control the
     * standard output/error capture for this script. By default, [System.out] is redirected to the Gradle logging system
     * at the QUIET log level, and [System.err] is redirected at the ERROR log level.
     *
     * @return the LoggingManager. Never returns null.
     */
    val logging: LoggingManager

    /**
     * Returns the logger for this script. You can use this in your script to write log messages.
     *
     * @return The logger. Never returns null.
     */
    val logger: Logger

    /**
     * Provides access to resource-specific utility methods, for example factory methods that create various resources.
     *
     * @return Returned instance contains various resource-specific utility methods.
     */
    val resources: ResourceHandler

    /**
     *
     * Configures the delegate object for this script using plugins or scripts.
     *
     *
     * The given closure is used to configure an [org.gradle.api.plugins.ObjectConfigurationAction] which is
     * then used to configure the delegate object.
     *
     * @param closure The closure to configure the `ObjectConfigurationAction`.
     */
    fun apply(closure: Closure<*>)

    /**
     *
     * Configures the delegate object for this script using plugins or scripts.
     *
     *
     * The following options are available:
     *
     *  * `from`: A script to apply to the delegate object. Accepts any path supported by [Script.uri].
     *
     *  * `plugin`: The id or implementation class of the plugin to apply to the delegate object.
     *
     *  * `to`: The target delegate object or objects.
     *
     *
     * For more detail, see [org.gradle.api.plugins.ObjectConfigurationAction].
     *
     * @param options The options to use to configure the `ObjectConfigurationAction`.
     */
    fun apply(options: Map<String, *>)

    /**
     * Configures the classpath for this script.
     *
     *
     * The given closure is executed against this script's [ScriptHandler]. The [ScriptHandler] is passed
     * to the closure as the closure's delegate.
     *
     * @param configureClosure the closure to use to configure the script classpath.
     */
    fun buildscript(configureClosure: Closure<*>)

    /**
     *
     * Resolves a file path relative to the directory containing this script. This works as described for [Project.file]
     *
     * @param path The object to resolve as a File.
     * @return The resolved file. Never returns null.
     */
    fun file(path: Any): File

    /**
     *
     * Resolves a file path relative to the directory containing this script and validates it using the given scheme.
     * See [PathValidation] for the list of possible validations.
     *
     * @param path An object to resolve as a File.
     * @param validation The validation to perform on the file.
     * @return The resolved file. Never returns null.
     * @throws InvalidUserDataException When the file does not meet the given validation constraint.
     */
    @Throws(InvalidUserDataException::class)
    fun file(path: Any, validation: PathValidation): File

    /**
     *
     * Resolves a file path to a URI, relative to the directory containing this script. Evaluates the provided path
     * object as described for [file], with the exception that any URI scheme is supported, not just
     * 'file:' URIs.
     *
     * @param path The object to resolve as a URI.
     * @return The resolved URI. Never returns null.
     */
    fun uri(path: Any): URI

    /**
     *
     * Returns a [ConfigurableFileCollection] containing the given files. This works as described for [Project.files].
     * Relative paths are resolved relative to the directory containing this script.
     *
     * @param paths The paths to the files. May be empty.
     * @return The file collection. Never returns null.
     */
    fun files(vararg paths: Any): ConfigurableFileCollection

    /**
     *
     * Creates a new `ConfigurableFileCollection` using the given paths. The file collection is configured
     * using the given closure. This method works as described for [Project.files].
     * Relative paths are resolved relative to the directory containing this script.
     *
     * @param paths The contents of the file collection. Evaluated as per [Script.files].
     * @param configureClosure The closure to use to configure the file collection.
     * @return the configured file tree. Never returns null.
     */
    fun files(paths: Any, configureClosure: Closure<*>): ConfigurableFileCollection

    /**
     *
     * Returns the relative path from the directory containing this script to the given path. The given path object
     * is (logically) resolved as described for [file], from which a relative path is calculated.
     *
     * @param path The path to convert to a relative path.
     * @return The relative path. Never returns null.
     */
    fun relativePath(path: Any): String

    /**
     *
     * Creates a new `ConfigurableFileTree` using the given base directory. The given baseDir path is evaluated
     * as per [file].
     *
     *
     * The returned file tree is lazy, so that it scans for files only when the contents of the file tree are
     * queried. The file tree is also live, so that it scans for files each time the contents of the file tree are
     * queried.
     *
     * @param baseDir The base directory of the file tree. Evaluated as per [file].
     * @return the file tree. Never returns null.
     */
    fun fileTree(baseDir: Any): ConfigurableFileTree

    /**
     *
     * Creates a new `ConfigurableFileTree` using the provided map of arguments.  The map will be applied as
     * properties on the new file tree.  Example:
     * ```groovy
     * fileTree(dir:'src', excludes:['**&#47;ignore&#47**','**&#47;.svn&#47**'])
     * ```
     *
     * The returned file tree is lazy, so that it scans for files only when the contents of the file tree are
     * queried. The file tree is also live, so that it scans for files each time the contents of the file tree are
     * queried.
     *
     * @param args map of property assignments to `ConfigurableFileTree` object
     * @return the configured file tree. Never returns null.
     */
    fun fileTree(args: Map<String, *>): ConfigurableFileTree

    /**
     *
     * Creates a new `ConfigurableFileTree` using the given base directory. The given baseDir path is evaluated
     * as per [file]. The closure will be used to configure the new file tree.
     * The file tree is passed to the closure as its delegate.  Example:
     *
     * ```groovy
     * fileTree('src') {
     *     exclude '**&#47;.svn&#47;**'
     * }.copy { into 'dest'}*
     * ```
     *
     * The returned file tree is lazy, so that it scans for files only when the contents of the file tree are
     * queried. The file tree is also live, so that it scans for files each time the contents of the file tree are
     * queried.
     *
     * @param baseDir The base directory of the file tree. Evaluated as per [.file].
     * @param configureClosure Closure to configure the `ConfigurableFileTree` object.
     * @return the configured file tree. Never returns null.
     */
    fun fileTree(baseDir: Any, configureClosure: Closure<*>): ConfigurableFileTree

    /**
     *
     * Creates a new [FileTree] which contains the contents of the given ZIP file. The given zipPath path is
     * evaluated as per [file]. You can combine this method with the [copy]
     * method to unzip a ZIP file.
     *
     *
     * The returned file tree is lazy, so that it scans for files only when the contents of the file tree are
     * queried. The file tree is also live, so that it scans for files each time the contents of the file tree are
     * queried.
     *
     * @param zipPath The ZIP file. Evaluated as per [file].
     * @return the file tree. Never returns null.
     */
    fun zipTree(zipPath: Any): FileTree

    /**
     * Creates a new [FileTree] which contains the contents of the given TAR file. The given tarPath path can be:
     *
     *  * an instance of [org.gradle.api.resources.Resource]
     *  * any other object is evaluated as per [file]
     *
     *
     * The returned file tree is lazy, so that it scans for files only when the contents of the file tree are
     * queried. The file tree is also live, so that it scans for files each time the contents of the file tree are
     * queried.
     *
     *
     * Unless custom implementation of resources is passed, the tar tree attempts to guess the compression based on the file extension.
     *
     *
     * You can combine this method with the [copy]
     * method to untar a TAR file:
     *
     * ```
     * task untar(type: Copy) {
     *     from tarTree('someCompressedTar.gzip')
     *
     *     // tar tree attempts to guess the compression based on the file extension
     *     // however if you must specify the compression explicitly you can:
     *     from tarTree(resources.gzip('someTar.ext'))
     *
     *     // in case you work with unconventionally compressed tars
     *     // you can provide your own implementation of a ReadableResource:
     *     // from tarTree(yourOwnResource as ReadableResource)
     *
     *     into 'dest'
     * }
     * ```
     *
     * @param tarPath The TAR file or an instance of [org.gradle.api.resources.Resource].
     * @return the file tree. Never returns null.
     */
    fun tarTree(tarPath: Any): FileTree

    /**
     * Copy the specified files.  The given closure is used to configure a [org.gradle.api.file.CopySpec], which
     * is then used to copy the files. Example:
     *
     * ```groovy
     * copy {
     *    from configurations.runtime
     *    into 'build/deploy/lib'
     * }
     * ```
     *
     * Note that CopySpecs can be nested:
     * ```groovy
     * copy {
     *     into 'build/webroot'
     *     exclude '**&#47;.svn&#47;**'
     *     from('src/main/webapp') {
     *         include '**&#47;*.jsp'
     *         filter(ReplaceTokens, tokens:[copyright:'2009', version:'2.3.1'])
     *     }
     *     from('src/main/js') {
     *        include '**&#47;*.js'
     *     }
     * }
     * ```
     *
     * @param closure Closure to configure the CopySpec
     * @return [org.gradle.api.tasks.WorkResult] that can be used to check if the copy did any work.
     */
    fun copy(closure: Closure<*>): WorkResult

    /**
     * Creates a [org.gradle.api.file.CopySpec] which can later be used to copy files or create an archive. The
     * given closure is used to configure the [org.gradle.api.file.CopySpec] before it is returned by this
     * method.
     *
     * @param closure Closure to configure the CopySpec
     * @return The CopySpec
     */
    fun copySpec(closure: Closure<*>): CopySpec

    /**
     * Creates a directory and returns a file pointing to it.
     *
     * @param path The path for the directory to be created. Evaluated as per [.file].
     * @return the created directory
     * @throws org.gradle.api.InvalidUserDataException If the path points to an existing file.
     */
    fun mkdir(path: Any): File

    /**
     * Deletes files and directories.
     *
     * @param paths Any type of object accepted by [org.gradle.api.Project.files]
     * @return true if anything got deleted, false otherwise
     */
    fun delete(vararg paths: Any): Boolean

    /**
     * Executes a Java main class. The closure configures a [org.gradle.process.JavaExecSpec].
     *
     * @param closure The closure for configuring the execution.
     * @return the result of the execution
     */
    fun javaexec(closure: Closure<*>): ExecResult

    /**
     * Executes a Java main class.
     *
     * @param action The action for configuring the execution.
     * @return the result of the execution
     */
    fun javaexec(action: Action<in JavaExecSpec>): ExecResult

    /**
     * Executes an external command. The closure configures a [org.gradle.process.ExecSpec].
     *
     * @param closure The closure for configuring the execution.
     * @return the result of the execution
     */
    fun exec(closure: Closure<*>): ExecResult

    /**
     * Executes an external command.
     *
     * @param action The action for configuring the execution.
     * @return the result of the execution
     */
    fun exec(action: Action<in ExecSpec>): ExecResult

    /**
     * Creates a `Provider` implementation based on the provided value.
     *
     * @param value The `java.util.concurrent.Callable` use to calculate the value.
     * @return The provider. Never returns null.
     * @throws org.gradle.api.InvalidUserDataException If the provided value is null.
     * @see org.gradle.api.provider.ProviderFactory.provider
     * @since 4.0
     */
    @Incubating
    fun <T> provider(value: Callable<T>): Provider<T>
}
