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

import org.gradle.api.internal.initialization.ClassLoaderScope

import org.gradle.cache.internal.CrossBuildInMemoryCache
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory

import org.gradle.internal.Cast
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.HashCode

import org.gradle.kotlin.dsl.support.loggerFor

import java.io.File
import java.lang.ref.WeakReference
import javax.inject.Inject


internal
data class LoadedScriptClass<out T : Any>(
    val compiledScript: CompiledScript<T>,
    val scriptClass: Class<*>)


internal
class KotlinScriptClassloadingCache @Inject constructor(cacheFactory: CrossBuildInMemoryCacheFactory) {

    private
    val logger = loggerFor<KotlinScriptPluginFactory>()

    private
    val cache: CrossBuildInMemoryCache<ScriptCacheKey, LoadedScriptClass<*>> = cacheFactory.newCache()

    fun <T : Any> loadScriptClass(
        scriptBlock: ScriptBlock<T>,
        parentClassLoader: ClassLoader,
        scopeFactory: () -> ClassLoaderScope,
        compilation: (ScriptBlock<T>) -> CompiledScript<T>
    ): LoadedScriptClass<T> {

        val key = ScriptCacheKey(scriptBlock.scriptTemplate.qualifiedName!!, scriptBlock.sourceHash, parentClassLoader)
        val cached = cache.get(key)
        if (cached != null) {
            return Cast.uncheckedCast<LoadedScriptClass<T>>(cached)
        }

        val compiledScript = compilation(scriptBlock)

        logger.debug("Loading {} from {}", scriptBlock.scriptTemplate.simpleName, scriptBlock.displayName)
        val scriptClass = classFrom(compiledScript, scopeFactory())
        return LoadedScriptClass(compiledScript, scriptClass).also {
            cache.put(key, it)
        }
    }

    private
    fun classFrom(compiledScript: CompiledScript<*>, scope: ClassLoaderScope): Class<*> =
        classLoaderFor(compiledScript.location, scope)
            .loadClass(compiledScript.className)

    private
    fun classLoaderFor(location: File, scope: ClassLoaderScope) =
        scope
            .local(DefaultClassPath(location))
            .lock()
            .localClassLoader
}

private
class ScriptCacheKey(
    private val templateId: String,
    private val sourceHash: HashCode,
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
            && templateId == key.templateId
            && sourceHash == key.sourceHash)
    }

    override fun hashCode(): Int {
        var result = templateId.hashCode()
        result = 31 * result + sourceHash.hashCode()
        parentClassLoader.get()?.let { loader ->
            result = 31 * result + loader.hashCode()
        }
        return result
    }
}
