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

package org.gradle.kotlin.dsl.resolver.internal

import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.File


/**
 * Memoizes Gradle distribution source resolution, which is invariant across the build tree.
 *
 * The IDE creates a fresh resolver per script model, so without this the network-bound resolution, and its
 * failure warning, would repeat for every script. Build-tree scoped, so a later build retries.
 */
@ServiceScope(Scope.BuildTree::class)
internal class SourceDistributionResolutionCache {

    @Volatile
    private var cachedSourceDirs: Collection<File>? = null

    fun computeIfAbsent(resolve: () -> Collection<File>): Collection<File> =
        cachedSourceDirs ?: synchronized(this) {
            cachedSourceDirs ?: resolve().also { cachedSourceDirs = it }
        }
}
