/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.kotlin.dsl.normalization

import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.service.PrivateService
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider


internal
object GradleUserHomeServices : ServiceRegistrationProvider {


    @Provides
    fun createClasspathSnapshotHashesCache(
        store: CrossBuildFileHashCache,
    ): KotlinDslCompileAvoidanceClasspathHashCache {
        /* KotlinCompileClasspathFingerprinter keeps entries in this cache for jar files and class file directories.
         * At this level of granularity, for a project like `gradle/gradle` for example, it stores less than 300 entries.
         * Considering this, 10_000 seems like a safe enough value.
         *
         * One entry has a 128 bit HashCode both as its key and value, so 10_000 entries take up roughly 300+ kilobytes
         * of memory, which is not a lot.
         */
        val maxEntriesToKeep = 10_000

        return KotlinDslCompileAvoidanceClasspathHashCache(
            store.createIndexedCache(
                IndexedCacheParameters.of("kotlinDslCompileAvoidanceClasspathHashCache", HashCode::class.java, HashCodeSerializer()),
                maxEntriesToKeep,
                true
            )
        )
    }

    @Provides
    @PrivateService
    fun createCrossBuildFileHashCache(
        cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
        inMemoryCacheDecoratorFactory: InMemoryCacheDecoratorFactory
    ): CrossBuildFileHashCache =
        CrossBuildFileHashCache(cacheBuilderFactory, inMemoryCacheDecoratorFactory, CrossBuildFileHashCache.Kind.FILE_HASHES)
}
