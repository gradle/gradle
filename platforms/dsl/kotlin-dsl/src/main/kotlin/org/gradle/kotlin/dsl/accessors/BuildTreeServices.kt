/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.BuildTreeScopedCacheBuilderFactory
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider

internal object BuildTreeServices : ServiceRegistrationProvider {

    @Provides
    fun createKotlinDslPluginEntryCache(
        cacheBuilderFactory: BuildTreeScopedCacheBuilderFactory,
        inMemoryCacheDecoratorFactory: InMemoryCacheDecoratorFactory,
        checksums: ChecksumService,
    ): KotlinDslPluginEntryCache =
        KotlinDslPluginEntryCache(
            cacheBuilderFactory,
            inMemoryCacheDecoratorFactory,
            checksums,
        )
}
