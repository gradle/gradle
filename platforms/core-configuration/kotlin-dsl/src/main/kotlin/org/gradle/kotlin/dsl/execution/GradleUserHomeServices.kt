/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.kotlin.dsl.execution

import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.classpath.ClasspathWalker
import org.gradle.internal.execution.FileCollectionSnapshotter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.BaseSerializerFactory.BOOLEAN_SERIALIZER
import org.gradle.internal.service.PrivateService
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider

internal
object GradleUserHomeServices : ServiceRegistrationProvider {

    @Provides
    fun createMetadataCompatibilityChecker(
        fileCollectionSnapshotter: FileCollectionSnapshotter,
        fileCollectionFactory: FileCollectionFactory,
        compatibilityCache: KotlinMetadataCompatibilityCache,
        classpathWalker: ClasspathWalker,
    ): KotlinMetadataCompatibilityChecker {
        return DefaultKotlinMetadataCompatibilityChecker(fileCollectionSnapshotter, fileCollectionFactory, compatibilityCache, classpathWalker)
    }

    @Provides
    fun createKotlinMetadataCompatibilityCache(
        store: CrossBuildFileHashCache,
    ): KotlinMetadataCompatibilityCache {
        /* KotlinMetadataCompatibilityCache keeps entries in this cache for jar files and class file directories.
         * At this level of granularity, for a project like `gradle/gradle` for example, it stores less than 300 entries.
         * Considering this, 10_000 seems like a safe enough value.
         *
         * One entry has a 128 bit HashCode as its key and a Boolean flag as its value, so 10_000 entries take up
         * less than 200 kilobytes of memory, which is not a lot.
         */
        val maxEntriesToKeep = 10_000

        return KotlinMetadataCompatibilityCache(
            store.createIndexedCache(
                IndexedCacheParameters.of("KotlinMetadataCompatibilityCache", HashCode::class.java, BOOLEAN_SERIALIZER),
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