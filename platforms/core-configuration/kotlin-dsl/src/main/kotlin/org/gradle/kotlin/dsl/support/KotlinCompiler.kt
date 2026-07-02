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
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.internal.classloader.ClassLoaderFactory
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.io.NullOutputStream
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.kotlin.dsl.cache.KotlinDslClasspathEntrySnapshotCache
import org.gradle.kotlin.dsl.cache.KotlinDslIncrementalCompilationCache
import org.gradle.kotlin.dsl.provider.PrecompiledScriptsEnvironment.EnvironmentProperties.kotlinDslImplicitImports
import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames
import org.jetbrains.kotlin.buildtools.api.BaseCompilationOperation
import org.jetbrains.kotlin.buildtools.api.BaseCompilationOperation.Companion.COMPILER_MESSAGE_RENDERER
import org.jetbrains.kotlin.buildtools.api.BaseIncrementalCompilationConfiguration.Companion.BACKUP_CLASSES
import org.jetbrains.kotlin.buildtools.api.BaseIncrementalCompilationConfiguration.Companion.KEEP_IC_CACHES_IN_MEMORY
import org.jetbrains.kotlin.buildtools.api.CompilerMessageRenderer
import org.jetbrains.kotlin.buildtools.api.CompilerMessageRenderer.Severity
import org.jetbrains.kotlin.buildtools.api.CompilerMessageRenderer.SourceLocation
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.SourcesChanges
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
import org.jetbrains.kotlin.buildtools.api.arguments.Jsr305
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
import org.jetbrains.kotlin.buildtools.api.arguments.enums.JvmTarget as BtaJvmTarget
import org.jetbrains.kotlin.buildtools.api.arguments.enums.KotlinVersion
import org.jetbrains.kotlin.buildtools.api.arguments.enums.SamConversionsMode
import org.jetbrains.kotlin.buildtools.api.jvm.JvmPlatformToolchain.Companion.jvm
import org.jetbrains.kotlin.buildtools.api.jvm.JvmSnapshotBasedIncrementalCompilationConfiguration.Companion.PRECISE_JAVA_TRACKING
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmCompilationOperation
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmCompilationOperation.Companion.INCREMENTAL_COMPILATION
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmCompilationOperation.CompilerArgumentsLogLevel
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.JvmTarget.JVM_1_8
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverPluginNames
import org.jetbrains.kotlin.scripting.compiler.plugin.KOTLIN_SCRIPTING_PLUGIN_ID
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.Path
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.util.PropertiesCollection

// Master switch for BTA incremental compilation; even when on, cold compiles skip IC (see
// KotlinDslIncrementalCompilationCache.shouldConfigureIncrementalCompilation). Off compiles plain.
private const val INCREMENTAL_COMPILATION_ENABLED = true

private val classloaderInstances: MutableMap<ClassPath, URLClassLoader> = mutableMapOf() // necessary because some Kotlin code is retaining them and we can't clean it up properly
private val compilerInstances: MutableMap<Pair<ModuleRegistry, ClassLoaderFactory>, KotlinCompilerImpl> = mutableMapOf()

internal fun kotlinCompiler(moduleRegistry: ModuleRegistry, classLoaderFactory: ClassLoaderFactory): KotlinCompiler {
    val classLoader = KotlinCompiler::class.java.classLoader
    return compilerInstances.computeIfAbsent(Pair(moduleRegistry, classLoaderFactory), { KotlinCompilerImpl(moduleRegistry, classLoader) })
}

internal fun cleanupKotlinCompilers() {
    compilerInstances.values.forEach { kotlinCompiler -> kotlinCompiler.clean() }
    compilerInstances.clear()

    classloaderInstances.values.forEach { classLoader -> classLoader.close() }
    classloaderInstances.clear()
}

internal interface KotlinCompiler {
    fun compileKotlinScriptToDirectory(
        outputDirectory: File,
        compilerOptions: KotlinCompilerOptions,
        scriptFile: File,
        implicitImports: List<String>,
        template: KClass<out Any>,
        classPath: List<File>,
        logger: Logger,
        fileSystemAccess: FileSystemAccess,
        classpathSnapshotCache: KotlinDslClasspathEntrySnapshotCache,
        incrementalCompilationCache: KotlinDslIncrementalCompilationCache,
        scriptIdentity: String,
        pathTranslation: (String) -> String
    ): String

    fun implicitReceiverOf(template: KClass<*>): KClass<*>?
}

private
class KotlinCompilerImpl(val moduleRegistry: ModuleRegistry, val classLoader: ClassLoader) : KotlinCompiler {

    private val lazyBTACompiler = lazy { BTACompiler(moduleRegistry, classLoader) }
    private val btaCompiler by lazyBTACompiler

    override fun compileKotlinScriptToDirectory(
        outputDirectory: File,
        compilerOptions: KotlinCompilerOptions,
        scriptFile: File,
        implicitImports: List<String>,
        template: KClass<out Any>,
        classPath: List<File>,
        logger: Logger,
        fileSystemAccess: FileSystemAccess,
        classpathSnapshotCache: KotlinDslClasspathEntrySnapshotCache,
        incrementalCompilationCache: KotlinDslIncrementalCompilationCache,
        scriptIdentity: String,
        pathTranslation: (String) -> String
    ): String {
        compileKotlinScriptToDirectory(
            outputDirectory,
            compilerOptions,
            scriptFile,
            implicitImports,
            template,
            classPath,
            messageCollectorFor(logger, compilerOptions.allWarningsAsErrors, pathTranslation),
            fileSystemAccess,
            classpathSnapshotCache,
            incrementalCompilationCache,
            scriptIdentity
        )

        return NameUtils.getScriptNameForFile(scriptFile.name).asString()
    }

    private val receiverCache: MutableMap<KClass<*>, KClass<*>> = mutableMapOf()

    override fun implicitReceiverOf(template: KClass<*>): KClass<*>? {
        return receiverCache.getOrPut(template) {
            val compilationConfigurationClass: KClass<out ScriptCompilationConfiguration>? = template.annotations.firstNotNullOfOrNull { (it as? KotlinScript)?.compilationConfiguration }
            return compilationConfigurationClass?.let {
                val compileConfiguration = scriptConfigInstance(compilationConfigurationClass)
                compileConfiguration?.get(ScriptCompilationConfiguration.implicitReceivers)?.firstOrNull()?.fromClass
            }
        }
    }


    private
    fun compileKotlinScriptToDirectory(
        outputDirectory: File,
        compilerOptions: KotlinCompilerOptions,
        scriptFile: File,
        implicitImports: List<String>,
        template: KClass<out Any>,
        classPath: List<File>,
        messageRenderer: LoggingMessageRenderer,
        fileSystemAccess: FileSystemAccess,
        classpathSnapshotCache: KotlinDslClasspathEntrySnapshotCache,
        incrementalCompilationCache: KotlinDslIncrementalCompilationCache,
        scriptIdentity: String
    ) {
        Output.withRedirecting(messageRenderer.log) {
            btaCompiler.compile(
                listOf(Path(scriptFile.path)),
                outputDirectory.toPath(),
                compilerOptions,
                classPath,
                template,
                implicitImports,
                messageRenderer,
                fileSystemAccess,
                classpathSnapshotCache,
                incrementalCompilationCache,
                scriptIdentity
            )
            if (messageRenderer.errors.isNotEmpty()) {
                throw ScriptCompilationException(messageRenderer.errors)
            }
        }
    }

    fun clean() {
        if (lazyBTACompiler.isInitialized()) {
            btaCompiler.clean()
        }
        receiverCache.clear()
    }

}


private object Output {

    inline fun <T> withRedirecting(logger: Logger, action: () -> T): T {
        return when {
            logger.isDebugEnabled -> {
                loggingOutputTo(logger::debug) { action() }
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

    override fun render(severity: Severity, message: String, location: SourceLocation?): String {
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

        fun onError(): String {
            errors += ScriptCompilationError(message, location)
            return taggedMsg().also { log.error { it } }
        }

        fun onWarning(): String {
            return when (onCompilerWarning) {
                CompilerWarning.FAIL -> onError()
                CompilerWarning.WARN -> taggedMsg().also { log.warn { it } }
                CompilerWarning.DEBUG -> taggedMsg().also { log.debug { it } }
            }
        }

        return when (severity) {
            Severity.ERROR -> onError()
            Severity.WARNING -> onWarning()
            Severity.INFO -> msg().also { log.info { it } }
            Severity.DEBUG -> taggedMsg().also { log.debug { it } }
        }
    }

}


internal
fun compilerMessageFor(path: String, line: Int, column: Int, message: String) =
    "${clickableFileUrlFor(path)}:$line:$column: $message"


private
fun clickableFileUrlFor(path: String): String =
    ConsoleRenderer().asClickableFileUrl(File(path))


private
inline fun <reified T : PropertiesCollection> scriptConfigInstance(kclass: KClass<out T>): T? =
    kclass.objectInstance ?: run {
        val noArgsConstructor = kclass.java.constructors.singleOrNull { it.parameters.isEmpty() }
        noArgsConstructor?.let {
            try {
                it.isAccessible = true
            } catch (_: RuntimeException) {
            }
            it.newInstance() as T
        }
    }


@VisibleForTesting
fun JavaVersion.toKotlinJvmTarget(): JvmTarget {
    // JvmTarget.fromString(JavaVersion.majorVersion) works from Java 9 to Java 26
    return JvmTarget.fromString(majorVersion)
        ?: if (this <= JavaVersion.VERSION_1_8) JVM_1_8
        else JvmTarget.JVM_26
}


@VisibleForTesting
fun JvmTarget.toBuildToolsApiJvmTarget(): BtaJvmTarget =
    // Match on string value: the two enums agree on values ("1.8", "9", ... "26") but not on names (e.g. JVM_1_8 vs JVM1_8).
    BtaJvmTarget.entries.first { it.stringValue == description }


@OptIn(ExperimentalBuildToolsApi::class, ExperimentalCompilerArgument::class)
private class BTACompiler(val moduleRegistry: ModuleRegistry, classLoader: ClassLoader) {

    companion object {
        private const val MODULE_NAME = "buildscript"
        private val logger = LoggerFactory.getLogger(BTACompiler::class.java)
    }

    private lateinit var toolchains: KotlinToolchains
    private lateinit var buildSession: KotlinToolchains.BuildSession

    init {
        toolchains = KotlinToolchains.loadImplementation(classLoader)
        if (!::buildSession.isInitialized) {
            buildSession = toolchains.createBuildSession()
        }
    }

    private val plugins: List<CompilerPlugin> = createPlugins()

    @OptIn(ExperimentalCompilerArgument::class)
    fun compile(
        sources: List<Path>,
        destinationDirectory: Path,
        compilerOptions: KotlinCompilerOptions,
        classPath: List<File>,
        template: KClass<out Any>,
        implicitImports: List<String>,
        messageRenderer: LoggingMessageRenderer,
        fileSystemAccess: FileSystemAccess,
        classpathSnapshotCache: KotlinDslClasspathEntrySnapshotCache,
        incrementalCompilationCache: KotlinDslIncrementalCompilationCache,
        scriptIdentity: String
    ) {
        if (incrementalCompilationCache.wasPreviousCompilationInterrupted(scriptIdentity)) {
            logger.info("Previous compilation of '{}' was interrupted; discarding its outputs and rebuilding.", scriptIdentity)
            incrementalCompilationCache.discardOutputsAndIncrementalState(scriptIdentity)
        }

        // Route BTA at a stable per-scriptIdentity output dir...
        val btaOutputDir = incrementalCompilationCache.scriptOutputsDirectory(scriptIdentity)

        fun runCompilation(incremental: Boolean) {
            val operationBuilder = toolchains.jvm.jvmCompilationOperationBuilder(sources, btaOutputDir)

            operationBuilder[BaseCompilationOperation.COMPILER_ARGUMENTS_LOG_LEVEL] = CompilerArgumentsLogLevel.DEBUG

            operationBuilder.compilerArguments.let {
                it.configureScriptEnvironment(classPath, template, implicitImports)
                it.configureLanguageVersion(compilerOptions)
                it.configureMisc()
            }

            operationBuilder[COMPILER_MESSAGE_RENDERER] = messageRenderer

            if (incremental) {
                operationBuilder.configureIncrementalCompilation(scriptIdentity, classPath, fileSystemAccess, classpathSnapshotCache, incrementalCompilationCache)
            }

            buildSession.executeOperation(operationBuilder.build(), toolchains.createInProcessExecutionPolicy())
        }

        incrementalCompilationCache.markCompilationStarted(scriptIdentity)
        if (INCREMENTAL_COMPILATION_ENABLED && incrementalCompilationCache.shouldConfigureIncrementalCompilation(scriptIdentity)) {
            try {
                runCompilation(incremental = true)
            } catch (e: Exception) {
                logger.info("Incremental compilation of '{}' failed; falling back to a full compile.", scriptIdentity, e)
                messageRenderer.errors.clear()
                incrementalCompilationCache.discardIncrementalState(scriptIdentity)
                runCompilation(incremental = false)
            }
        } else {
            runCompilation(incremental = false)
        }
        incrementalCompilationCache.markCompilationComplete(scriptIdentity)

        // ... then copy into the workspace [destinationDirectory] (which changes every time the immutable compilation workspace changes).
        copyOutputs(btaOutputDir, destinationDirectory)
    }

    private fun copyOutputs(src: Path, dst: Path) {
        Files.walk(src).use { stream ->
            stream.forEach { srcPath ->
                if (srcPath == src) return@forEach
                val target = dst.resolve(src.relativize(srcPath))
                if (Files.isDirectory(srcPath)) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    // Copy, not link: [dst] is the immutable-workspace snapshot one layer up and must
                    // stay isolated. A hard/sym link would share storage with [src] (`outputs/<hash>/`),
                    // which BTA rewrites in place on the next recompile — mutating the already-published
                    // workspace entry and invalidating the output hashes recorded for it. A reflink/CoW
                    // clone would be both safe and fast, but the JDK copy API can't request it.
                    Files.copy(srcPath, target, REPLACE_EXISTING)
                }
            }
        }
    }

    fun clean() {
        if (::buildSession.isInitialized) {
            buildSession.close()
        }
    }

    private fun JvmCompilerArguments.Builder.configureScriptEnvironment(classPath: List<File>, template: KClass<out Any>, implicitImports: List<String>) {
        this[NO_STDLIB] = true // Don't automatically include the Kotlin/JVM stdlib and Kotlin reflection dependencies in the classpath.
        this[NO_REFLECT] = true // Don't automatically include the Kotlin reflection dependency in the classpath.
        this[CLASSPATH] = classPath.map { it.toPath() }

        this[SCRIPT_TEMPLATES] = listOf(template.jvmName)
        this[X_SCRIPT_RESOLVER_ENVIRONMENT] = listOf(resolverEnvironmentStringFor(listOf(kotlinDslImplicitImports to implicitImports)))

        this[COMPILER_PLUGINS] = plugins
    }

    private fun JvmCompilerArguments.Builder.configureLanguageVersion(compilerOptions: KotlinCompilerOptions) {
        this[LANGUAGE_VERSION] = KotlinVersion.V2_2
        this[API_VERSION] = KotlinVersion.V2_2
        this[JVM_TARGET] = compilerOptions.jvmTarget.toKotlinJvmTarget().toBuildToolsApiJvmTarget()

        this[X_SKIP_METADATA_VERSION_CHECK] = compilerOptions.skipMetadataVersionCheck
        this[X_SKIP_PRERELEASE_CHECK] = true
        this[X_ALLOW_UNSTABLE_DEPENDENCIES] = true
        this[JVM_DEFAULT] = JvmDefaultMode.ENABLE

        this.also { // apply java type enhancement settings
            it[X_JSR305] = listOf(Jsr305.Global(Jsr305.Mode.STRICT), Jsr305.UnderMigration(Jsr305.Mode.STRICT))
        }
    }

    private fun JvmCompilerArguments.Builder.configureMisc() {
        this[X_ALLOW_ANY_SCRIPTS_IN_SOURCE_ROOTS] = true
        this[X_USE_FIR_LT] = false
        this[X_SAM_CONVERSIONS] = SamConversionsMode.CLASS

        this[JvmCompilerArguments.MODULE_NAME] = MODULE_NAME
    }

    /**
     * Wires snapshot-based incremental compilation into [this] operation builder.
     *
     * The IC working directory is keyed by [scriptIdentity] so IC state persists across edits to
     * the same script. Paired with the stable per-[scriptIdentity] BTA output directory (see
     * [compile]); the kotlin-dsl workspace cache's content-addressed destination one layer up
     * gets populated by copy.
     */
    private fun JvmCompilationOperation.Builder.configureIncrementalCompilation(
        scriptIdentity: String,
        classPath: List<File>,
        fileSystemAccess: FileSystemAccess,
        classpathSnapshotCache: KotlinDslClasspathEntrySnapshotCache,
        incrementalCompilationCache: KotlinDslIncrementalCompilationCache,
    ) {
        val icWorkingDirectory = scriptIcRootFor(scriptIdentity, incrementalCompilationCache)
        val dependencySnapshots = classPath.map { snapshotClasspathEntry(it.toPath(), fileSystemAccess, classpathSnapshotCache) }

        val icConfig = snapshotBasedIcConfigurationBuilder(
            workingDirectory = icWorkingDirectory,
            sourcesChanges = SourcesChanges.ToBeCalculated,
            dependenciesSnapshotFiles = dependencySnapshots,
        ).apply {
            // kotlin-dsl never compiles Java sources.
            this[PRECISE_JAVA_TRACKING] = false

            // BACKUP_CLASSES=true rolls back partial outputs on an in-process compile failure. Off here:
            // thrown failures are already covered by the full-compile fallback, and a hard crash mid-emit
            // (which its in-process rollback couldn't undo anyway) by the interrupted-compile marker — both in compile().
            this[BACKUP_CLASSES] = false

            // Keep IC caches in memory and flush them together rather than writing each key through
            // during the compile — faster. A crash before that flush leaves ic-state stale or uncommitted,
            // which is safe: the interrupted-compile marker (compile()) discards it and rebuilds cold.
            this[KEEP_IC_CACHES_IN_MEMORY] = true
        }.build()

        this[INCREMENTAL_COMPILATION] = icConfig
    }

    private fun snapshotClasspathEntry(entry: Path, fileSystemAccess: FileSystemAccess, snapshotCache: KotlinDslClasspathEntrySnapshotCache): Path {
        // Key by the entry's content hash (covers both jar files and class directories), so identical
        // bytes share one snapshot file and in-place rewrites invalidate the cached snapshot correctly.
        val contentHash = fileSystemAccess.read(entry.toAbsolutePath().toString()).hash
        return snapshotCache.snapshotFileFor(contentHash) { path ->
            BtaClasspathSnapshotter.snapshot(toolchains.jvm, buildSession, entry, path)
        }
    }

    private fun scriptIcRootFor(scriptIdentity: String, cache: KotlinDslIncrementalCompilationCache): Path =
        cache.scriptCacheDirectory(scriptIdentity)

    private fun createPlugins(): List<CompilerPlugin> {
        fun pathOfJar(moduleRegistry: ModuleRegistry, jarName: String): Path? {
            val module = moduleRegistry.findModule(jarName)
            val jarUri = module?.implementationClasspath?.asURIs?.firstOrNull()
            if (jarUri != null) {
                return Paths.get(jarUri)
            }

            return null
        }

        val scriptingPlugin = pathOfJar(moduleRegistry, "kotlin-scripting-compiler-embeddable")?.let {
            CompilerPlugin(
                pluginId = KOTLIN_SCRIPTING_PLUGIN_ID,
                classpath = listOf(it),
                rawArguments = listOf(),
                orderingRequirements = setOf()
            )
        }
        val samWithReceiverPlugin = pathOfJar(moduleRegistry, "kotlin-sam-with-receiver-compiler-plugin")?.let {
            CompilerPlugin(
                pluginId = SamWithReceiverPluginNames.PLUGIN_ID,
                classpath = listOf(it),
                rawArguments = listOf(CompilerPluginOption(SamWithReceiverPluginNames.ANNOTATION_OPTION_NAME, HasImplicitReceiver::class.qualifiedName!!)),
                orderingRequirements = setOf()
            )
        }
        val assignmentPlugin = pathOfJar(moduleRegistry, "kotlin-assignment-compiler-plugin-embeddable")?.let {
            CompilerPlugin(
                pluginId = AssignmentPluginNames.PLUGIN_ID,
                classpath = listOf(it),
                rawArguments = listOf(CompilerPluginOption(AssignmentPluginNames.ANNOTATION_OPTION_NAME, SupportsKotlinAssignmentOverloading::class.qualifiedName!!)),
                orderingRequirements = setOf()
            )
        }

        return listOfNotNull(scriptingPlugin, samWithReceiverPlugin, assignmentPlugin)
    }
}
