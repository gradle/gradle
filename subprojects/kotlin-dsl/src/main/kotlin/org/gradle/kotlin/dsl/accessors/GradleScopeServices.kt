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

import org.gradle.cache.internal.CacheKeyBuilder
import org.gradle.internal.execution.CachingResult
import org.gradle.internal.execution.ExecutionRequestContext
import org.gradle.internal.execution.WorkExecutor
import org.gradle.internal.fingerprint.impl.OutputFileCollectionFingerprinter
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.kotlin.dsl.cache.KotlinDslWorkspaceProvider


internal
object GradleScopeServices {

    @Suppress("unused")
    fun createPluginAccessorClassPathGenerator(
        classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
        outputFileCollectionFingerprinter: OutputFileCollectionFingerprinter,
        workExecutor: WorkExecutor<ExecutionRequestContext, CachingResult>,
        workspaceProvider: KotlinDslWorkspaceProvider
    ) = PluginAccessorClassPathGenerator(
        classLoaderHierarchyHasher,
        outputFileCollectionFingerprinter,
        workExecutor,
        workspaceProvider
    )

    @Suppress("unused")
    fun createProjectAccessorClassPathGenerator(
        cacheKeyBuilder: CacheKeyBuilder,
        outputFileCollectionFingerprinter: OutputFileCollectionFingerprinter,
        projectSchemaProvider: ProjectSchemaProvider,
        workExecutor: WorkExecutor<ExecutionRequestContext, CachingResult>,
        workspaceProvider: KotlinDslWorkspaceProvider
    ) = ProjectAccessorsClassPathGenerator(
        cacheKeyBuilder,
        outputFileCollectionFingerprinter,
        projectSchemaProvider,
        workExecutor,
        workspaceProvider
    )
}
