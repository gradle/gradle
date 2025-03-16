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

package org.gradle.internal.cc.impl

import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.initialization.ClassLoaderScopeIdentifier
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId
import org.gradle.initialization.ClassLoaderScopeId
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.initialization.ClassLoaderScopeRegistryListener
import org.gradle.initialization.ClassLoaderScopeRegistryListenerManager
import org.gradle.internal.buildtree.BuildTreeLifecycleListener
import org.gradle.internal.cc.impl.serialize.ClassLoaderScopeSpec
import org.gradle.internal.cc.impl.serialize.ScopeLookup
import org.gradle.internal.cc.impl.serialize.describeClassLoader
import org.gradle.internal.cc.impl.serialize.describeKnownClassLoaders
import org.gradle.internal.classloader.DelegatingClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.graph.ClassLoaderRole
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Closeable
import java.util.IdentityHashMap


@ServiceScope(Scope.BuildTree::class)
internal
class ConfigurationCacheClassLoaderScopeRegistryListener(
    private val listenerManager: ClassLoaderScopeRegistryListenerManager
) : ClassLoaderScopeRegistryListener, ScopeLookup, BuildTreeLifecycleListener, Closeable {

    private
    val lock = Any()

    private
    val scopeSpecs = mutableMapOf<ClassLoaderScopeId, ClassLoaderScopeSpec>()

    private
    val loaders = IdentityHashMap<ClassLoader, Pair<ClassLoaderScopeSpec, ClassLoaderRole>>()

    private
    var disposed = false

    override fun afterStart() {
        synchronized(lock) {
            assertNotDisposed("afterStart")
            listenerManager.add(this)
        }
    }

    /**
     * Stops recording [ClassLoaderScopeSpec]s and releases any recorded state.
     */
    fun dispose() {
        synchronized(lock) {
            if (disposed) {
                return
            }
            // TODO:configuration-cache find a way to make `dispose` unnecessary;
            //  maybe by extracting an `ConfigurationCacheBuildDefinition` service
            //  from DefaultConfigurationCacheHost so a decision based on the configured
            //  configuration cache strategy (none, store or load) can be taken early on.
            //  The listener only needs to be attached in the `store` state.
            scopeSpecs.clear()
            loaders.clear()
            listenerManager.remove(this)
            disposed = true
        }
    }

    override fun close() {
        dispose()
    }

    override fun scopeFor(classLoader: ClassLoader?): Pair<ClassLoaderScopeSpec, ClassLoaderRole>? {
        synchronized(lock) {
            assertNotDisposed("scopeFor")
            return loaders[classLoader]
        }
    }

    override val knownClassLoaders: Set<ClassLoader>
        get() = synchronized(lock) {
            ImmutableSet.copyOf(loaders.keys)
        }

    override fun childScopeCreated(parentId: ClassLoaderScopeId, childId: ClassLoaderScopeId, origin: ClassLoaderScopeOrigin?) {
        synchronized(lock) {
            assertNotDisposed("childScopeCreated")
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

            val child = ClassLoaderScopeSpec(parent, childId.name, origin)
            scopeSpecs[childId] = child
        }
    }

    override fun classloaderCreated(scopeId: ClassLoaderScopeId, classLoaderId: ClassLoaderId, classLoader: ClassLoader, classPath: ClassPath, implementationHash: HashCode?) {
        require(classLoader !is DelegatingClassLoader) {
            "Unexpected delegating ${describeClassLoader(classLoader)} with id '$classLoaderId' " +
                "for scope '$scopeId' with classpath '$classPath'.\n" +
                describeKnownClassLoaders() +
                "Please report this error, run './gradlew --stop' and try again."
        }
        synchronized(lock) {
            assertNotDisposed("classloaderCreated")
            val spec = scopeSpecs[scopeId]
            check(spec != null) {
                "Spec for ClassLoaderScope '$scopeId' not found!"
            }
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

    private
    fun assertNotDisposed(method: String) {
        check(!disposed) {
            "${javaClass.simpleName}.$method cannot be used after being disposed of."
        }
    }
}
