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

import org.gradle.StartParameter

import org.gradle.cache.internal.CacheKeyBuilder
import org.gradle.kotlin.dsl.provider.ScriptCacheRepository


internal
object BuildServices {

    @Suppress("unused")
    fun createScriptCache(
        cacheKeyBuilder: CacheKeyBuilder,
        scriptCacheRepository: ScriptCacheRepository,
        startParameters: StartParameter
    ): ScriptCache {

        val hasBuildCacheIntegration =
            startParameters.isBuildCacheEnabled && isKotlinDslBuildCacheEnabled

        return ScriptCache(
            scriptCacheRepository,
            cacheKeyBuilder,
            hasBuildCacheIntegration
        )
    }
}


private
val isKotlinDslBuildCacheEnabled: Boolean
    get() = System.getProperty("org.gradle.kotlin.dsl.caching.buildcache", null) != "false"
