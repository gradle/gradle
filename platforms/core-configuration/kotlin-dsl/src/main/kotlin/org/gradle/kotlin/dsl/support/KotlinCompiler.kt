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

package org.gradle.kotlin.dsl.support

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.HasImplicitReceiver
import org.gradle.api.JavaVersion
import org.gradle.api.SupportsKotlinAssignmentOverloading
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.util.internal.CollectionUtils
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames
import org.jetbrains.kotlin.buildtools.api.CompilationResult
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinLogger
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.arguments.CommonCompilerArguments.Companion.API_VERSION
import org.jetbrains.kotlin.buildtools.api.arguments.CommonCompilerArguments.Companion.COMPILER_PLUGINS
import org.jetbrains.kotlin.buildtools.api.arguments.CommonCompilerArguments.Companion.LANGUAGE_VERSION
import org.jetbrains.kotlin.buildtools.api.arguments.CommonCompilerArguments.Companion.X_ALLOW_ANY_SCRIPTS_IN_SOURCE_ROOTS
import org.jetbrains.kotlin.buildtools.api.arguments.CommonCompilerArguments.Companion.X_SKIP_METADATA_VERSION_CHECK
import org.jetbrains.kotlin.buildtools.api.arguments.CommonCompilerArguments.Companion.X_SKIP_PRERELEASE_CHECK
import org.jetbrains.kotlin.buildtools.api.arguments.CommonCompilerArguments.Companion.X_USE_FIR_LT
import org.jetbrains.kotlin.buildtools.api.arguments.CompilerPlugin
import org.jetbrains.kotlin.buildtools.api.arguments.CompilerPluginOption
import org.jetbrains.kotlin.buildtools.api.arguments.ExperimentalCompilerArgument
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.CLASSPATH
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.JVM_DEFAULT
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.JVM_TARGET
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.MODULE_NAME
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.NO_REFLECT
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.NO_STDLIB
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.SCRIPT_TEMPLATES
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.X_ALLOW_UNSTABLE_DEPENDENCIES
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.X_JSR305
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.X_SAM_CONVERSIONS
import org.jetbrains.kotlin.buildtools.api.jvm.JvmPlatformToolchain.Companion.jvm
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmCompilationOperation
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmCompilationOperation.CompilerArgumentsLogLevel
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.JvmTarget.JVM_1_8
import org.jetbrains.kotlin.config.JvmTarget.JVM_25
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverPluginNames
import org.jetbrains.kotlin.scripting.compiler.plugin.KOTLIN_SCRIPTING_PLUGIN_ID
import org.slf4j.Logger
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

private const val MODULE_NAME = "buildscript"


@OptIn(ExperimentalBuildToolsApi::class)
private class Compiler {

    // TODO: this should be done in an isolated classloader and then we can load an
    //  implementation with a different version than the API we are using, thus making it configurable to users
    //  supported versions range from -3 major version to +1 major version
    private val toolchains = KotlinToolchains.loadImplementation(this::class.java.classLoader)

    // TODO: session should be closed after no longer needed, for cleanup to happen
    private val buildSession = toolchains.createBuildSession()

    @OptIn(ExperimentalCompilerArgument::class)
    fun compile(
        sources: List<Path>,
        destinationDirectory: Path,
        logger: Logger,
        arguments: (JvmCompilerArguments.Builder) -> Unit,
    ): CompilationResult {
        val operationBuilder = toolchains.jvm.jvmCompilationOperationBuilder(sources, destinationDirectory)

        // compilation operation config
        operationBuilder[JvmCompilationOperation.COMPILER_ARGUMENTS_LOG_LEVEL] = CompilerArgumentsLogLevel.DEBUG
        // TODO: incremental compilation should make explicit fingerprint checking obsolete

        arguments.invoke(operationBuilder.compilerArguments)

        val operation = operationBuilder.build()
        // TODO operation[JvmCompilationOperation.COMPILER_MESSAGE_RENDERER] = ... will be the proper replacement for MessageCollector

        // TODO: executeOperation has an overload with configurable ExecutionPolicy, that's how Deamon mode can be enabled
        return buildSession.executeOperation(operation, toolchains.createInProcessExecutionPolicy(), CompilationLogger(logger))
    }
}

private val compiler by lazy { Compiler() }


@ExperimentalCompilerArgument
internal
fun compileKotlinScriptToDirectory(
    outputDirectory: File,
    compilerOptions: KotlinCompilerOptions,
    scriptFile: File,
    template: KClass<out Any>,
    classPath: List<File>,
    logger: Logger,
    pathTranslation: (String) -> String
): String {
    fun configureClasspath(arguments: JvmCompilerArguments.Builder, classPath: List<File>) {
        arguments[NO_STDLIB] = true // Don't automatically include the Kotlin/JVM stdlib and Kotlin reflection dependencies in the classpath.
        arguments[NO_REFLECT] = true // Don't automatically include the Kotlin reflection dependency in the classpath. // TODO: is it really covered by NO_STDLIB?
        arguments[CLASSPATH] = CollectionUtils.join(File.pathSeparator, classPath)
    }

    fun configurePlugins(arguments: JvmCompilerArguments.Builder, classPath: List<File>) {
        arguments[COMPILER_PLUGINS] = listOf(
            CompilerPlugin(
                pluginId = KOTLIN_SCRIPTING_PLUGIN_ID,
                classpath = listOf(classPath.first { it.name.contains("kotlin-scripting-compiler-embeddable") }.toPath()),
                rawArguments = listOf(),
                orderingRequirements = setOf()
            ),
            CompilerPlugin(
                pluginId = SamWithReceiverPluginNames.PLUGIN_ID,
                classpath = listOf(classPath.first { it.name.contains("kotlin-sam-with-receiver-compiler-plugin") }.toPath()),
                rawArguments = listOf(CompilerPluginOption(SamWithReceiverPluginNames.ANNOTATION_OPTION_NAME, HasImplicitReceiver::class.qualifiedName!!)),
                orderingRequirements = setOf()
            ),
            CompilerPlugin(
                pluginId = AssignmentPluginNames.PLUGIN_ID,
                classpath = listOf(classPath.first { it.name.contains("kotlin-assignment-compiler-plugin-embeddable") }.toPath()),
                rawArguments = listOf(CompilerPluginOption(AssignmentPluginNames.ANNOTATION_OPTION_NAME, SupportsKotlinAssignmentOverloading::class.qualifiedName!!)),
                orderingRequirements = setOf()
            ),
        )
    }

    println("pathTranslation $pathTranslation ignored for now") // TODO

    val compilationResult = compiler.compile(listOf(Path(scriptFile.path)), outputDirectory.toPath(), logger) {
        // TODO: put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
        it[X_ALLOW_ANY_SCRIPTS_IN_SOURCE_ROOTS] = true
        it[X_USE_FIR_LT] = false
        it[JVM_TARGET] = org.jetbrains.kotlin.buildtools.api.arguments.enums.JvmTarget.valueOf("JVM_" + compilerOptions.jvmTarget.toKotlinJvmTarget().description) // TODO: ugly conversion
        it[X_SAM_CONVERSIONS] = "class"
        // TODO: addJvmSdkRoot(...)

        it.also { // apply language version settings
            it[LANGUAGE_VERSION] = org.jetbrains.kotlin.buildtools.api.arguments.enums.KotlinVersion.V2_2
            it[API_VERSION] = org.jetbrains.kotlin.buildtools.api.arguments.enums.KotlinVersion.V2_2
            it.also { // apply analysis flags
                it[X_SKIP_METADATA_VERSION_CHECK] = compilerOptions.skipMetadataVersionCheck
                it[X_SKIP_PRERELEASE_CHECK] = true
                it[X_ALLOW_UNSTABLE_DEPENDENCIES] = true
                it[JVM_DEFAULT] = "enable"
                it.also { // apply java type enhancement settings
                    it[X_JSR305] = arrayOf("strict", "under-migration:strict")
                    // TODO: not sure what the equivalent of `getReportLevelForAnnotation` is... maybe JvmCompilerArguments.X_JSPECIFY_ANNOTATIONS, but that defaults to the right value
                }
            }
        }

        it[MODULE_NAME] = org.gradle.kotlin.dsl.support.MODULE_NAME

        configureClasspath(it, classPath)
        configurePlugins(it, classPath)

        it[SCRIPT_TEMPLATES] = arrayOf(template.jvmName)
    }
    println("compilationResult $compilationResult ignored for now") // TODO

    return NameUtils.getScriptNameForFile(scriptFile.name).asString()
}


@VisibleForTesting
fun JavaVersion.toKotlinJvmTarget(): JvmTarget {
    // JvmTarget.fromString(JavaVersion.majorVersion) works from Java 9 to Java 25
    return JvmTarget.fromString(majorVersion)
        ?: if (this <= JavaVersion.VERSION_1_8) JVM_1_8
        else JVM_25
}


@OptIn(K1Deprecation::class)
internal
fun disposeKotlinCompilerContext() =
    KotlinCoreEnvironment.disposeApplicationEnvironment()


internal
data class ScriptCompilationError(val message: String, val location: CompilerMessageSourceLocation?)


internal
data class ScriptCompilationException(private val scriptCompilationErrors: List<ScriptCompilationError>) : RuntimeException() {

    val errors: List<ScriptCompilationError> by unsafeLazy {
        scriptCompilationErrors.filter { it.location == null } +
            scriptCompilationErrors.filter { it.location != null }
                .sortedBy { it.location!!.line }
    }

    init {
        require(scriptCompilationErrors.isNotEmpty())
    }

    val firstErrorLine
        get() = errors.asSequence().mapNotNull { it.location?.line }.firstOrNull()

    override val message: String
        get() = (
            listOf("Script compilation $errorPlural:")
                + indentedErrorMessages()
                + "${errors.size} $errorPlural"
            )
            .joinToString("\n\n")

    private
    fun indentedErrorMessages() =
        errors.map { prependIndent(errorMessage(it)) }

    private
    fun errorMessage(error: ScriptCompilationError): String =
        error.location?.let { location ->
            errorAt(location, error.message)
        } ?: error.message

    private
    fun errorAt(location: CompilerMessageSourceLocation, message: String): String {
        val columnIndent = " ".repeat(5 + maxLineNumberStringLength + 1 + location.column)
        return "Line ${lineNumber(location)}: ${location.lineContent}\n" +
            "^ $message".lines().joinToString(
                prefix = columnIndent,
                separator = "\n$columnIndent  $INDENT"
            )
    }

    private
    fun lineNumber(location: CompilerMessageSourceLocation) =
        location.line.toString().padStart(maxLineNumberStringLength, '0')

    private
    fun prependIndent(it: String) = it.prependIndent(INDENT)

    private
    val errorPlural
        get() = if (errors.size > 1) "errors" else "error"

    private
    val maxLineNumberStringLength: Int by lazy {
        errors.mapNotNull { it.location?.line }.maxOrNull()?.toString()?.length ?: 0
    }
}


private
const val INDENT = "  "


private
class CompilationLogger(val log: Logger) : KotlinLogger {
    // TODO: MessageCollector does more, how much to duplicate?

    override fun debug(msg: String) {
        log.debug(msg)
    }

    override fun error(msg: String, throwable: Throwable?) {
        log.error(msg, throwable)
    }

    override fun info(msg: String) {
        log.info(msg)
    }

    override fun lifecycle(msg: String) {
        log.info(msg) // TODO: right level?
    }

    override fun warn(msg: String, throwable: Throwable?) {
        log.warn(msg)
    }

    override val isDebugEnabled: Boolean
        get() = log.isDebugEnabled
}


internal
fun compilerMessageFor(path: String, line: Int, column: Int, message: String) =
    "${clickableFileUrlFor(path)}:$line:$column: $message"


private
fun clickableFileUrlFor(path: String): String =
    ConsoleRenderer().asClickableFileUrl(File(path))
