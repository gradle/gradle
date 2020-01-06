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

package org.gradle.kotlin.dsl.support

import org.gradle.api.Action
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction
import org.gradle.api.plugins.ObjectConfigurationAction

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.internal.service.ServiceRegistry

import org.gradle.kotlin.dsl.fileOperationsFor
import org.gradle.kotlin.dsl.invoke

import org.gradle.util.ConfigureUtil.configureByMap


class KotlinScriptHost<out T : Any>(
    val target: T,
    val scriptSource: ScriptSource,
    val scriptHandler: ScriptHandler,
    val targetScope: ClassLoaderScope,
    val baseScope: ClassLoaderScope,
    private val serviceRegistry: ServiceRegistry
) {

    val fileName = scriptSource.fileName!!

    internal
    val fileOperations: FileOperations by unsafeLazy {
        fileOperationsFor(serviceRegistry, scriptSource.resource.location.file?.parentFile)
    }

    internal
    val processOperations: ProcessOperations by unsafeLazy {
        serviceRegistry.get<ProcessOperations>()
    }

    internal
    fun applyObjectConfigurationAction(configure: Action<in ObjectConfigurationAction>) {
        executeObjectConfigurationAction { configure(it) }
    }

    internal
    fun applyObjectConfigurationAction(options: Map<String, *>) {
        executeObjectConfigurationAction { configureByMap(options, it) }
    }

    private
    inline fun executeObjectConfigurationAction(configure: (ObjectConfigurationAction) -> Unit) {
        createObjectConfigurationAction().also(configure).execute()
    }

    private
    fun createObjectConfigurationAction() =
        DefaultObjectConfigurationAction(
            fileOperations.fileResolver,
            serviceRegistry.get(),
            serviceRegistry.get(),
            baseScope,
            serviceRegistry.get(),
            target)
}
