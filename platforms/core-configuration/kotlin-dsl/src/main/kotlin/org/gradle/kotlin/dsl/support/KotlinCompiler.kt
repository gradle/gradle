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
import org.gradle.internal.io.NullOutputStream
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.kotlin.dsl.provider.PrecompiledScriptsEnvironment.EnvironmentProperties.kotlinDslImplicitImports
import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames
import org.jetbrains.kotlin.buildtools.api.CompilationResult
import org.jetbrains.kotlin.buildtools.api.CompilerMessageRenderer
import org.jetbrains.kotlin.buildtools.api.CompilerMessageRenderer.Severity
import org.jetbrains.kotlin.buildtools.api.CompilerMessageRenderer.SourceLocation
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
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
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.NO_REFLECT
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.NO_STDLIB
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.SCRIPT_TEMPLATES
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.X_ALLOW_UNSTABLE_DEPENDENCIES
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.X_JSR305
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.X_SAM_CONVERSIONS
import org.jetbrains.kotlin.buildtools.api.arguments.JvmCompilerArguments.Companion.X_SCRIPT_RESOLVER_ENVIRONMENT
import org.jetbrains.kotlin.buildtools.api.arguments.enums.JvmDefaultMode
import org.jetbrains.kotlin.buildtools.api.arguments.enums.SamConversionsMode
import org.jetbrains.kotlin.buildtools.api.jvm.JvmPlatformToolchain.Companion.jvm
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmCompilationOperation
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmCompilationOperation.CompilerArgumentsLogLevel
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.JvmTarget.JVM_1_8
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverPluginNames
import org.jetbrains.kotlin.scripting.compiler.plugin.KOTLIN_SCRIPTING_PLUGIN_ID
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName


private val compiler by lazy { Compiler() }


internal
fun compileKotlinScriptToDirectory(
    outputDirectory: File,
    compilerOptions: KotlinCompilerOptions,
    scriptFile: File,
    implicitImports: List<String>,
    template: KClass<out Any>,
    classPath: List<File>,
    logger: Logger,
    pathTranslation: (String) -> String
): String {
    compileKotlinScriptToDirectory(
        outputDirectory,
        compilerOptions,
        scriptFile,
        implicitImports,
        template,
        classPath,
        messageCollectorFor(logger, compilerOptions.allWarningsAsErrors, pathTranslation)
    )

    return NameUtils.getScriptNameForFile(scriptFile.name).asString()
}


private
fun compileKotlinScriptToDirectory(
    outputDirectory: File,
    compilerOptions: KotlinCompilerOptions,
    scriptFile: File,
    implicitImports: List<String>,
    template: KClass<out Any>,
    classPath: List<File>,
    messageRenderer: LoggingMessageRenderer
) {
    withCompilationExceptionHandler(messageRenderer) {
        val compilationResult = compiler.compile(
            listOf(Path(scriptFile.path)),
            outputDirectory.toPath(),
            compilerOptions,
            classPath,
            template,
            implicitImports,
            messageRenderer
        )
        compilationResult.reportToMessageCollectorAndThrowOnErrors(messageRenderer)
    }
}


private fun CompilationResult.reportToMessageCollectorAndThrowOnErrors(messageCollector: LoggingMessageRenderer) {
    // TODO: anything else we need to duplicate?
    if (messageCollector.errors.isNotEmpty()) {
        throw ScriptCompilationException(messageCollector.errors)
    }
}


private
inline fun <T> withCompilationExceptionHandler(messageCollector: LoggingMessageRenderer, action: () -> T): T {
    val log = messageCollector.log
    return when {
        log.isDebugEnabled -> {
            loggingOutputTo(log::debug) { action() }
        }

        else -> {
            ignoringOutputOf { action() }
        }
    }
}


private
inline fun <T> loggingOutputTo(noinline log: (String) -> Unit, action: () -> T): T =
    redirectingOutputTo({ LoggingOutputStream(log) }, action)


private
inline fun <T> ignoringOutputOf(action: () -> T): T =
    redirectingOutputTo({ NullOutputStream.INSTANCE }, action)


private
inline fun <T> redirectingOutputTo(noinline outputStream: () -> OutputStream, action: () -> T): T =
    redirecting(System.err, System::setErr, outputStream()) {
        redirecting(System.out, System::setOut, outputStream()) {
            action()
        }
    }


private
inline fun <T> redirecting(
    stream: PrintStream,
    set: (PrintStream) -> Unit,
    to: OutputStream,
    action: () -> T
): T = try {
    set(PrintStream(to, true))
    action()
} finally {
    set(stream)
    to.flush()
}


private
class LoggingOutputStream(val log: (String) -> Unit) : OutputStream() {

    private
    val buffer = ByteArrayOutputStream()

    override fun write(b: Int) = buffer.write(b)

    override fun write(b: ByteArray, off: Int, len: Int) = buffer.write(b, off, len)

    override fun flush() {
        buffer.run {
            val string = toString("utf8")
            if (string.isNotBlank()) {
                log(string)
            }
            reset()
        }
    }

    override fun close() {
        flush()
    }
}


private
fun messageCollectorFor(
    log: Logger,
    allWarningsAsErrors: Boolean,
    pathTranslation: (String) -> String,
): LoggingMessageRenderer =
    messageCollectorFor(log, onCompilerWarningsFor(allWarningsAsErrors), pathTranslation)


private
fun messageCollectorFor(
    log: Logger,
    onCompilerWarning: CompilerWarning = CompilerWarning.WARN,
    pathTranslation: (String) -> String = { it }
): LoggingMessageRenderer =
    LoggingMessageRenderer(log, onCompilerWarning, pathTranslation)


internal
data class ScriptCompilationError(val message: String, val location: SourceLocation?)


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
        get() = errors.firstNotNullOfOrNull { it.location?.line }

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
    fun errorAt(location: SourceLocation, message: String): String {
        val columnIndent = " ".repeat(5 + maxLineNumberStringLength + 1 + location.column)
        return "Line ${lineNumber(location)}: ${location.lineContent}\n" +
                "^ $message".lines().joinToString(
                    prefix = columnIndent,
                    separator = "\n$columnIndent  $INDENT"
                )
    }

    private
    fun lineNumber(location: SourceLocation) =
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
enum class CompilerWarning {
    FAIL, WARN, DEBUG
}


private
fun onCompilerWarningsFor(allWarningsAsErrors: Boolean) =
    if (allWarningsAsErrors) CompilerWarning.FAIL
    else CompilerWarning.WARN


private
class LoggingMessageRenderer(
    val log: Logger,
    private val onCompilerWarning: CompilerWarning,
    private val pathTranslation: (String) -> String,
) : CompilerMessageRenderer {
    val errors = arrayListOf<ScriptCompilationError>()

    override fun render(severity: Severity, message: String, location: SourceLocation?): String? {
        fun msg() =
            location?.run {
                path.let(pathTranslation).let { path ->
                    when {
                        line >= 0 && column >= 0 -> compilerMessageFor(path, line, column, message)
                        else -> "${clickableFileUrlFor(path)}: $message"
                    }
                }
            } ?: message

        fun taggedMsg() =
            "${severity.name[0].lowercase()}: ${msg()}"

        fun onError() {
            errors += ScriptCompilationError(message, location)
            log.error { taggedMsg() }
        }

        fun onWarning() {
            when (onCompilerWarning) {
                CompilerWarning.FAIL -> onError()
                CompilerWarning.WARN -> log.warn { taggedMsg() }
                CompilerWarning.DEBUG -> log.debug { taggedMsg() }
            }
        }

        when (severity) {
            Severity.ERROR -> onError()
            Severity.WARNING -> onWarning()
            Severity.INFO -> log.info { msg() }
            Severity.DEBUG -> log.debug { taggedMsg() }
        }

        return message // TODO: does it matter what we return here?
    }

}


internal
fun compilerMessageFor(path: String, line: Int, column: Int, message: String) =
    "${clickableFileUrlFor(path)}:$line:$column: $message"


private
fun clickableFileUrlFor(path: String): String =
    ConsoleRenderer().asClickableFileUrl(File(path))


@OptIn(ExperimentalBuildToolsApi::class, ExperimentalCompilerArgument::class)
private class Compiler {

    companion object {
        private const val MODULE_NAME = "buildscript"
    }

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
        compilerOptions: KotlinCompilerOptions,
        classPath: List<File>,
        template: KClass<out Any>,
        implicitImports: List<String>,
        messageRenderer: LoggingMessageRenderer
    ): CompilationResult {
        val operationBuilder = toolchains.jvm.jvmCompilationOperationBuilder(sources, destinationDirectory)

        // compilation operation config
        operationBuilder[JvmCompilationOperation.COMPILER_ARGUMENTS_LOG_LEVEL] = CompilerArgumentsLogLevel.DEBUG
        // TODO: incremental compilation should make explicit fingerprint checking obsolete

        operationBuilder.compilerArguments.let {
            it.configureScriptEnvironment(classPath, template, implicitImports)
            it.configureLanguageVersion(compilerOptions)
            it.configurePlugins()
            it.configureMisc()
        }

        operationBuilder[JvmCompilationOperation.COMPILER_MESSAGE_RENDERER] = messageRenderer

        // TODO: executeOperation has an overload with configurable ExecutionPolicy, that's how Daemon mode can be enabled
        return buildSession.executeOperation(operationBuilder.build(), toolchains.createInProcessExecutionPolicy())
    }

    fun JvmCompilerArguments.Builder.configureScriptEnvironment(classPath: List<File>, template: KClass<out Any>, implicitImports: List<String>) {
        this[NO_STDLIB] = true // Don't automatically include the Kotlin/JVM stdlib and Kotlin reflection dependencies in the classpath.
        this[NO_REFLECT] = true // Don't automatically include the Kotlin reflection dependency in the classpath. // TODO: is it really covered by NO_STDLIB?
        this[CLASSPATH] = classPath.map { it.toPath() }

        this[SCRIPT_TEMPLATES] = listOf(template.jvmName)
        this[X_SCRIPT_RESOLVER_ENVIRONMENT] = arrayOf(
            resolverEnvironmentStringFor(
                listOf(kotlinDslImplicitImports to implicitImports)
            )
        )
    }

    fun JvmCompilerArguments.Builder.configureLanguageVersion(compilerOptions: KotlinCompilerOptions) {
        this[LANGUAGE_VERSION] = org.jetbrains.kotlin.buildtools.api.arguments.enums.KotlinVersion.V2_2
        this[API_VERSION] = org.jetbrains.kotlin.buildtools.api.arguments.enums.KotlinVersion.V2_2
        this[JVM_TARGET] = org.jetbrains.kotlin.buildtools.api.arguments.enums.JvmTarget.valueOf("JVM_" + compilerOptions.jvmTarget.toKotlinJvmTarget().description) // TODO: ugly conversion

        this[X_SKIP_METADATA_VERSION_CHECK] = compilerOptions.skipMetadataVersionCheck
        this[X_SKIP_PRERELEASE_CHECK] = true
        this[X_ALLOW_UNSTABLE_DEPENDENCIES] = true
        this[JVM_DEFAULT] = JvmDefaultMode.ENABLE

        this.also { // apply java type enhancement settings
            it[X_JSR305] = arrayOf("strict", "under-migration:strict")
            // TODO: not sure what the equivalent of `getReportLevelForAnnotation` is... maybe JvmCompilerArguments.X_JSPECIFY_ANNOTATIONS, but that defaults to the right value
        }
    }

    fun JvmCompilerArguments.Builder.configurePlugins() {
        fun pathOfJar(classLoader: ClassLoader, jarName: String): Path { // TODO: blasphemy, but how to do it right?!?
            if (classLoader is URLClassLoader) {
                val jarFile = classLoader.urLs.firstOrNull { it.file.contains(jarName) }
                if (jarFile != null) {
                    return Paths.get(jarFile.toURI())
                }
            }

            val pathToJar = System.getProperty("java.class.path").split(File.pathSeparator).firstOrNull { it.contains(jarName) }
            if (pathToJar != null) {
                return Paths.get(pathToJar)
            }

            throw RuntimeException("$jarName.jar not found on the classpath!")
        }

        val scriptingPlugin = CompilerPlugin(
            pluginId = KOTLIN_SCRIPTING_PLUGIN_ID,
            classpath = listOf(pathOfJar(ScriptingCompilerConfigurationComponentRegistrar::class.java.classLoader, "kotlin-scripting-compiler-embeddable")),
            rawArguments = listOf(),
            orderingRequirements = setOf()
        )
        val samWithReceiverPlugin = CompilerPlugin(
            pluginId = SamWithReceiverPluginNames.PLUGIN_ID,
            classpath = listOf(pathOfJar(SamWithReceiverPluginNames::class.java.classLoader, "kotlin-sam-with-receiver-compiler-plugin")),
            rawArguments = listOf(CompilerPluginOption(SamWithReceiverPluginNames.ANNOTATION_OPTION_NAME, HasImplicitReceiver::class.qualifiedName!!)),
            orderingRequirements = setOf()
        )
        val assignmentPlugin = CompilerPlugin(
            pluginId = AssignmentPluginNames.PLUGIN_ID,
            classpath = listOf(pathOfJar(AssignmentPluginNames::class.java.classLoader, "kotlin-assignment-compiler-plugin-embeddable")),
            rawArguments = listOf(CompilerPluginOption(AssignmentPluginNames.ANNOTATION_OPTION_NAME, SupportsKotlinAssignmentOverloading::class.qualifiedName!!)),
            orderingRequirements = setOf()
        )

        this[COMPILER_PLUGINS] = listOf(scriptingPlugin, samWithReceiverPlugin, assignmentPlugin)
    }

    fun JvmCompilerArguments.Builder.configureMisc() {
        // TODO: put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
        this[X_ALLOW_ANY_SCRIPTS_IN_SOURCE_ROOTS] = true
        this[X_USE_FIR_LT] = false
        this[X_SAM_CONVERSIONS] = SamConversionsMode.CLASS
        // TODO: addJvmSdkRoot(...)

        this[JvmCompilerArguments.MODULE_NAME] = MODULE_NAME
    }
}


@VisibleForTesting
fun JavaVersion.toKotlinJvmTarget(): JvmTarget {
    // JvmTarget.fromString(JavaVersion.majorVersion) works from Java 9 to Java 26
    return JvmTarget.fromString(majorVersion)
        ?: if (this <= JavaVersion.VERSION_1_8) JVM_1_8
        else JvmTarget.JVM_26
}
