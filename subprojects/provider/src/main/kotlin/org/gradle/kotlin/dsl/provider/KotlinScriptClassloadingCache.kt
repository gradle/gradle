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

import org.gradle.internal.hash.HashCode

import java.lang.ref.WeakReference

import javax.inject.Inject


internal
class KotlinScriptClassloadingCache @Inject constructor(
    cacheFactory: CrossBuildInMemoryCacheFactory
) {

    private
    val cache: CrossBuildInMemoryCache<ScriptCacheKey, Class<*>> = cacheFactory.newCache()

    fun get(key: ScriptCacheKey): Class<*>? =
        cache.get(key)

    fun <T> put(key: ScriptCacheKey, loadedScriptClass: Class<T>) {
        cache.put(key, loadedScriptClass)
    }
}


internal
class ScriptCacheKey(
    private val templateId: String,
    private val sourceHash: HashCode,
    parentClassLoader: ClassLoader,
    private val classPathHash: HashCode? = null
) {

    private
    val parentClassLoader = WeakReference(parentClassLoader)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        val that = other as? ScriptCacheKey ?: return false
        val thisParentLoader = parentClassLoader.get()
        return thisParentLoader != null
            && thisParentLoader == that.parentClassLoader.get()
            && templateId == that.templateId
            && sourceHash == that.sourceHash
            && classPathHash == that.classPathHash
    }

    override fun hashCode(): Int {
        var result = templateId.hashCode()
        result = 31 * result + sourceHash.hashCode()
        parentClassLoader.get()?.let { loader ->
            result = 31 * result + loader.hashCode()
        }
        classPathHash?.let { classPathHash ->
            result = 31 * result + classPathHash.hashCode()
        }
        return result
    }
}
