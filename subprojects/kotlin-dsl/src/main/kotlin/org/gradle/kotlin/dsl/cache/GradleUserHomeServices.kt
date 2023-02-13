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
import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.hash.ClassLoaderHierarchyHasher


internal
object GradleUserHomeServices {

    @Suppress("unused")
    fun createKotlinDslWorkspaceProvider(
        cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
        fileAccessTimeJournal: FileAccessTimeJournal,
        inMemoryCacheDecoratorFactory: InMemoryCacheDecoratorFactory,
        stringInterner: StringInterner,
        classLoaderHasher: ClassLoaderHierarchyHasher,
        cacheConfigurations: CacheConfigurationsInternal
    ): KotlinDslWorkspaceProvider {
        return KotlinDslWorkspaceProvider(
            cacheBuilderFactory,
            fileAccessTimeJournal,
            inMemoryCacheDecoratorFactory,
            stringInterner,
            classLoaderHasher,
            cacheConfigurations
        )
    }
}
