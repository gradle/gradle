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

import org.gradle.cache.internal.CrossBuildInMemoryCache
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory
import org.gradle.kotlin.dsl.execution.CompiledScript

import org.gradle.kotlin.dsl.execution.ProgramId

import javax.inject.Inject


internal
class KotlinScriptClassloadingCache @Inject constructor(
    cacheFactory: CrossBuildInMemoryCacheFactory
) {

    private
    val cache: CrossBuildInMemoryCache<ProgramId, CompiledScript> = cacheFactory.newCache()

    fun get(key: ProgramId): CompiledScript? =
        cache.getIfPresent(key)?.also { it.onReuse() }

    fun put(key: ProgramId, loadedScriptClass: CompiledScript) {
        cache.put(key, loadedScriptClass)
    }
}
