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

import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache

import org.gradle.cache.internal.CacheKeyBuilder
import org.gradle.cache.internal.CacheKeyBuilder.CacheKeySpec

import java.io.File


internal
class ScriptCache(

    private
    val cacheRepository: CacheRepository,

    private
    val cacheKeyBuilder: CacheKeyBuilder,

    private
    val recompileScripts: Boolean) {

    fun cacheDirFor(
        keySpec: CacheKeySpec,
        properties: Map<String, Any?>? = null,
        scope: Any? = null,
        initializer: PersistentCache.() -> Unit): File =

        cacheRepository
            .cache(scope, cacheKeyFor(keySpec))
            .apply { properties?.let { withProperties(it) } }
            .apply { if (recompileScripts) withValidator { false } }
            .withInitializer(initializer)
            .open().run {
                close()
                baseDir
            }

    private
    fun cacheKeyFor(spec: CacheKeySpec) = cacheKeyBuilder.build(spec)
}


internal
operator fun CacheKeySpec.plus(files: List<File>) =
    files.fold(this, CacheKeySpec::plus)

