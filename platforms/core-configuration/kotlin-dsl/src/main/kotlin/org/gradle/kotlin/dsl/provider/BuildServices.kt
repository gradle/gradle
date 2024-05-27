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
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.loadercache.DefaultClasspathHasher
import org.gradle.groovy.scripts.internal.ScriptSourceHasher
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.initialization.GradlePropertiesController
import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.CachedClasspathTransformer
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForLegacy
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.FileCollectionSnapshotter
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.scripts.ScriptExecutionListener
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.kotlin.dsl.cache.KotlinDslWorkspaceProvider
import org.gradle.kotlin.dsl.normalization.KotlinCompileClasspathFingerprinter
import org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler
import org.gradle.plugin.use.internal.PluginRequestApplicator


internal
const val KOTLIN_SCRIPT_COMPILATION_AVOIDANCE_ENABLED_PROPERTY =
    "org.gradle.kotlin.dsl.scriptCompilationAvoidance"


internal
object BuildServices : ServiceRegistrationProvider {

    @Provides
    fun createKotlinScriptClassPathProvider(
        moduleRegistry: ModuleRegistry,
        classPathRegistry: ClassPathRegistry,
        classLoaderScopeRegistry: ClassLoaderScopeRegistry,
        dependencyFactory: DependencyFactoryInternal,
    ) =

        KotlinScriptClassPathProvider(
            moduleRegistry,
            classPathRegistry,
            classLoaderScopeRegistry.coreAndPluginsScope,
            gradleApiJarsProviderFor(dependencyFactory),
        )

    @Provides
    fun createPluginRequestsHandler(
        pluginRequestApplicator: PluginRequestApplicator,
        autoAppliedPluginHandler: AutoAppliedPluginHandler
    ) =

        PluginRequestsHandler(pluginRequestApplicator, autoAppliedPluginHandler)

    @Provides
    fun createClassPathModeExceptionCollector() =
        ClassPathModeExceptionCollector()

    @Provides
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
        buildOperationRunner: BuildOperationRunner,
        cachedClasspathTransformer: CachedClasspathTransformer,
        listenerManager: ListenerManager,
        executionEngine: ExecutionEngine,
        workspaceProvider: KotlinDslWorkspaceProvider,
        @Suppress("UNUSED_PARAMETER") kotlinCompilerContextDisposer: KotlinCompilerContextDisposer,
        fileCollectionFactory: FileCollectionFactory,
        inputFingerprinter: InputFingerprinter,
        gradlePropertiesController: GradlePropertiesController,
        transformFactoryForLegacy: ClasspathElementTransformFactoryForLegacy
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
            buildOperationRunner,
            cachedClasspathTransformer,
            listenerManager.getBroadcaster(ScriptExecutionListener::class.java),
            executionEngine,
            workspaceProvider,
            fileCollectionFactory,
            inputFingerprinter,
            gradlePropertiesController,
            transformFactoryForLegacy
        )

    @Provides
    fun createCompileClasspathHasher(
        cacheService: ResourceSnapshotterCacheService,
        fileCollectionSnapshotter: FileCollectionSnapshotter,
        stringInterner: StringInterner,
        fileCollectionFactory: FileCollectionFactory,
        classpathFingerprinter: ClasspathFingerprinter
    ) =
        DefaultClasspathHasher(
            if (isKotlinScriptCompilationAvoidanceEnabled) {
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

    @Provides
    fun createKotlinCompilerContextDisposer(listenerManager: ListenerManager) =
        KotlinCompilerContextDisposer(listenerManager)

    private
    val isKotlinScriptCompilationAvoidanceEnabled: Boolean
        get() = System.getProperty(KOTLIN_SCRIPT_COMPILATION_AVOIDANCE_ENABLED_PROPERTY, "true") == "true"
}
