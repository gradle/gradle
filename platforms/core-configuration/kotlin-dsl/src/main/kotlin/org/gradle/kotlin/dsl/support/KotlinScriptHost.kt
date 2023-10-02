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
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.file.DefaultFileOperations
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.service.ServiceRegistry
import org.gradle.kotlin.dsl.*
import org.gradle.util.internal.ConfigureUtil.configureByMap
import java.io.File


class KotlinScriptHost<out T : Any> internal constructor(
    val target: T,
    val scriptSource: ScriptSource,
    internal val scriptHandler: ScriptHandler,
    internal val targetScope: ClassLoaderScope,
    private val baseScope: ClassLoaderScope,
    private val serviceRegistry: ServiceRegistry
) {

    internal
    val fileName = scriptSource.fileName

    internal
    val fileOperations: FileOperations by unsafeLazy {
        fileOperationsFor(serviceRegistry, scriptSource.resource.location.file?.parentFile)
    }

    internal
    val processOperations: ProcessOperations by unsafeLazy {
        serviceRegistry.get()
    }

    internal
    val objectFactory: ObjectFactory by unsafeLazy {
        serviceRegistry.get()
    }

    internal
    val temporaryFileProvider: TemporaryFileProvider by unsafeLazy {
        // GradleUserHomeTemporaryFileProvider must be used instead of the TemporaryFileProvider.
        // In this scope the TemporaryFileProvider would be provided by the ProjectScopeServices.
        // That would generate this temporary directory inside the project build directory.
        serviceRegistry.get<GradleUserHomeTemporaryFileProvider>()
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
            target
        )
}


internal
fun fileOperationsFor(settings: Settings): FileOperations =
    fileOperationsFor(settings.gradle, settings.rootDir)


internal
fun fileOperationsFor(gradle: Gradle, baseDir: File?): FileOperations =
    fileOperationsFor((gradle as GradleInternal).services, baseDir)


internal
fun fileOperationsFor(services: ServiceRegistry, baseDir: File?): FileOperations {
    val fileLookup = services.get<FileLookup>()
    val fileResolver = baseDir?.let { fileLookup.getFileResolver(it) } ?: fileLookup.fileResolver
    val fileCollectionFactory = services.get<FileCollectionFactory>().withResolver(fileResolver)
    return DefaultFileOperations.createSimple(
        fileResolver,
        fileCollectionFactory,
        services
    )
}
