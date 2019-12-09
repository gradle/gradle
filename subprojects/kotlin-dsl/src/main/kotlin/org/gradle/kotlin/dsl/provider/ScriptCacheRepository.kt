/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.CacheScopeMapping
import org.gradle.cache.internal.VersionStrategy
import org.gradle.internal.vfs.AdditiveCache
import org.gradle.kotlin.dsl.accessors.accessorCacheKeyPrefix
import java.io.File


internal
class ScriptCacheRepository(
    private val cacheScopeMapping: CacheScopeMapping,
    private val cacheRepository: CacheRepository
) : AdditiveCache {

    fun cache(key: String): CacheBuilder =
        cacheRepository.cache(key)

    override fun getAdditiveCacheRoots(): List<File> =
        listOf(
            cacheRootFor(scriptCacheKeyPrefix),
            cacheRootFor(accessorCacheKeyPrefix)
        )

    private
    fun cacheRootFor(prefix: String): File =
        cacheScopeMapping.getBaseDirectory(null, prefix, VersionStrategy.CachePerVersion)
}
