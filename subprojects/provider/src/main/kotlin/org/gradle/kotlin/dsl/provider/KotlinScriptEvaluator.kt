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
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginAwareInternal

import org.gradle.cache.internal.CacheKeyBuilder

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.HashCode

import org.gradle.kotlin.dsl.execution.Interpreter

import org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider
import org.gradle.kotlin.dsl.support.KotlinScriptHost

import org.gradle.plugin.management.internal.DefaultPluginRequests
import org.gradle.plugin.management.internal.PluginRequests

import java.io.File

import java.util.*


interface KotlinScriptEvaluator {

    fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean,
        options: EnumSet<KotlinScriptOption>
    )
}


enum class KotlinScriptOption {
    IgnoreErrors,
    SkipBody
}


internal
class StandardKotlinScriptEvaluator(
    private val classPathProvider: KotlinScriptClassPathProvider,
    private val kotlinCompiler: CachingKotlinCompiler,
    private val classloadingCache: KotlinScriptClassloadingCache,
    private val pluginRequestsHandler: PluginRequestsHandler,
    private val embeddedKotlinProvider: EmbeddedKotlinProvider,
    private val classPathModeExceptionCollector: ClassPathModeExceptionCollector
) : KotlinScriptEvaluator {

    override fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean,
        options: EnumSet<KotlinScriptOption>
    ) {

        if (options.isEmpty() && (target is Settings || isProjectScriptPluginRequest(topLevelScript, target))) {
            interpreter.eval(
                target,
                scriptSource,
                scriptHandler,
                targetScope,
                baseScope,
                topLevelScript)
            return
        }

        evaluationFor(target, scriptSource, scriptHandler, targetScope, baseScope, topLevelScript).run {
            if (KotlinScriptOption.IgnoreErrors in options)
                executeIgnoringErrors(executeScriptBody = KotlinScriptOption.SkipBody !in options)
            else
                execute()
        }
    }

    private
    fun isProjectScriptPluginRequest(topLevelScript: Boolean, target: Any) =
        !topLevelScript && target is Project

    private
    fun evaluationFor(
        target: Any,
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean
    ): KotlinScriptEvaluation =

        KotlinScriptEvaluation(
            kotlinScriptTargetFor(target, scriptSource, scriptHandler, targetScope, baseScope, topLevelScript),
            KotlinScriptSource(scriptSource),
            scriptHandler as ScriptHandlerInternal,
            targetScope,
            baseScope,
            classloadingCache,
            kotlinCompiler,
            pluginRequestsHandler,
            classPathProvider,
            embeddedKotlinProvider,
            classPathModeExceptionCollector)

    private
    val interpreter by lazy {
        Interpreter(InterpreterHost())
    }

    private
    inner class InterpreterHost : Interpreter.Host {

        override fun applyPluginsTo(scriptHost: KotlinScriptHost<*>, pluginRequests: PluginRequests) {
            pluginRequestsHandler.handle(
                pluginRequests,
                scriptHost.scriptHandler as ScriptHandlerInternal,
                scriptHost.target as PluginAwareInternal,
                scriptHost.targetScope)
        }

        override fun closeTargetScopeOf(scriptHost: KotlinScriptHost<*>) {
            val targetScope = scriptHost.targetScope
//                    targetScope.export(classPathProvider.gradleApiExtensions)
            pluginRequestsHandler.pluginRequestApplicator.applyPlugins(
                DefaultPluginRequests.EMPTY,
                scriptHost.scriptHandler as ScriptHandlerInternal?,
                null,
                targetScope)
        }

        override fun cachedClassFor(
            templateId: String,
            sourceHash: HashCode,
            parentClassLoader: ClassLoader
        ): Class<*>? =
            classloadingCache
                .get(cacheKeyFor(templateId, sourceHash, parentClassLoader))
                ?.scriptClass

        override fun cache(
            templateId: String,
            sourceHash: HashCode,
            parentClassLoader: ClassLoader,
            specializedProgram: Class<*>
        ) {
            classloadingCache.put(
                cacheKeyFor(templateId, sourceHash, parentClassLoader),
                LoadedScriptClass(
                    CompiledScript(File(""), specializedProgram.name, Unit),
                    specializedProgram))
        }

        private
        fun cacheKeyFor(
            templateId: String,
            sourceHash: HashCode,
            parentClassLoader: ClassLoader
        ): ScriptCacheKey =
            ScriptCacheKey(
                templateId,
                sourceHash,
                parentClassLoader,
                lazyOf(HashCode.fromInt(0)))

        override fun cachedDirFor(
            templateId: String,
            sourceHash: HashCode,
            parentClassLoader: ClassLoader,
            initializer: (File) -> Unit
        ): File =
            kotlinCompiler.cacheDirFor(
                cacheKeyPrefix + templateId + sourceHash.toString() + parentClassLoader) {
                initializer(baseDir)
            }

        private
        val cacheKeyPrefix =
            CacheKeyBuilder.CacheKeySpec.withPrefix("kotlin-dsl-interpreter")

        override fun compilationClassPathOf(classLoaderScope: ClassLoaderScope): ClassPath =
            classPathProvider.compilationClassPathOf(classLoaderScope)

        override fun loadClassInChildScopeOf(
            classLoaderScope: ClassLoaderScope,
            childScopeId: String,
            location: File,
            className: String
        ): Class<*> =
            classLoaderScope
                .createChild(childScopeId)
                .local(DefaultClassPath.of(location))
                .lock()
                .localClassLoader
                .loadClass(className)

        override val implicitImports: List<String>
            get() = kotlinCompiler.implicitImports.list
    }
}
