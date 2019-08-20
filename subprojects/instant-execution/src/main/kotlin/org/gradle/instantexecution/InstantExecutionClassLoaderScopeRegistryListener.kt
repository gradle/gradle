/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.initialization.ClassLoaderScopeId
import org.gradle.initialization.ClassLoaderScopeRegistryListener
import org.gradle.initialization.DefaultClassLoaderScopeRegistry
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.event.ListenerManager


internal
class InstantExecutionClassLoaderScopeRegistryListener : ClassLoaderScopeRegistryListener {

    var coreAndPluginsSpec: ClassLoaderScopeSpec? = null

    private
    val scopeSpecs = mutableMapOf<ClassLoaderScopeId, ClassLoaderScopeSpec>()

    private
    var manager: ListenerManager? = null

    fun attach(manager: ListenerManager) {
        require(this.manager == null)
        this.manager = manager
        manager.addListener(this)
    }

    /**
     * Stops recording [ClassLoaderScopeSpec]s and releases any recorded state.
     */
    fun dispose() {
        // TODO:instant-execution find a way to make `dispose` unnecessary;
        //  maybe by extracting an `InstantExecutionBuildDefinition` service
        //  from DefaultInstantExecutionHost so a decision based on the configured
        //  instant execution strategy (none, store or load) can be taken early on.
        //  The listener only needs to be attached in the `store` state.
        coreAndPluginsSpec = null
        scopeSpecs.clear()
        detach()
    }

    private
    fun detach() {
        manager?.removeListener(this)
        manager = null
    }

    override fun rootScopeCreated(rootScopeId: ClassLoaderScopeId) {
        if (rootScopeId.name === DefaultClassLoaderScopeRegistry.CORE_AND_PLUGINS_NAME) {
            ClassLoaderScopeSpec(rootScopeId.name).let { root ->
                coreAndPluginsSpec = root
                scopeSpecs[rootScopeId] = root
            }
        }
    }

    override fun childScopeCreated(parentId: ClassLoaderScopeId, childId: ClassLoaderScopeId) {
        scopeSpecs[parentId]?.let { scopeSpec ->
            ClassLoaderScopeSpec(childId.name).let { child ->
                scopeSpec.children.add(child)
                scopeSpecs[childId] = child
            }
        }
    }

    override fun localClasspathAdded(scopeId: ClassLoaderScopeId, localClassPath: ClassPath) {
        scopeSpecs[scopeId]?.localClassPath?.add(localClassPath)
    }

    override fun exportClasspathAdded(scopeId: ClassLoaderScopeId, exportClassPath: ClassPath) {
        scopeSpecs[scopeId]?.exportClassPath?.add(exportClassPath)
    }
}


internal
class ClassLoaderScopeSpec(val name: String) {
    val localClassPath = mutableListOf<ClassPath>()
    val exportClassPath = mutableListOf<ClassPath>()
    val children = mutableListOf<ClassLoaderScopeSpec>()
}
