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
import org.gradle.api.JavaVersion
import org.gradle.api.SupportsKotlinAssignmentOverloading
import org.gradle.internal.SystemProperties
import org.gradle.internal.io.NullOutputStream
import org.gradle.internal.logging.ConsoleRenderer
import org.jetbrains.kotlin.assignment.plugin.AssignmentComponentContainerContributor
import org.jetbrains.kotlin.assignment.plugin.CliAssignPluginResolutionAltererExtension
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler.compileBunchOfSources
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmSdkRoots
import org.jetbrains.kotlin.codegen.CompilationException
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer.dispose
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer.newDisposable
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys.IR
import org.jetbrains.kotlin.config.JVMConfigurationKeys.JDK_HOME
import org.jetbrains.kotlin.config.JVMConfigurationKeys.JVM_TARGET
import org.jetbrains.kotlin.config.JVMConfigurationKeys.OUTPUT_DIRECTORY
import org.jetbrains.kotlin.config.JVMConfigurationKeys.RETAIN_OUTPUT_IN_MEMORY
import org.jetbrains.kotlin.config.JVMConfigurationKeys.SAM_CONVERSIONS
import org.jetbrains.kotlin.config.JvmAnalysisFlags
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.config.JvmDefaultMode
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.JvmTarget.JVM_1_8
import org.jetbrains.kotlin.config.JvmTarget.JVM_22
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.extensions.internal.InternalNonStableExtensionPoints
import org.jetbrains.kotlin.load.java.JavaTypeEnhancementState
import org.jetbrains.kotlin.load.java.Jsr305Settings
import org.jetbrains.kotlin.load.java.ReportLevel
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.resolve.extensions.AssignResolutionAltererExtension
import org.jetbrains.kotlin.samWithReceiver.CliSamWithReceiverComponentContributor
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptJvmCompilerFromEnvironment
import org.jetbrains.kotlin.scripting.compiler.plugin.toCompilerMessageSeverity
import org.jetbrains.kotlin.scripting.configuration.ScriptingConfigurationKeys.SCRIPT_DEFINITIONS
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.utils.PathUtil
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.isError
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.JvmGetScriptingClass
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvmhost.BasicJvmScriptClassFilesGenerator
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.JvmScriptCompiler


@Suppress("LongParameterList")
fun compileKotlinScriptModuleForPrecompiledScriptPluginsTo(
    outputDirectory: File,
    compilerOptions: KotlinCompilerOptions,
    moduleName: String,
    scriptFiles: Collection<String>,
    scriptDef: ScriptDefinition,
    classPath: Iterable<File>,
    logger: Logger,
    pathTranslation: (String) -> String
) = compileKotlinScriptModuleForPrecompiledScriptPluginsTo(
    outputDirectory,
    compilerOptions,
    moduleName,
    scriptFiles,
    scriptDef,
    classPath,
    LoggingMessageCollector(logger, onCompilerWarningsFor(compilerOptions.allWarningsAsErrors), pathTranslation)
)


/**
 * Still uses internal K1 API until the consuming task is replaced with a regular KotlinCompile.
 */
private
fun compileKotlinScriptModuleForPrecompiledScriptPluginsTo(
    outputDirectory: File,
    compilerOptions: KotlinCompilerOptions,
    moduleName: String,
    scriptFiles: Collection<String>,
    scriptDef: ScriptDefinition,
    classPath: Iterable<File>,
    messageCollector: LoggingMessageCollector
) {
    withRootDisposable {
        withCompilationExceptionHandler(messageCollector) {
            val configuration = compilerConfigurationFor(messageCollector, compilerOptions).apply {
                put(RETAIN_OUTPUT_IN_MEMORY, false)
                put(OUTPUT_DIRECTORY, outputDirectory)
                setModuleName(moduleName)
                addScriptingCompilerComponents()
                addScriptDefinition(scriptDef)
                scriptFiles.forEach { addKotlinSourceRoot(it) }
                classPath.forEach { addJvmClasspathRoot(it) }
            }

            val environment = kotlinCoreEnvironmentFor(configuration).apply {
                HasImplicitReceiverCompilerPlugin.apply(project)
                KotlinAssignmentCompilerPlugin.apply(project)
            }

            compileBunchOfSources(environment)
                || throw ScriptCompilationException(messageCollector.errors)
        }
    }
}


fun scriptDefinitionFromTemplate(
    template: KClass<out Any>,
    implicitImports: List<String>,
    implicitReceiver: KClass<*>? = null,
    classPath: List<File> = listOf()
): ScriptDefinition {
    val hostConfiguration = ScriptingHostConfiguration {
        getScriptingClass(JvmGetScriptingClass())
        configurationDependencies(JvmDependency(classPath))
    }
    return ScriptDefinition.FromConfigurations(
        hostConfiguration = hostConfiguration,
        compilationConfiguration = ScriptCompilationConfiguration {
            baseClass(template)
            defaultImports(implicitImports)
            hostConfiguration(hostConfiguration)
            implicitReceiver?.let {
                implicitReceivers(it)
            }
        },
        evaluationConfiguration = null
    )
}


internal
fun compileKotlinScriptToDirectory(
    outputDirectory: File,
    compilerOptions: KotlinCompilerOptions,
    scriptFile: File,
    scriptDef: ScriptDefinition,
    classPath: List<File>,
    logger: Logger,
    pathTranslation: (String) -> String
): String {

    compileKotlinScriptModuleTo(
        outputDirectory,
        compilerOptions,
        "buildscript",
        listOf(scriptFile.path),
        scriptDef,
        classPath,
        messageCollectorFor(logger, compilerOptions.allWarningsAsErrors, pathTranslation)
    )

    return NameUtils.getScriptNameForFile(scriptFile.name).asString()
}


private
fun compileKotlinScriptModuleTo(
    outputDirectory: File,
    compilerOptions: KotlinCompilerOptions,
    moduleName: String,
    scriptFiles: Collection<String>,
    scriptDef: ScriptDefinition,
    classPath: Iterable<File>,
    messageCollector: LoggingMessageCollector
) {
    withRootDisposable {
        withCompilationExceptionHandler(messageCollector) {
            val configuration = compilerConfigurationFor(messageCollector, compilerOptions).apply {
                put(RETAIN_OUTPUT_IN_MEMORY, false)
                put(OUTPUT_DIRECTORY, outputDirectory)
                setModuleName(moduleName)
                addScriptingCompilerComponents()
            }

            val environment = kotlinCoreEnvironmentFor(configuration).apply {
                HasImplicitReceiverCompilerPlugin.apply(project)
                KotlinAssignmentCompilerPlugin.apply(project)
            }

            val host = BasicJvmScriptingHost(
                compiler = JvmScriptCompiler(scriptDef.hostConfiguration, ScriptJvmCompilerFromEnvironment(environment)),
                evaluator = BasicJvmScriptClassFilesGenerator(outputDirectory)
            )
            val compilationConfiguration = scriptDef.compilationConfiguration.with {
                updateClasspath(classPath.toList())
            }
            scriptFiles.forEach {
                val script = File(it).toScriptSource()
                host.eval(script, compilationConfiguration, scriptDef.evaluationConfiguration)
                    .reportToMessageCollectorAndThrowOnErrors(script, messageCollector)
            }
        }
    }
}


private fun ResultWithDiagnostics<*>.reportToMessageCollectorAndThrowOnErrors(script: SourceCode, messageCollector: MessageCollector): ResultWithDiagnostics<*> = also {
    val lines = if (it.reports.isEmpty()) null else script.text.lines()
    val scriptErrors = ArrayList<ScriptCompilationError>()
    for (report in it.reports) {
        val location = report.location
        val sourcePath = report.sourcePath
        val compilerMessageLocation = if (location != null && sourcePath != null) {
            CompilerMessageLocation.create(
                sourcePath,
                location.start.line, location.start.col,
                lines?.getOrNull(location.start.line - 1)
            )
        } else null

        if (report.isError() || report.severity == ScriptDiagnostic.Severity.WARNING) {
            scriptErrors.add(ScriptCompilationError(report.message, compilerMessageLocation))
        }
        messageCollector.report(
            report.severity.toCompilerMessageSeverity(),
            report.render(withSeverity = false, withLocation = location == null || sourcePath == null),
            compilerMessageLocation
        )
    }
    if (it is ResultWithDiagnostics.Failure || (messageCollector.hasErrors() && scriptErrors.isNotEmpty())) {
        throw ScriptCompilationException(scriptErrors)
    }
}

private
object KotlinAssignmentCompilerPlugin {

    @OptIn(InternalNonStableExtensionPoints::class)
    fun apply(project: Project) {
        val annotations = listOf(SupportsKotlinAssignmentOverloading::class.qualifiedName!!)
        AssignResolutionAltererExtension.Companion.registerExtension(project, CliAssignPluginResolutionAltererExtension(annotations))
        StorageComponentContainerContributor.registerExtension(project, AssignmentComponentContainerContributor(annotations))
    }
}


private
object HasImplicitReceiverCompilerPlugin {

    fun apply(project: Project) {
        StorageComponentContainerContributor.registerExtension(project, samWithReceiverComponentContributor)
    }

    val samWithReceiverComponentContributor = CliSamWithReceiverComponentContributor(
        listOf("org.gradle.api.HasImplicitReceiver")
    )
}


@VisibleForTesting
internal
fun compileToDirectory(
    outputDirectory: File,
    compilerOptions: KotlinCompilerOptions,
    moduleName: String,
    sourceFiles: Iterable<File>,
    logger: Logger,
    classPath: Iterable<File>,
): Boolean {

    withRootDisposable {
        withMessageCollectorFor(logger, EmbeddedKotlinCompilerWarning.WARN) { messageCollector ->
            val configuration = compilerConfigurationFor(messageCollector, compilerOptions).apply {
                addKotlinSourceRoots(sourceFiles.map { it.canonicalPath })
                put(OUTPUT_DIRECTORY, outputDirectory)
                setModuleName(moduleName)
                classPath.forEach { addJvmClasspathRoot(it) }
                addJvmClasspathRoot(kotlinStdlibJar)
            }
            val environment = kotlinCoreEnvironmentFor(configuration)
            return compileBunchOfSources(environment)
        }
    }
}


private
val kotlinStdlibJar: File
    get() = PathUtil.getResourcePathForClass(Unit::class.java)


private
inline fun <T> withRootDisposable(action: Disposable.() -> T): T {
    val rootDisposable = newDisposable()
    try {
        return action(rootDisposable)
    } finally {
        dispose(rootDisposable)
    }
}


private
inline fun <T> withMessageCollectorFor(log: Logger, onCompilerWarning: EmbeddedKotlinCompilerWarning, action: (MessageCollector) -> T): T {
    val messageCollector = messageCollectorFor(log, onCompilerWarning)
    withCompilationExceptionHandler(messageCollector) {
        return action(messageCollector)
    }
}


private
inline fun <T> withCompilationExceptionHandler(messageCollector: LoggingMessageCollector, action: () -> T): T {
    try {
        val log = messageCollector.log
        return when {
            log.isDebugEnabled -> {
                loggingOutputTo(log::debug) { action() }
            }

            else -> {
                ignoringOutputOf { action() }
            }
        }
    } catch (ex: CompilationException) {
        messageCollector.report(
            CompilerMessageSeverity.EXCEPTION,
            ex.localizedMessage,
            MessageUtil.psiElementToMessageLocation(ex.element)
        )

        throw IllegalStateException("Internal compiler error: ${ex.localizedMessage}", ex)
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
fun compilerConfigurationFor(messageCollector: MessageCollector, compilerOptions: KotlinCompilerOptions): CompilerConfiguration =
    CompilerConfiguration().apply {
        put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
        put(JVM_TARGET, compilerOptions.jvmTarget.toKotlinJvmTarget())
        put(JDK_HOME, File(System.getProperty("java.home")))
        put(IR, true)
        put(SAM_CONVERSIONS, JvmClosureGenerationScheme.CLASS)
        addJvmSdkRoots(PathUtil.getJdkClassesRootsFromCurrentJre())
        put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, gradleKotlinDslLanguageVersionSettingsFor(compilerOptions))
        put(CommonConfigurationKeys.ALLOW_ANY_SCRIPTS_IN_SOURCE_ROOTS, true)
    }


@VisibleForTesting
internal
fun JavaVersion.toKotlinJvmTarget(): JvmTarget {
    // JvmTarget.fromString(JavaVersion.majorVersion) works from Java 9 to Java 21
    return JvmTarget.fromString(majorVersion)
        ?: if (this <= JavaVersion.VERSION_1_8) JVM_1_8
        else JVM_22
}


private
fun gradleKotlinDslLanguageVersionSettingsFor(compilerOptions: KotlinCompilerOptions) = LanguageVersionSettingsImpl(
    languageVersion = LanguageVersion.KOTLIN_1_8,
    apiVersion = ApiVersion.KOTLIN_1_8,
    analysisFlags = mapOf(
        AnalysisFlags.skipMetadataVersionCheck to compilerOptions.skipMetadataVersionCheck,
        AnalysisFlags.skipPrereleaseCheck to true,
        AnalysisFlags.allowUnstableDependencies to true,
        JvmAnalysisFlags.jvmDefaultMode to JvmDefaultMode.ALL,
        JvmAnalysisFlags.javaTypeEnhancementState to JavaTypeEnhancementState(
            jsr305 = Jsr305Settings(globalLevel = ReportLevel.STRICT, migrationLevel = ReportLevel.STRICT),
            getReportLevelForAnnotation = { ReportLevel.STRICT }
        ),
    ),
    specificFeatures = mapOf(
        LanguageFeature.DisableCompatibilityModeForNewInference to LanguageFeature.State.ENABLED,
        LanguageFeature.TypeEnhancementImprovementsInStrictMode to LanguageFeature.State.DISABLED,
    )
)


private
fun CompilerConfiguration.setModuleName(name: String) {
    put(CommonConfigurationKeys.MODULE_NAME, name)
}


@OptIn(ExperimentalCompilerApi::class)
private
fun CompilerConfiguration.addScriptingCompilerComponents() {
    @Suppress("DEPRECATION")
    add(
        org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS,
        ScriptingCompilerConfigurationComponentRegistrar()
    )
}


private
fun CompilerConfiguration.addScriptDefinition(scriptDef: ScriptDefinition) {
    add(SCRIPT_DEFINITIONS, scriptDef)
}


private
fun Disposable.kotlinCoreEnvironmentFor(configuration: CompilerConfiguration): KotlinCoreEnvironment {
    org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback()
    return SystemProperties.getInstance().withSystemProperty(
        KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY.property,
        "true"
    ) {
        KotlinCoreEnvironment.createForProduction(
            this,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
    }
}


internal
fun disposeKotlinCompilerContext() =
    KotlinCoreEnvironment.disposeApplicationEnvironment()


private
fun messageCollectorFor(
    log: Logger,
    allWarningsAsErrors: Boolean,
    pathTranslation: (String) -> String,
): LoggingMessageCollector =
    messageCollectorFor(log, onCompilerWarningsFor(allWarningsAsErrors), pathTranslation)


private
fun messageCollectorFor(
    log: Logger,
    onCompilerWarning: EmbeddedKotlinCompilerWarning = EmbeddedKotlinCompilerWarning.WARN,
    pathTranslation: (String) -> String = { it }
): LoggingMessageCollector =
    LoggingMessageCollector(log, onCompilerWarning, pathTranslation)


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
        errors.asSequence().map(::errorMessage).map(::prependIndent).toList()

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
enum class EmbeddedKotlinCompilerWarning {
    FAIL, WARN, DEBUG
}


private
fun onCompilerWarningsFor(allWarningsAsErrors: Boolean) =
    if (allWarningsAsErrors) EmbeddedKotlinCompilerWarning.FAIL
    else EmbeddedKotlinCompilerWarning.WARN


private
class LoggingMessageCollector(
    val log: Logger,
    private val onCompilerWarning: EmbeddedKotlinCompilerWarning,
    private val pathTranslation: (String) -> String,
) : MessageCollector {

    val errors = arrayListOf<ScriptCompilationError>()

    override fun hasErrors() = errors.isNotEmpty()

    override fun clear() = errors.clear()

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {

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
            "${severity.presentableName[0]}: ${msg()}"

        fun onError() {
            errors += ScriptCompilationError(message, location)
            log.error { taggedMsg() }
        }

        fun onWarning() {
            when (onCompilerWarning) {
                EmbeddedKotlinCompilerWarning.FAIL -> onError()
                EmbeddedKotlinCompilerWarning.WARN -> log.warn { taggedMsg() }
                EmbeddedKotlinCompilerWarning.DEBUG -> log.debug { taggedMsg() }
            }
        }

        when (severity) {
            CompilerMessageSeverity.ERROR, CompilerMessageSeverity.EXCEPTION -> onError()
            in CompilerMessageSeverity.VERBOSE -> log.trace { msg() }
            CompilerMessageSeverity.STRONG_WARNING -> onWarning()
            CompilerMessageSeverity.WARNING -> onWarning()
            CompilerMessageSeverity.INFO -> log.info { msg() }
            else -> log.debug { taggedMsg() }
        }
    }
}


internal
fun compilerMessageFor(path: String, line: Int, column: Int, message: String) =
    "${clickableFileUrlFor(path)}:$line:$column: $message"


private
fun clickableFileUrlFor(path: String): String =
    ConsoleRenderer().asClickableFileUrl(File(path))
