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
import org.gradle.initialization.ClassLoaderScopeRegistryListenerManager
import org.gradle.internal.buildtree.BuildTreeLifecycleListener
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.HashCode
import java.io.Closeable


internal
class ConfigurationCacheClassLoaderScopeRegistryListener(

    private
    val startParameter: ConfigurationCacheStartParameter,

    private
    val listenerManager: ClassLoaderScopeRegistryListenerManager

) : ClassLoaderScopeRegistryListener, ScopeLookup, BuildTreeLifecycleListener, Closeable {

    private
    val scopeSpecs = mutableMapOf<ClassLoaderScopeId, ClassLoaderScopeSpec>()

    private
    val loaders = mutableMapOf<ClassLoader, Pair<ClassLoaderScopeSpec, ClassLoaderRole>>()

    override fun afterStart() {
        if (startParameter.isEnabled) {
            listenerManager.add(this)
        }
    }

    /**
     * Stops recording [ClassLoaderScopeSpec]s and releases any recorded state.
     */
    fun dispose() {
        // TODO:configuration-cache find a way to make `dispose` unnecessary;
        //  maybe by extracting an `ConfigurationCacheBuildDefinition` service
        //  from DefaultConfigurationCacheHost so a decision based on the configured
        //  configuration cache strategy (none, store or load) can be taken early on.
        //  The listener only needs to be attached in the `store` state.
        scopeSpecs.clear()
        loaders.clear()
        listenerManager.remove(this)
    }

    override fun close() {
        dispose()
    }

    override fun scopeFor(classLoader: ClassLoader?): Pair<ClassLoaderScopeSpec, ClassLoaderRole>? {
        return loaders[classLoader]
    }

    override fun childScopeCreated(parentId: ClassLoaderScopeId, childId: ClassLoaderScopeId) {
        if (scopeSpecs.containsKey(childId)) {
            // scope is being reused
            return
        }

        val parentIsRoot = parentId.parent == null
        val parent = if (parentIsRoot) {
            null
        } else {
            val lookupParent = scopeSpecs[parentId]
            require(lookupParent != null) {
                "Cannot find parent $parentId for child scope $childId"
            }
            lookupParent
        }

        val child = ClassLoaderScopeSpec(parent, childId.name)
        scopeSpecs[childId] = child
    }

    override fun classloaderCreated(scopeId: ClassLoaderScopeId, classLoaderId: ClassLoaderId, classLoader: ClassLoader, classPath: ClassPath, implementationHash: HashCode?) {
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

    override fun toString(): String {
        return if (parent != null) {
            "$parent:$name"
        } else {
            name
        }
    }
}
