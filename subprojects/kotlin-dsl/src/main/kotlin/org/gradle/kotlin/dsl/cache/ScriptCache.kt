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

package org.gradle.kotlin.dsl.cache

import org.gradle.api.Project

import org.gradle.cache.internal.CacheKeyBuilder
import org.gradle.cache.internal.CacheKeyBuilder.CacheKeySpec

import org.gradle.caching.internal.controller.BuildCacheController

import org.gradle.internal.id.UniqueId
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.time.Time.startTimer
import org.gradle.kotlin.dsl.provider.ScriptCacheRepository

import org.gradle.kotlin.dsl.support.serviceOf

import java.io.File


internal
class ScriptCache(

    private
    val scriptCacheRepository: ScriptCacheRepository,

    private
    val cacheKeyBuilder: CacheKeyBuilder,

    private
    val hasBuildCacheIntegration: Boolean
) {

    fun cacheDirFor(
        cacheKeySpec: CacheKeySpec,
        scriptTarget: Any? = null,
        displayName: String = "",
        initializer: (File) -> Unit
    ): File {
        val cacheKey = cacheKeyFor(cacheKeySpec)

        return scriptCacheRepository.cache(cacheKey)
            .withProperties(cacheProperties)
            .withInitializer {
                initializeCacheDir(
                    cacheDirOf(it.baseDir).apply { mkdir() },
                    cacheKey,
                    scriptTarget,
                    displayName,
                    initializer
                )
            }.open().run {
                close()
                cacheDirOf(baseDir)
            }
    }

    private
    val cacheProperties = mapOf("version" to "15")

    private
    fun cacheDirOf(baseDir: File) = File(baseDir, "cache")

    private
    fun cacheKeyFor(spec: CacheKeySpec): String = cacheKeyBuilder.build(spec)

    private
    fun initializeCacheDir(
        cacheDir: File,
        cacheKey: String,
        scriptTarget: Any?,
        displayName: String,
        initializer: (File) -> Unit
    ) {

        val cacheController =
            if (hasBuildCacheIntegration) buildCacheControllerOf(scriptTarget)
            else null

        if (cacheController != null) {
            val buildCacheKey = ScriptBuildCacheKey(displayName, cacheKey)
            val buildInvocationId = buildInvocationIdOf(scriptTarget).asString()
            val existing = cacheController.load(LoadDirectory(cacheDir, buildCacheKey, buildInvocationId))
            if (!existing.isPresent) {

                val executionTime = executionTimeMillisOf {
                    initializer(cacheDir)
                }

                cacheController.store(
                    StoreDirectory(
                        cacheDir,
                        buildCacheKey,
                        PackMetadata(buildInvocationId, executionTime)
                    )
                )
            }
        } else {
            initializer(cacheDir)
        }
    }

    private
    fun buildCacheControllerOf(scriptTarget: Any?): BuildCacheController? =
        (scriptTarget as? Project)
            ?.serviceOf<BuildCacheController>()
            ?.takeIf { it.isEnabled }

    private
    fun buildInvocationIdOf(scriptTarget: Any?): UniqueId =
        (scriptTarget as Project)
            .gradle.serviceOf<BuildInvocationScopeId>()
            .id
}


private
inline fun executionTimeMillisOf(action: () -> Unit) = startTimer().run {
    action()
    elapsedMillis
}


internal
operator fun CacheKeySpec.plus(files: List<File>) =
    files.fold(this, CacheKeySpec::plus)
