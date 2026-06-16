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

package org.gradle.kotlin.dsl.cache

import org.gradle.cache.FineGrainedPersistentCache
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Closeable


/**
 * Owns the [FineGrainedPersistentCache] backing [KotlinDslIncrementalCompilationCache], rooted at
 * `<gradleUserHome>/caches/<gradleVersion>/kotlin-dsl-ic/`. Holds only the mutable, per-script
 * incremental-compilation state.
 *
 * [close] is invoked by the service registry on shutdown.
 *
 * TODO: no disk cleanup — per-script entries grow unbounded. Drop-in via
 *  `FineGrainedCacheBuilder.withCleanupStrategy(...)`, mirroring KotlinDslWorkspaceProvider's cleanup
 *  wiring and touching each entry on use so LRU drops the right scripts.
 */
@ServiceScope(Scope.UserHome::class)
internal class KotlinDslIncrementalCompilationStore(
    cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
) : Closeable {

    val cache: FineGrainedPersistentCache = cacheBuilderFactory
        .createFineGrainedCacheBuilder("kotlin-dsl-ic")
        .withDisplayName("Kotlin DSL incremental compilation cache")
        .open()

    override fun close() {
        cache.close()
    }
}
