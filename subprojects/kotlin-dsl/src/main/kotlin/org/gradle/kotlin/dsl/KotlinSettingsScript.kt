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

package org.gradle.kotlin.dsl

import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.DefaultFileOperations
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.invocation.Gradle
import org.gradle.internal.service.ServiceRegistry
import org.gradle.kotlin.dsl.support.get
import java.io.File


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
