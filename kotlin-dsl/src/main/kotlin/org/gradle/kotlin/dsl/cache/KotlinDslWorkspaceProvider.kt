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

package org.gradle.kotlin.dsl.cache

import org.gradle.api.internal.cache.StringInterner
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCache
import org.gradle.internal.execution.workspace.WorkspaceProvider
import org.gradle.internal.execution.workspace.impl.DefaultImmutableWorkspaceProvider
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import java.io.Closeable


class KotlinDslWorkspaceProvider(
    cacheRepository: GlobalScopedCache,
    fileAccessTimeJournal: FileAccessTimeJournal,
    inMemoryCacheDecoratorFactory: InMemoryCacheDecoratorFactory,
    stringInterner: StringInterner,
    classLoaderHasher: ClassLoaderHierarchyHasher
) : Closeable {

    private
    val kotlinDslWorkspace = DefaultImmutableWorkspaceProvider.withBuiltInHistory(
        cacheRepository
            .cache("kotlin-dsl")
            .withDisplayName("kotlin-dsl"),
        fileAccessTimeJournal,
        inMemoryCacheDecoratorFactory,
        stringInterner,
        classLoaderHasher,
        2 // scripts and accessors caches sit below the root directory
    )

    val accessors = subWorkspace("accessors")

    val scripts = subWorkspace("scripts")

    override fun close() =
        kotlinDslWorkspace.close()

    private
    fun subWorkspace(prefix: String): WorkspaceProvider = object : WorkspaceProvider {
        override fun <T : Any> withWorkspace(path: String, action: WorkspaceProvider.WorkspaceAction<T>): T =
            kotlinDslWorkspace.withWorkspace("$prefix/$path", action)
    }
}
