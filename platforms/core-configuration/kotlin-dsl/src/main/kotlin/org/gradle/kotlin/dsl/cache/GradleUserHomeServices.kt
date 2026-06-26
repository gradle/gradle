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
import org.gradle.cache.FineGrainedCacheCleanupStrategyFactory
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.service.PrivateService
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider


internal
object GradleUserHomeServices : ServiceRegistrationProvider {

    @Provides
    fun configure(registration: ServiceRegistration) {
        registration.add(KotlinDslWorkspaceProvider::class.java)
    }

    @Provides
    @PrivateService
    fun createKotlinDslIncrementalCompilationStore(
        cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
        fileAccessTimeJournal: FileAccessTimeJournal,
        cacheConfigurations: CacheConfigurationsInternal,
        cacheCleanupStrategyFactory: FineGrainedCacheCleanupStrategyFactory,
    ): KotlinDslIncrementalCompilationStore =
        KotlinDslIncrementalCompilationStore(cacheBuilderFactory, fileAccessTimeJournal, cacheConfigurations, cacheCleanupStrategyFactory)

    @Provides
    fun createKotlinDslIncrementalCompilationCache(
        store: KotlinDslIncrementalCompilationStore,
    ): KotlinDslIncrementalCompilationCache =
        KotlinDslIncrementalCompilationCache(store.cache, store.fileAccessTracker, store.softDeleter)

    @Provides
    @PrivateService
    fun createKotlinDslClasspathEntrySnapshotStore(
        cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
        inMemoryCacheDecoratorFactory: InMemoryCacheDecoratorFactory,
    ): KotlinDslClasspathEntrySnapshotStore =
        KotlinDslClasspathEntrySnapshotStore(cacheBuilderFactory, inMemoryCacheDecoratorFactory)

    @Provides
    fun createKotlinDslClasspathEntrySnapshotCache(
        store: KotlinDslClasspathEntrySnapshotStore,
    ): KotlinDslClasspathEntrySnapshotCache {
        val maxEntriesToKeep = 10_000
        return KotlinDslClasspathEntrySnapshotCache(
            store.snapshotsCacheDirectory,
            store.createIndexedCache(
                IndexedCacheParameters.of("kotlinDslClasspathSnapshotIndex", HashCode::class.java, HashCodeSerializer()),
                maxEntriesToKeep,
                true
            )
        )
    }
}
