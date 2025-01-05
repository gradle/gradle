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

import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.cache.CacheCleanupStrategyFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider
import org.gradle.internal.execution.workspace.impl.CacheBasedImmutableWorkspaceProvider
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Closeable


@ServiceScope(Scope.UserHome::class)
internal
class KotlinDslWorkspaceProvider(
    cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
    fileAccessTimeJournal: FileAccessTimeJournal,
    cacheConfigurations: CacheConfigurationsInternal,
    cacheCleanupStrategyFactory: CacheCleanupStrategyFactory
) : Closeable {

    private
    val kotlinDslWorkspace = CacheBasedImmutableWorkspaceProvider.createWorkspaceProvider(
        cacheBuilderFactory
            .createCacheBuilder("kotlin-dsl")
            .withDisplayName("kotlin-dsl"),
        fileAccessTimeJournal,
        2, // scripts and accessors caches sit below the root directory
        cacheConfigurations,
        cacheCleanupStrategyFactory
    )

    val accessors = subWorkspace("accessors")

    val scripts = subWorkspace("scripts")

    override fun close() =
        kotlinDslWorkspace.close()

    private
    fun subWorkspace(prefix: String): ImmutableWorkspaceProvider =
        ImmutableWorkspaceProvider { path -> kotlinDslWorkspace.getWorkspace("$prefix/$path") }
}
