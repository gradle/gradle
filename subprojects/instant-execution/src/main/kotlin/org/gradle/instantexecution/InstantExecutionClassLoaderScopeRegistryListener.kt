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

import org.gradle.initialization.ClassLoaderScopeRegistryListener
import org.gradle.initialization.DefaultClassLoaderScopeRegistry
import org.gradle.internal.classpath.ClassPath


internal
class InstantExecutionClassLoaderScopeRegistryListener : ClassLoaderScopeRegistryListener {

    lateinit var coreAndPluginsSpec: ClassLoaderScopeSpec

    private
    val scopeSpecs = mutableMapOf<String, ClassLoaderScopeSpec>()

    override fun rootScopeCreated(scopeId: String) {
        if (scopeId == DefaultClassLoaderScopeRegistry.CORE_AND_PLUGINS_NAME) {
            ClassLoaderScopeSpec(scopeId).let { root ->
                coreAndPluginsSpec = root
                scopeSpecs[scopeId] = coreAndPluginsSpec
            }
        }
    }

    override fun childScopeCreated(parentId: String, childId: String) {
        if (scopeSpecs.containsKey(parentId)) {
            ClassLoaderScopeSpec(childId).let { child ->
                scopeSpecs.getValue(parentId).children.add(child)
                scopeSpecs[childId] = child
            }
        }
    }

    override fun localClasspathAdded(scopeId: String, localClassPath: ClassPath) {
        if (scopeSpecs.containsKey(scopeId)) {
            scopeSpecs.getValue(scopeId).localClassPath += localClassPath
        }
    }

    override fun exportClasspathAdded(scopeId: String, exportClassPath: ClassPath) {
        if (scopeSpecs.containsKey(scopeId)) {
            scopeSpecs.getValue(scopeId).exportClassPath += exportClassPath
        }
    }
}


internal
class ClassLoaderScopeSpec(val id: String) {
    var localClassPath = ClassPath.EMPTY
    var exportClassPath = ClassPath.EMPTY
    val children = mutableListOf<ClassLoaderScopeSpec>()
}
