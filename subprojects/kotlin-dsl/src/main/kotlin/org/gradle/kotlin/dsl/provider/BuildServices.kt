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

package org.gradle.kotlin.dsl.provider

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.initialization.loadercache.DefaultClasspathHasher
import org.gradle.cache.internal.GeneratedGradleJarCache
import org.gradle.groovy.scripts.internal.ScriptSourceHasher
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.CachedClasspathTransformer
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.fingerprint.FileCollectionSnapshotter
import org.gradle.internal.execution.fingerprint.InputFingerprinter
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.scripts.ScriptExecutionListener
import org.gradle.kotlin.dsl.cache.KotlinDslWorkspaceProvider
import org.gradle.kotlin.dsl.normalization.KotlinCompileClasspathFingerprinter
import org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler
import org.gradle.plugin.use.internal.PluginRequestApplicator


const val BUILDSCRIPT_COMPILE_AVOIDANCE_ENABLED = true


internal
object BuildServices {

    @Suppress("unused")
    fun createKotlinScriptClassPathProvider(
        moduleRegistry: ModuleRegistry,
        classPathRegistry: ClassPathRegistry,
        classLoaderScopeRegistry: ClassLoaderScopeRegistry,
        dependencyFactory: DependencyFactory,
        jarCache: GeneratedGradleJarCache,
        temporaryFileProvider: TemporaryFileProvider,
        progressLoggerFactory: ProgressLoggerFactory
    ) =

        KotlinScriptClassPathProvider(
            moduleRegistry,
            classPathRegistry,
            classLoaderScopeRegistry.coreAndPluginsScope,
            gradleApiJarsProviderFor(dependencyFactory),
            versionedJarCacheFor(jarCache),
            temporaryFileProvider,
            StandardJarGenerationProgressMonitorProvider(progressLoggerFactory)
        )

    @Suppress("unused")
    fun createPluginRequestsHandler(
        pluginRequestApplicator: PluginRequestApplicator,
        autoAppliedPluginHandler: AutoAppliedPluginHandler
    ) =

        PluginRequestsHandler(pluginRequestApplicator, autoAppliedPluginHandler)

    @Suppress("unused")
    fun createClassPathModeExceptionCollector() =
        ClassPathModeExceptionCollector()

    @Suppress("unused")
    fun createKotlinScriptEvaluator(
        classPathProvider: KotlinScriptClassPathProvider,
        classloadingCache: KotlinScriptClassloadingCache,
        pluginRequestsHandler: PluginRequestsHandler,
        pluginRequestApplicator: PluginRequestApplicator,
        embeddedKotlinProvider: EmbeddedKotlinProvider,
        classPathModeExceptionCollector: ClassPathModeExceptionCollector,
        kotlinScriptBasePluginsApplicator: KotlinScriptBasePluginsApplicator,
        scriptSourceHasher: ScriptSourceHasher,
        classpathHasher: ClasspathHasher,
        implicitImports: ImplicitImports,
        progressLoggerFactory: ProgressLoggerFactory,
        buildOperationExecutor: BuildOperationExecutor,
        cachedClasspathTransformer: CachedClasspathTransformer,
        listenerManager: ListenerManager,
        executionEngine: ExecutionEngine,
        workspaceProvider: KotlinDslWorkspaceProvider,
        @Suppress("UNUSED_PARAMETER") kotlinCompilerContextDisposer: KotlinCompilerContextDisposer,
        fileCollectionFactory: FileCollectionFactory,
        inputFingerprinter: InputFingerprinter
    ): KotlinScriptEvaluator =

        StandardKotlinScriptEvaluator(
            classPathProvider,
            classloadingCache,
            pluginRequestApplicator,
            pluginRequestsHandler,
            embeddedKotlinProvider,
            classPathModeExceptionCollector,
            kotlinScriptBasePluginsApplicator,
            scriptSourceHasher,
            classpathHasher,
            implicitImports,
            progressLoggerFactory,
            buildOperationExecutor,
            cachedClasspathTransformer,
            listenerManager.getBroadcaster(ScriptExecutionListener::class.java),
            executionEngine,
            workspaceProvider,
            fileCollectionFactory,
            inputFingerprinter
        )

    @Suppress("unused")
    fun createCompileClasspathHasher(
        cacheService: ResourceSnapshotterCacheService,
        fileCollectionSnapshotter: FileCollectionSnapshotter,
        stringInterner: StringInterner,
        fileCollectionFactory: FileCollectionFactory,
        classpathFingerprinter: ClasspathFingerprinter
    ) =
        DefaultClasspathHasher(
            if (BUILDSCRIPT_COMPILE_AVOIDANCE_ENABLED) {
                KotlinCompileClasspathFingerprinter(
                    cacheService,
                    fileCollectionSnapshotter,
                    stringInterner
                )
            } else {
                classpathFingerprinter
            },
            fileCollectionFactory
        )

    @Suppress("unused")
    fun createKotlinCompilerContextDisposer(listenerManager: ListenerManager) =
        KotlinCompilerContextDisposer(listenerManager)

    private
    fun versionedJarCacheFor(jarCache: GeneratedGradleJarCache): JarCache =
        { id, creator -> jarCache[id, creator] }
}
