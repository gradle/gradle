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
import org.gradle.api.internal.cache.GeneratedGradleJarCache

import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.kotlin.dsl.cache.ScriptCache
import org.gradle.kotlin.dsl.support.ImplicitImports


internal
object BuildServices {

    @Suppress("unused")
    fun createCachingKotlinCompiler(
        scriptCache: ScriptCache,
        implicitImports: ImplicitImports,
        progressLoggerFactory: ProgressLoggerFactory) =

        CachingKotlinCompiler(scriptCache, implicitImports, progressLoggerFactory)

    @Suppress("unused")
    fun createKotlinScriptClassPathProvider(
        classPathRegistry: ClassPathRegistry,
        dependencyFactory: DependencyFactory,
        jarCache: GeneratedGradleJarCache,
        progressLoggerFactory: ProgressLoggerFactory) =

        KotlinScriptClassPathProvider(
            classPathRegistry,
            gradleApiJarsProviderFor(dependencyFactory),
            versionedJarCacheFor(jarCache),
            StandardJarGenerationProgressMonitorProvider(progressLoggerFactory))

    private
    fun versionedJarCacheFor(jarCache: GeneratedGradleJarCache): JarCache =
        { id, creator -> jarCache["$id-$gradleKotlinDslVersion", creator] }

    private
    val gradleKotlinDslVersion by lazy {
        this::class.java.`package`.implementationVersion
    }
}
