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

import org.gradle.api.internal.initialization.ClassLoaderScopeIdentifier
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId
import org.gradle.initialization.ClassLoaderScopeId
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.initialization.ClassLoaderScopeRegistryListener
import org.gradle.initialization.ClassLoaderScopeRegistryListenerManager
import org.gradle.internal.buildtree.BuildTreeLifecycleListener
import org.gradle.internal.cc.impl.serialize.ClassLoaderRole
import org.gradle.internal.cc.impl.serialize.ClassLoaderScopeSpec
import org.gradle.internal.cc.impl.serialize.ScopeLookup
import org.gradle.internal.cc.impl.serialize.describeClassLoader
import org.gradle.internal.classloader.DelegatingClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.HashCode
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Closeable
import java.util.Collections
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

    /**
     * The class loaders that [scopeFor] reported as having no scope. A scope
     * must not arrive for one of these afterwards: the class was already
     * encoded against the Gradle runtime, which does not have it.
     */
    private
    val reportedAsUnknown = Collections.newSetFromMap(IdentityHashMap<ClassLoader, Boolean>())

    private
    var state = State.IDLE

    private
    enum class State {
        /** Before the build tree starts. No event was received. */
        IDLE,

        /** Registered as a listener. Events are received and recorded. */
        ACTIVE,

        /** Unregistered and the recorded state released. Terminal. */
        DISPOSED
    }

    /**
     * Starts recording the [ClassLoaderScopeSpec]s of this build tree.
     *
     * This runs before any scope of the build tree can be created, so that no
     * scope is missed. A build that does not need the scope tree stops the
     * recording again through [stopRecording].
     */
    override fun afterBuildTreeStart() {
        startRecording()
    }

    private
    fun startRecording() {
        synchronized(lock) {
            check(state == State.IDLE) {
                "Cannot start recording ClassLoaderScopes in state $state."
            }
            listenerManager.add(this)
            state = State.ACTIVE
        }
    }

    /**
     * Unregisters the listener and releases the recorded state.
     *
     * Callers invoke this more than once per build tree, so a call in
     * [State.DISPOSED] returns without doing anything. The state is terminal:
     * recording cannot start again for this build tree.
     *
     * TODO:configuration-cache make this unnecessary by deciding the cache strategy
     *  early, so the listener is only attached when the entry is stored.
     */
    fun stopRecording() {
        synchronized(lock) {
            if (state == State.DISPOSED) {
                return
            }
            check(state == State.ACTIVE) {
                "Cannot stop recording ClassLoaderScopes in state $state."
            }
            dispose()
        }
    }

    /**
     * Releases the state of a listener that the build tree no longer needs.
     *
     * Unlike [stopRecording] this accepts every state, because the services of
     * a build tree are closed even when the tree failed before it started.
     */
    override fun close() {
        synchronized(lock) {
            if (state != State.DISPOSED) {
                dispose()
            }
        }
    }

    private
    fun dispose() {
        if (state == State.ACTIVE) {
            listenerManager.remove(this)
        }
        scopeSpecs.clear()
        loaders.clear()
        reportedAsUnknown.clear()
        state = State.DISPOSED
    }

    override fun scopeFor(classLoader: ClassLoader?): Pair<ClassLoaderScopeSpec, ClassLoaderRole>? {
        synchronized(lock) {
            check(state == State.ACTIVE) {
                "Cannot look up a ClassLoaderScope in state $state."
            }
            // TODO:configuration-cache assert the spec can no longer change after it has been observed
            val scopeAndRole = loaders[classLoader]
            if (scopeAndRole == null && classLoader != null) {
                reportedAsUnknown.add(classLoader)
            }
            return scopeAndRole
        }
    }

    override fun describeKnownClassLoaders(): String =
        synchronized(lock) {
            if (loaders.isEmpty()) "No class loaders are currently known."
            else "These are the known class loaders:\n${loaders.keys.joinToString("\n") { "\t- $it" }}\n"
        }

    override fun childScopeCreated(parentId: ClassLoaderScopeId, childId: ClassLoaderScopeId, origin: ClassLoaderScopeOrigin?) {
        synchronized(lock) {
            check(state == State.ACTIVE) {
                "Received a ClassLoaderScope event for $childId in state $state."
            }
            if (scopeSpecs.containsKey(childId)) {
                // scope is being reused
                return
            }

            val parentIsRoot = parentId.parent == null
            val parent = if (parentIsRoot) {
                null
            } else {
                val lookupParent = scopeSpecs[parentId]
                check(lookupParent != null) {
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
            check(state == State.ACTIVE) {
                "Received a ClassLoader event for scope '$scopeId' in state $state."
            }
            check(classLoader !in reportedAsUnknown) {
                "ClassLoaderScope '$scopeId' was created for ${describeClassLoader(classLoader)} " +
                    "after that loader was reported as having no scope.\n" +
                    describeKnownClassLoaders() +
                    "Please report this error, run './gradlew --stop' and try again."
            }
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
}
