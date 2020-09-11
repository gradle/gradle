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
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginAwareInternal

import org.gradle.cache.CacheOpenException
import org.gradle.cache.internal.CacheKeyBuilder

import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.internal.ScriptSourceHasher

import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.CachedClasspathTransformer
import org.gradle.internal.classpath.CachedClasspathTransformer.StandardTransform.BuildLogic
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.internal.hash.HashCode

import org.gradle.internal.logging.progress.ProgressLoggerFactory

import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.CallableBuildOperation

import org.gradle.internal.scripts.CompileScriptBuildOperationType.Details
import org.gradle.internal.scripts.CompileScriptBuildOperationType.Result
import org.gradle.internal.scripts.ScriptExecutionListener
import org.gradle.kotlin.dsl.accessors.PluginAccessorClassPathGenerator

import org.gradle.kotlin.dsl.cache.ScriptCache
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
const val scriptCacheKeyPrefix = "gradle-kotlin-dsl"


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
    private val classPathHasher: ClasspathHasher,
    private val scriptCache: ScriptCache,
    private val implicitImports: ImplicitImports,
    private val progressLoggerFactory: ProgressLoggerFactory,
    private val buildOperationExecutor: BuildOperationExecutor,
    private val cachedClasspathTransformer: CachedClasspathTransformer,
    private val scriptExecutionListener: ScriptExecutionListener
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
            val operation = progressLoggerFactory
                .newOperation(KotlinScriptEvaluator::class.java)
                .start("Compiling script into cache", "Compiling $description into local compilation cache")
            return AutoCloseable { operation.completed() }
        }

        override fun hashOf(classPath: ClassPath): HashCode =
            classPathHasher.hash(classPath)

        override fun applyPluginsTo(scriptHost: KotlinScriptHost<*>, pluginRequests: PluginRequests) {
            pluginRequestsHandler.handle(
                pluginRequests,
                scriptHost.scriptHandler as ScriptHandlerInternal,
                scriptHost.target as PluginAwareInternal,
                scriptHost.targetScope)
        }

        override fun applyBasePluginsTo(project: Project) {
            kotlinScriptBasePluginsApplicator
                .apply(project)
        }

        override fun closeTargetScopeOf(scriptHost: KotlinScriptHost<*>) {

            pluginRequestApplicator.applyPlugins(
                PluginRequests.EMPTY,
                scriptHost.scriptHandler as ScriptHandlerInternal?,
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
            parentClassLoader: ClassLoader,
            accessorsClassPath: ClassPath?,
            initializer: (File) -> Unit
        ): File = try {

            val baseCacheKey =
                cacheKeySpecPrefix + templateId + sourceHash + parentClassLoader

            val effectiveCacheKey =
                accessorsClassPath?.let { baseCacheKey + it }
                    ?: baseCacheKey

            cacheDirFor(scriptHost, effectiveCacheKey, initializer)
        } catch (e: CacheOpenException) {
            throw e.cause as? ScriptCompilationException ?: e
        }

        private
        fun cacheDirFor(
            scriptHost: KotlinScriptHost<*>,
            cacheKeySpec: CacheKeyBuilder.CacheKeySpec,
            initializer: (File) -> Unit
        ): File =
            scriptCache.cacheDirFor(
                cacheKeySpec,
                scriptTarget = scriptHost.target,
                displayName = scriptHost.scriptSource.displayName,
                initializer = initializer
            )

        private
        val cacheKeySpecPrefix =
            CacheKeyBuilder.CacheKeySpec.withPrefix(scriptCacheKeyPrefix)

        override fun compilationClassPathOf(classLoaderScope: ClassLoaderScope): ClassPath =
            classPathProvider.compilationClassPathOf(classLoaderScope)

        override fun loadClassInChildScopeOf(
            classLoaderScope: ClassLoaderScope,
            childScopeId: String,
            location: File,
            className: String,
            accessorsClassPath: ClassPath?
        ): CompiledScript {
            val instrumentedClasses = cachedClasspathTransformer.transform(DefaultClassPath.of(location), BuildLogic)
            val classpath = instrumentedClasses.plus(accessorsClassPath ?: ClassPath.EMPTY)
            return ScopeBackedCompiledScript(classLoaderScope, childScopeId, classpath, className)
        }

        override val implicitImports: List<String>
            get() = this@StandardKotlinScriptEvaluator.implicitImports.list
    }

    private
    class ScopeBackedCompiledScript(
        private val classLoaderScope: ClassLoaderScope,
        private val childScopeId: String,
        private val classPath: ClassPath,
        private val className: String
    ) : CompiledScript {
        private
        var loadedClass: Class<*>? = null
        var scope: ClassLoaderScope? = null

        override val programFor: Class<*>
            get() {
                if (loadedClass == null) {
                    scope = prepareClassLoaderScope().also {
                        loadedClass = it.localClassLoader.loadClass(className)
                    }
                }
                return loadedClass!!
            }

        override fun onReuse() {
            scope?.let {
                // Recreate the script scope and ClassLoader, so that things that use scopes are notified that the scope exists
                it.onReuse()
                require(loadedClass!!.classLoader == it.localClassLoader)
            }
        }

        private
        fun prepareClassLoaderScope() = classLoaderScope.createLockedChild(childScopeId, classPath, null, null)
    }
}
