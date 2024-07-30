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

package org.gradle.kotlin.dsl.provider

import org.gradle.api.Project
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginAwareInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.ExtensionAware
import org.gradle.cache.CacheOpenException
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.internal.ScriptSourceHasher
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.initialization.GradlePropertiesController
import org.gradle.internal.buildoption.InternalFlag
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.CachedClasspathTransformer
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForLegacy
import org.gradle.internal.classpath.types.GradleCoreInstrumentationTypeRegistry
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.caching.CachingDisabledReason
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory
import org.gradle.internal.execution.history.OverlappingOutputs
import org.gradle.internal.hash.HashCode
import org.gradle.internal.instrumentation.reporting.PropertyUpgradeReportConfig
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.scripts.BuildScriptCompilationAndInstrumentation
import org.gradle.internal.scripts.BuildScriptCompilationAndInstrumentation.Output
import org.gradle.internal.scripts.CompileScriptBuildOperationType.Details
import org.gradle.internal.scripts.CompileScriptBuildOperationType.Result
import org.gradle.internal.scripts.ScriptExecutionListener
import org.gradle.kotlin.dsl.accessors.Stage1BlocksAccessorClassPathGenerator
import org.gradle.kotlin.dsl.cache.KotlinDslWorkspaceProvider
import org.gradle.kotlin.dsl.execution.CompiledScript
import org.gradle.kotlin.dsl.execution.EvalOption
import org.gradle.kotlin.dsl.execution.EvalOptions
import org.gradle.kotlin.dsl.execution.Interpreter
import org.gradle.kotlin.dsl.execution.ProgramId
import org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.KotlinCompilerOptions
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.ScriptCompilationException
import org.gradle.kotlin.dsl.support.kotlinCompilerOptions
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.use.internal.PluginRequestApplicator
import java.io.File
import java.util.Optional


interface KotlinScriptEvaluator {

    fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean,
        options: EvalOptions
    )
}


@Suppress("unused", "LongParameterList")
internal
class StandardKotlinScriptEvaluator(
    private val classPathProvider: KotlinScriptClassPathProvider,
    private val classloadingCache: KotlinScriptClassloadingCache,
    private val pluginRequestApplicator: PluginRequestApplicator,
    private val pluginRequestsHandler: PluginRequestsHandler,
    private val embeddedKotlinProvider: EmbeddedKotlinProvider,
    private val classPathModeExceptionCollector: ClassPathModeExceptionCollector,
    private val kotlinScriptBasePluginsApplicator: KotlinScriptBasePluginsApplicator,
    private val scriptSourceHasher: ScriptSourceHasher,
    private val classpathHasher: ClasspathHasher,
    private val implicitImports: ImplicitImports,
    private val progressLoggerFactory: ProgressLoggerFactory,
    private val buildOperationRunner: BuildOperationRunner,
    private val cachedClasspathTransformer: CachedClasspathTransformer,
    private val scriptExecutionListener: ScriptExecutionListener,
    private val executionEngine: ExecutionEngine,
    private val workspaceProvider: KotlinDslWorkspaceProvider,
    private val fileCollectionFactory: FileCollectionFactory,
    private val inputFingerprinter: InputFingerprinter,
    private val internalOptions: InternalOptions,
    private val gradlePropertiesController: GradlePropertiesController,
    private val transformFactoryForLegacy: ClasspathElementTransformFactoryForLegacy,
    private val gradleCoreTypeRegistry: GradleCoreInstrumentationTypeRegistry,
    private val propertyUpgradeReportConfig: PropertyUpgradeReportConfig
) : KotlinScriptEvaluator {

    override fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean,
        options: EvalOptions
    ) {
        withOptions(options) {
            interpreter.eval(
                target,
                scriptSource,
                scriptSourceHasher.hash(scriptSource),
                scriptHandler,
                targetScope,
                baseScope,
                topLevelScript,
                options
            )
        }
    }

    private
    inline fun withOptions(options: EvalOptions, action: () -> Unit) {
        if (EvalOption.IgnoreErrors in options)
            classPathModeExceptionCollector.runCatching(action)
        else
            action()
    }

    private
    fun setupEmbeddedKotlinForBuildscript(scriptHandler: ScriptHandler) {
        embeddedKotlinProvider.pinEmbeddedKotlinDependenciesOn(
            scriptHandler.dependencies,
            "classpath"
        )
    }

    private
    val interpreter by lazy {
        when(propertyUpgradeReportConfig.isEnabled) {
            true -> Interpreter(InterpreterHostWithoutInMemoryCache(gradlePropertiesController))
            false -> Interpreter(InterpreterHost(gradlePropertiesController))
        }
    }

    /**
     * An interpreter host that doesn't cache compiled scripts in memory.
     * Used for property upgrade report since we don't cache a report in-memory.
     */
    inner class InterpreterHostWithoutInMemoryCache(
        gradleProperties: GradlePropertiesController
    ) : Interpreter.Host by InterpreterHost(gradleProperties) {
        override fun cachedClassFor(programId: ProgramId): CompiledScript? = null
        override fun cache(specializedProgram: CompiledScript, programId: ProgramId) = Unit
    }

    inner class InterpreterHost(
        gradleProperties: GradlePropertiesController,
    ) : Interpreter.Host {

        override val compilerOptions: KotlinCompilerOptions =
            kotlinCompilerOptions(gradleProperties)

        override fun stage1BlocksAccessorsFor(scriptHost: KotlinScriptHost<*>): ClassPath =
            (scriptHost.target as? ProjectInternal)
                ?.let {
                    val stage1BlocksAccessorClassPathGenerator = it.serviceOf<Stage1BlocksAccessorClassPathGenerator>()
                    stage1BlocksAccessorClassPathGenerator.stage1BlocksAccessorClassPath(it).bin
                } ?: ClassPath.EMPTY

        override fun accessorsClassPathFor(scriptHost: KotlinScriptHost<*>): ClassPath =
            (scriptHost.target as? ExtensionAware)
                ?.let { scriptTarget ->
                    scriptHost.projectAccessorsClassPathGenerator.projectAccessorsClassPath(
                        scriptTarget,
                        compilationClassPathOf(scriptHost.targetScope)
                    ).bin
                } ?: ClassPath.EMPTY

        override fun runCompileBuildOperation(scriptPath: String, stage: String, action: () -> String): String =

            buildOperationRunner.call(object : CallableBuildOperation<String> {

                override fun call(context: BuildOperationContext): String =
                    action().also {
                        context.setResult(object : Result {})
                    }

                override fun description(): BuildOperationDescriptor.Builder {
                    val name = "Compile script ${scriptPath.substringAfterLast(File.separator)} ($stage)"
                    return BuildOperationDescriptor.displayName(name).name(name).details(object : Details {
                        override fun getStage(): String = stage
                        override fun getLanguage(): String = "KOTLIN"
                    })
                }
            })

        override fun onScriptClassLoaded(scriptSource: ScriptSource, specializedProgram: Class<*>) {
            scriptExecutionListener.onScriptClassLoaded(scriptSource, specializedProgram)
        }

        override fun setupEmbeddedKotlinFor(scriptHost: KotlinScriptHost<*>) {
            setupEmbeddedKotlinForBuildscript(scriptHost.scriptHandler)
        }

        override fun startCompilerOperation(description: String): AutoCloseable {
            val operationDescription = "Compiling $description"
            val operation = progressLoggerFactory
                .newOperation(KotlinScriptEvaluator::class.java)
                .start(operationDescription, operationDescription)
            return AutoCloseable { operation.completed() }
        }

        override fun hashOf(classPath: ClassPath): HashCode =
            classpathHasher.hash(classPath)

        override fun applyPluginsTo(scriptHost: KotlinScriptHost<*>, pluginRequests: PluginRequests) {
            pluginRequestsHandler.handle(
                pluginRequests,
                scriptHost.scriptHandler as ScriptHandlerInternal,
                scriptHost.target as PluginAwareInternal,
                scriptHost.targetScope
            )
        }

        override fun applyBasePluginsTo(project: ProjectInternal) {
            kotlinScriptBasePluginsApplicator
                .apply(project)
        }

        override fun closeTargetScopeOf(scriptHost: KotlinScriptHost<*>) {

            pluginRequestApplicator.applyPlugins(
                PluginRequests.EMPTY,
                scriptHost.scriptHandler as ScriptHandlerInternal,
                null,
                scriptHost.targetScope
            )
        }

        override fun cachedClassFor(
            programId: ProgramId
        ): CompiledScript? = classloadingCache.get(programId)

        override fun cache(
            specializedProgram: CompiledScript,
            programId: ProgramId
        ) {
            classloadingCache.put(
                programId,
                specializedProgram
            )
        }

        override fun cachedDirFor(
            scriptHost: KotlinScriptHost<*>,
            programId: ProgramId,
            compilationClassPath: ClassPath,
            accessorsClassPath: ClassPath,
            initializer: (File) -> Unit
        ): File = try {
            val output = executionEngineFor(scriptHost)
                .createRequest(
                    KotlinScriptCompilationAndInstrumentation(
                        scriptHost.scriptSource,
                        programId,
                        compilationClassPath,
                        accessorsClassPath,
                        initializer,
                        classpathHasher,
                        workspaceProvider,
                        fileCollectionFactory,
                        inputFingerprinter,
                        internalOptions,
                        transformFactoryForLegacy,
                        gradleCoreTypeRegistry,
                        propertyUpgradeReportConfig
                    )
                )
                .execute()
                .getOutputAs(Output::class.java)
                .get()
            propertyUpgradeReportConfig.reportCollector.collect(output.propertyUpgradeReport)
            output.instrumentedOutput
        } catch (e: CacheOpenException) {
            throw e.cause as? ScriptCompilationException ?: e
        }

        override fun compilationClassPathOf(classLoaderScope: ClassLoaderScope): ClassPath =
            classPathProvider.compilationClassPathOf(classLoaderScope)

        override fun loadClassInChildScopeOf(
            classLoaderScope: ClassLoaderScope,
            childScopeId: String,
            origin: ClassLoaderScopeOrigin,
            location: File,
            className: String,
            accessorsClassPath: ClassPath
        ): CompiledScript {
            val classpath = DefaultClassPath.of(location).plus(accessorsClassPath)
            return ScopeBackedCompiledScript(classLoaderScope, childScopeId, origin, classpath, className)
        }

        override val implicitImports: List<String>
            get() = this@StandardKotlinScriptEvaluator.implicitImports.list
    }

    private
    fun executionEngineFor(scriptHost: KotlinScriptHost<*>): ExecutionEngine {
        // get the ExecutionEngine from the closest available service scope
        // for the global one has no support for the build cache
        return (scriptHost.target as? Project)?.serviceOf()
            ?: executionEngine
    }

    private
    class ScopeBackedCompiledScript(
        private val classLoaderScope: ClassLoaderScope,
        private val childScopeId: String,
        private val origin: ClassLoaderScopeOrigin,
        override val classPath: ClassPath,
        private val className: String
    ) : CompiledScript {
        private
        var loadedClass: Class<*>? = null
        var scope: ClassLoaderScope? = null

        @get:Synchronized
        override val program: Class<*>
            get() {
                if (loadedClass == null) {
                    scope = prepareClassLoaderScope().also {
                        loadedClass = it.localClassLoader.loadClass(className)
                    }
                }
                return loadedClass!!
            }

        @Synchronized
        override fun onReuse() {
            scope?.let {
                // Recreate the script scope and ClassLoader, so that things that use scopes are notified that the scope exists
                it.onReuse()
                require(loadedClass!!.classLoader == it.localClassLoader)
            }
        }

        private
        fun prepareClassLoaderScope() =
            classLoaderScope.createLockedChild(
                childScopeId,
                origin,
                classPath,
                null,
                null
            )
    }

    internal
    class KotlinScriptCompilationAndInstrumentation(
        source: ScriptSource,
        private val programId: ProgramId,
        private val compilationClassPath: ClassPath,
        private val accessorsClassPath: ClassPath,
        private val compileTo: (File) -> Unit,
        private val classpathHasher: ClasspathHasher,
        workspaceProvider: KotlinDslWorkspaceProvider,
        fileCollectionFactory: FileCollectionFactory,
        inputFingerprinter: InputFingerprinter,
        internalOptions: InternalOptions,
        transformFactory: ClasspathElementTransformFactoryForLegacy,
        gradleCoreTypeRegistry: GradleCoreInstrumentationTypeRegistry,
        propertyUpgradeReportConfig: PropertyUpgradeReportConfig,
        private val cachingDisabledByProperty: Boolean = internalOptions.getOption(CACHING_DISABLED_PROPERTY).get()

    ) : BuildScriptCompilationAndInstrumentation(source, workspaceProvider.scripts, fileCollectionFactory, inputFingerprinter, transformFactory, gradleCoreTypeRegistry, propertyUpgradeReportConfig) {

        companion object {
            const val JVM_TARGET = "jvmTarget"
            const val ALL_WARNINGS_AS_ERRORS = "allWarningsAsErrors"
            const val SKIP_METADATA_VERSION_CHECK = "skipMetadataVersionCheck"
            const val TEMPLATE_ID = "templateId"
            const val SOURCE_HASH = "sourceHash"
            const val COMPILATION_CLASS_PATH = "compilationClassPath"
            const val ACCESSORS_CLASS_PATH = "accessorsClassPath"
            val CACHING_DISABLED_PROPERTY: InternalFlag = InternalFlag("org.gradle.internal.kotlin-script-caching-disabled")
            val CACHING_DISABLED_REASON: CachingDisabledReason = CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Caching of Kotlin script compilation disabled by property")
        }

        override fun getDisplayName(): String =
            "Kotlin DSL script compilation (${programId.templateId})"


        override fun shouldDisableCaching(detectedOverlappingOutputs: OverlappingOutputs?): Optional<CachingDisabledReason> {
            if (cachingDisabledByProperty) {
                return Optional.of(CACHING_DISABLED_REASON)
            }

            return super.shouldDisableCaching(detectedOverlappingOutputs)
        }

        override fun visitIdentityInputs(visitor: UnitOfWork.InputVisitor) {
            super.visitIdentityInputs(visitor)
            visitor.visitInputProperty(JVM_TARGET) { programId.compilerOptions.jvmTarget.majorVersion }
            visitor.visitInputProperty(ALL_WARNINGS_AS_ERRORS) { programId.compilerOptions.allWarningsAsErrors }
            visitor.visitInputProperty(SKIP_METADATA_VERSION_CHECK) { programId.compilerOptions.skipMetadataVersionCheck }
            visitor.visitInputProperty(TEMPLATE_ID) { programId.templateId }
            visitor.visitInputProperty(SOURCE_HASH) { programId.sourceHash }
            visitor.visitInputProperty(COMPILATION_CLASS_PATH) { classpathHasher.hash(compilationClassPath) }
            visitor.visitInputProperty(ACCESSORS_CLASS_PATH) { classpathHasher.hash(accessorsClassPath) }
        }

        override fun compile(workspace: File): File {
            return File(workspace, "classes").apply {
                mkdirs()
                compileTo.invoke(this)
            }
        }

        override fun instrumentedOutput(workspace: File): File {
            return File(workspace, "instrumented/classes")
        }
    }
}
