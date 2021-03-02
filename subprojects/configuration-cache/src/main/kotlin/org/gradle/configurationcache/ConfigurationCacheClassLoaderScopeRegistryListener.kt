/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.internal.initialization.ClassLoaderScopeIdentifier
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.serialization.ClassLoaderRole
import org.gradle.configurationcache.serialization.ScopeLookup
import org.gradle.initialization.ClassLoaderScopeId
import org.gradle.initialization.ClassLoaderScopeRegistryListener
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.HashCode


internal
class ConfigurationCacheClassLoaderScopeRegistryListener(
    private val startParameter: ConfigurationCacheStartParameter
) : ClassLoaderScopeRegistryListener, ScopeLookup, AutoCloseable {
    private
    val scopeSpecs = LinkedHashMap<ClassLoaderScopeId, ClassLoaderScopeSpec>()

    private
    val loaders = mutableMapOf<ClassLoader, Pair<ClassLoaderScopeSpec, ClassLoaderRole>>()

    val scopes: Collection<ClassLoaderScopeSpec>
        get() = scopeSpecs.values

    var closed = false

    /**
     * Stops recording [ClassLoaderScopeSpec]s and releases any recorded state.
     */
    fun dispose() {
        // TODO:configuration-cache find a way to make `dispose` unnecessary;
        //  maybe by extracting an `ConfigurationCacheBuildDefinition` service
        //  from DefaultConfigurationCacheHost so a decision based on the configured
        //  configuration cache strategy (none, store or load) can be taken early on.
        //  The listener only needs to be attached in the `store` state.
        closed = true
        scopeSpecs.clear()
        loaders.clear()
    }

    override fun close() {
        dispose()
    }

    override fun scopeFor(classLoader: ClassLoader?): Pair<ClassLoaderScopeSpec, ClassLoaderRole>? {
        return loaders[classLoader]
    }

    override fun rootScopeCreated(rootScopeId: ClassLoaderScopeId) {
        if (closed || !startParameter.isEnabled) {
            return
        }

        if (scopeSpecs.containsKey(rootScopeId)) {
            // scope is being reused
            return
        }
        val root = ClassLoaderScopeSpec(null, rootScopeId.name)
        scopeSpecs[rootScopeId] = root
    }

    override fun childScopeCreated(parentId: ClassLoaderScopeId, childId: ClassLoaderScopeId) {
        if (closed || !startParameter.isEnabled) {
            return
        }

        if (scopeSpecs.containsKey(childId)) {
            // scope is being reused
            return
        }

        // We may not know of the parent if it is from another build
        // https://github.com/gradle/gradle/pull/16351
        val parent = scopeSpecs[parentId] ?: return

        val child = ClassLoaderScopeSpec(parent, childId.name)
        scopeSpecs[childId] = child
    }

    override fun classloaderCreated(scopeId: ClassLoaderScopeId, classLoaderId: ClassLoaderId, classLoader: ClassLoader, classPath: ClassPath, implementationHash: HashCode?) {
        if (closed || !startParameter.isEnabled) {
            return
        }

        val spec = scopeSpecs[scopeId]
        require(spec != null)
        // TODO - a scope can currently potentially have multiple export and local ClassLoaders but we're assuming one here
        //  Rather than fix the assumption here, it would be better to rework the scope implementation so that it produces no more than one export and one local ClassLoader
        val local = scopeId is ClassLoaderScopeIdentifier && scopeId.localId() == classLoaderId
        if (local) {
            spec.localClassPath = classPath
            spec.localImplementationHash = implementationHash
        } else {
            spec.exportClassPath = classPath
            spec.exportImplementationHash = implementationHash
        }
        loaders[classLoader] = Pair(spec, ClassLoaderRole(local))
    }
}


internal
class ClassLoaderScopeSpec(
    val parent: ClassLoaderScopeSpec?,
    val name: String
) {
    var localClassPath: ClassPath = ClassPath.EMPTY
    var localImplementationHash: HashCode? = null
    var exportClassPath: ClassPath = ClassPath.EMPTY
    var exportImplementationHash: HashCode? = null

    override fun toString(): String {
        return if (parent != null) {
            "$parent:$name"
        } else {
            name
        }
    }
}
