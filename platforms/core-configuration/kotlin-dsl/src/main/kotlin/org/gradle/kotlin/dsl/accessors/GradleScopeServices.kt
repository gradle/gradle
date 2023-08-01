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

package org.gradle.kotlin.dsl.accessors

import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.kotlin.dsl.cache.KotlinDslWorkspaceProvider


internal
object GradleScopeServices {

    @Suppress("unused")
    fun createStage1BlocksAccessorClassPathGenerator(
        classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
        fileCollectionFactory: FileCollectionFactory,
        executionEngine: ExecutionEngine,
        inputFingerprinter: InputFingerprinter,
        workspaceProvider: KotlinDslWorkspaceProvider
    ) = Stage1BlocksAccessorClassPathGenerator(
        classLoaderHierarchyHasher,
        fileCollectionFactory,
        executionEngine,
        inputFingerprinter,
        workspaceProvider
    )

    @Suppress("unused")
    fun createProjectAccessorClassPathGenerator(
        fileCollectionFactory: FileCollectionFactory,
        projectSchemaProvider: ProjectSchemaProvider,
        executionEngine: ExecutionEngine,
        inputFingerprinter: InputFingerprinter,
        workspaceProvider: KotlinDslWorkspaceProvider
    ) = ProjectAccessorsClassPathGenerator(
        fileCollectionFactory,
        projectSchemaProvider,
        executionEngine,
        inputFingerprinter,
        workspaceProvider
    )
}
