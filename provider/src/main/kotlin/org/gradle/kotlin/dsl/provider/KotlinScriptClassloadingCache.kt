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
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.internal.ScriptSourceHasher
import org.gradle.internal.Cast
import org.gradle.internal.hash.HashCode
import org.gradle.kotlin.dsl.support.loggerFor
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlin.reflect.KClass


internal
data class LoadedScriptClass<out T : Any>(
    val compiledScript: CompiledScript<T>,
    val scriptClass: Class<*>)


internal
class KotlinScriptClassloadingCache @Inject constructor(
    cacheFactory: CrossBuildInMemoryCacheFactory,
    private val scriptSourceHasher: ScriptSourceHasher) {

    private
    val logger = loggerFor<KotlinScriptPluginFactory>()

    private
    val cache: CrossBuildInMemoryCache<ScriptCacheKey, LoadedScriptClass<*>> = cacheFactory.newCache()

    fun <T : Any> loadScriptClass(
        displayName: String,
        scriptTemplate: KClass<*>,
        scriptSource: ScriptSource,
        parentClassLoader: ClassLoader,
        classloading: (CompiledScript<T>) -> Class<*>,
        compilation: () -> CompiledScript<T>
    ): LoadedScriptClass<T> {

        val cacheKey = ScriptCacheKey(
            scriptTemplateTypeName = scriptTemplate.java.name,
            scriptSourceHash = scriptSourceHasher.hash(scriptSource),
            parentClassLoader = parentClassLoader)
        val cached = cache.get(cacheKey)
        if (cached != null) {
            return Cast.uncheckedCast<LoadedScriptClass<T>>(cached)
        }

        val compiledScript = compilation()
        logger.debug("Loading {} from {}", scriptTemplate.simpleName, displayName)
        val scriptClass = classloading(compiledScript)
        return LoadedScriptClass(compiledScript, scriptClass).also {
            cache.put(cacheKey, it)
        }
    }
}

private
class ScriptCacheKey(
    private val scriptTemplateTypeName: String,
    private val scriptSourceHash: HashCode,
    parentClassLoader: ClassLoader) {

    private
    val parentClassLoader: WeakReference<ClassLoader> = WeakReference(parentClassLoader)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }
        val key = other as ScriptCacheKey
        return (parentClassLoader.get() != null && key.parentClassLoader.get() != null
            && parentClassLoader.get() == key.parentClassLoader.get()
            && scriptTemplateTypeName == key.scriptTemplateTypeName
            && scriptSourceHash == key.scriptSourceHash)
    }

    override fun hashCode(): Int {
        var result = scriptTemplateTypeName.hashCode()
        result = 31 * result + scriptSourceHash.hashCode()
        parentClassLoader.get()?.let { loader ->
            result = 31 * result + loader.hashCode()
        }
        return result
    }
}
