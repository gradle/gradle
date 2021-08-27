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
import org.gradle.cache.CacheOpenException
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.internal.ScriptSourceHasher
import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.CachedClasspathTransformer
import org.gradle.internal.classpath.CachedClasspathTransformer.StandardTransform.BuildLogic
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.fingerprint.InputFingerprinter
import org.gradle.internal.execution.fingerprint.InputFingerprinter.InputVisitor
import org.gradle.internal.file.TreeType
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing.newHasher
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.scripts.CompileScriptBuildOperationType.Details
import org.gradle.internal.scripts.CompileScriptBuildOperationType.Result
import org.gradle.internal.scripts.ScriptExecutionListener
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.kotlin.dsl.accessors.PluginAccessorClassPathGenerator
import org.gradle.kotlin.dsl.cache.KotlinDslWorkspaceProvider
import org.gradle.kotlin.dsl.execution.CompiledScript
import org.gradle.kotlin.dsl.execution.EvalOption
import org.gradle.kotlin.dsl.execution.EvalOptions
import org.gradle.kotlin.dsl.execution.Interpreter
import org.gradle.kotlin.dsl.execution.ProgramId
import org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.ScriptCompilationException
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.use.internal.PluginRequestApplicator
import java.io.File


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
    private val buildOperationExecutor: BuildOperationExecutor,
    private val cachedClasspathTransformer: CachedClasspathTransformer,
    private val scriptExecutionListener: ScriptExecutionListener,
    private val executionEngine: ExecutionEngine,
    private val workspaceProvider: KotlinDslWorkspaceProvider,
    private val fileCollectionFactory: FileCollectionFactory,
    private val inputFingerprinter: InputFingerprinter
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
            classPathModeExceptionCollector.ignoringErrors(action)
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
        Interpreter(InterpreterHost())
    }

    inner class InterpreterHost : Interpreter.Host {

        override fun pluginAccessorsFor(scriptHost: KotlinScriptHost<*>): ClassPath =
            (scriptHost.target as? Project)?.let {
                val pluginAccessorClassPathGenerator = it.serviceOf<PluginAccessorClassPathGenerator>()
                pluginAccessorClassPathGenerator.pluginSpecBuildersClassPath(it).bin
            } ?: ClassPath.EMPTY

        override fun runCompileBuildOperation(scriptPath: String, stage: String, action: () -> String): String =

            buildOperationExecutor.call(object : CallableBuildOperation<String> {

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

        override fun applyBasePluginsTo(project: Project) {
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
            templateId: String,
            sourceHash: HashCode,
            compilationClassPath: ClassPath,
            accessorsClassPath: ClassPath,
            initializer: (File) -> Unit
        ): File = try {
            executionEngineFor(scriptHost).createRequest(
                CompileKotlinScript(
                    templateId,
                    sourceHash,
                    compilationClassPath,
                    accessorsClassPath,
                    initializer,
                    classpathHasher,
                    workspaceProvider,
                    fileCollectionFactory,
                    inputFingerprinter
                )
            ).execute().executionResult.get().output as File
        } catch (e: CacheOpenException) {
            throw e.cause as? ScriptCompilationException ?: e
        }

        override fun compilationClassPathOf(classLoaderScope: ClassLoaderScope): ClassPath =
            classPathProvider.compilationClassPathOf(classLoaderScope)

        override fun loadClassInChildScopeOf(
            classLoaderScope: ClassLoaderScope,
            childScopeId: String,
            location: File,
            className: String,
            accessorsClassPath: ClassPath
        ): CompiledScript {
            val instrumentedClasses = cachedClasspathTransformer.transform(DefaultClassPath.of(location), BuildLogic)
            val classpath = instrumentedClasses.plus(accessorsClassPath)
            return ScopeBackedCompiledScript(classLoaderScope, childScopeId, classpath, className)
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
                classPath,
                null,
                null
            )
    }
}


internal
class CompileKotlinScript(
    private val templateId: String,
    private val sourceHash: HashCode,
    private val compilationClassPath: ClassPath,
    private val accessorsClassPath: ClassPath,
    private val compileTo: (File) -> Unit,
    private val classpathHasher: ClasspathHasher,
    private val workspaceProvider: KotlinDslWorkspaceProvider,
    private val fileCollectionFactory: FileCollectionFactory,
    private val inputFingerprinter: InputFingerprinter
) : UnitOfWork {

    override fun visitIdentityInputs(
        visitor: InputVisitor
    ) {
        visitor.visitInputProperty("templateId") { templateId }
        visitor.visitInputProperty("sourceHash") { sourceHash }
        visitor.visitClassPathProperty("compilationClassPath", compilationClassPath)
        visitor.visitClassPathProperty("accessorsClassPath", accessorsClassPath)
    }

    override fun visitOutputs(
        workspace: File,
        visitor: UnitOfWork.OutputVisitor
    ) {
        val classesDir = classesDir(workspace)
        visitor.visitOutputProperty(
            "classesDir",
            TreeType.DIRECTORY,
            classesDir,
            fileCollectionFactory.fixed(classesDir)
        )
    }

    override fun identify(
        identityInputs: MutableMap<String, ValueSnapshot>,
        identityFileInputs: MutableMap<String, CurrentFileCollectionFingerprint>
    ): UnitOfWork.Identity {
        val identityHash = newHasher().let { hasher ->
            listOf("templateId", "sourceHash", "compilationClassPath", "accessorsClassPath").forEach {
                requireNotNull(identityInputs[it]).appendToHasher(hasher)
            }
            hasher.hash().toString()
        }
        return UnitOfWork.Identity { identityHash }
    }

    override fun execute(executionRequest: UnitOfWork.ExecutionRequest): UnitOfWork.WorkOutput {
        val workspace = executionRequest.workspace
        compileTo(classesDir(workspace))
        return workOutputFor(workspace)
    }

    private
    fun workOutputFor(workspace: File): UnitOfWork.WorkOutput =
        object : UnitOfWork.WorkOutput {
            override fun getDidWork() = UnitOfWork.WorkResult.DID_WORK
            override fun getOutput() = loadRestoredOutput(workspace)
        }

    override fun getDisplayName(): String =
        "Kotlin DSL script compilation ($templateId)"

    override fun loadRestoredOutput(workspace: File): Any =
        classesDir(workspace)

    override fun getWorkspaceProvider() = workspaceProvider.scripts

    override fun getInputFingerprinter() = inputFingerprinter

    private
    fun classesDir(workspace: File) =
        workspace.resolve("classes")

    private
    fun InputVisitor.visitClassPathProperty(propertyName: String, classPath: ClassPath) {
        visitInputProperty(propertyName) {
            classpathHasher.hash(classPath)
        }
    }
}
