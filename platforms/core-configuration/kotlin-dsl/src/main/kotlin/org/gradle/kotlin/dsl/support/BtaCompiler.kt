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
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.kotlin.dsl.cache.KotlinDslClasspathEntrySnapshotCache
import org.gradle.kotlin.dsl.cache.KotlinDslIncrementalCompilationCache
import org.gradle.kotlin.dsl.provider.PrecompiledScriptsEnvironment.EnvironmentProperties.kotlinDslImplicitImports
import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames
import org.jetbrains.kotlin.buildtools.api.BaseCompilationOperation
import org.jetbrains.kotlin.buildtools.api.BaseCompilationOperation.Companion.COMPILER_MESSAGE_RENDERER
import org.jetbrains.kotlin.buildtools.api.BaseIncrementalCompilationConfiguration.Companion.BACKUP_CLASSES
import org.jetbrains.kotlin.buildtools.api.BaseIncrementalCompilationConfiguration.Companion.KEEP_IC_CACHES_IN_MEMORY
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.SourcesChanges
import org.jetbrains.kotlin.buildtools.api.arguments.CommonCompilerArguments.Companion.API_VERSION
import org.jetbrains.kotlin.buildtools.api.arguments.CommonCompilerArguments.Companion.COMPILER_PLUGINS
import org.jetbrains.kotlin.buildtools.api.arguments.CommonCompilerArguments.Companion.LANGUAGE_VERSION
import org.jetbrains.kotlin.buildtools.api.arguments.CommonCompilerArguments.Companion.X_ALLOW_ANY_SCRIPTS_IN_SOURCE_ROOTS
import org.jetbrains.kotlin.buildtools.api.arguments.CommonCompilerArguments.Companion.X_SKIP_METADATA_VERSION_CHECK
import org.jetbrains.kotlin.buildtools.api.arguments.CommonCompilerArguments.Companion.X_SKIP_PRERELEASE_CHECK
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
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverPluginNames
import org.jetbrains.kotlin.scripting.compiler.plugin.KOTLIN_SCRIPTING_PLUGIN_ID
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName


@VisibleForTesting
fun JavaVersion.toKotlinJvmTarget(): JvmTarget {
    // JvmTarget.fromString(JavaVersion.majorVersion) works from Java 9 to Java 26
    return JvmTarget.fromString(majorVersion)
        ?: if (this <= JavaVersion.VERSION_1_8) JVM_1_8
        else JvmTarget.JVM_26
}


@VisibleForTesting
/* Match on string value: the two enums agree on values ("1.8", "9", ... "26")
 * but not on names (e.g. JVM_1_8 vs JVM1_8). */
internal fun JvmTarget.toBuildToolsApiJvmTarget(): BtaJvmTarget =

    @Suppress("EnumValuesSoftDeprecate") // entries emits a synthetic public EnumEntries<BtaJvmTarget> field, exposing the type in this module's ABI.
    BtaJvmTarget.values().first { it.stringValue == description }


@OptIn(ExperimentalBuildToolsApi::class, ExperimentalCompilerArgument::class)
internal class BTACompiler(val moduleRegistry: ModuleRegistry) {

    companion object {
        private const val MODULE_NAME = "buildscript"
        private val logger = LoggerFactory.getLogger(BTACompiler::class.java)
    }

    private val session = kotlinToolchains.createBuildSession()

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
            val operationBuilder = kotlinToolchains.jvm.jvmCompilationOperationBuilder(sources, btaOutputDir)

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

            session.executeOperation(operationBuilder.build(), kotlinToolchains.createInProcessExecutionPolicy())
        }

        incrementalCompilationCache.markCompilationStarted(scriptIdentity)
        if (incrementalCompilationCache.shouldConfigureIncrementalCompilation(scriptIdentity)) {
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

    fun close() {
        session.close()
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
            BtaClasspathSnapshotter.snapshot(kotlinToolchains.jvm, session, entry, path)
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
