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

<<<<<<<< HEAD:platforms/core-configuration/configuration-cache/src/jmh/kotlin/org/gradle/internal/serialize/graph/FileTreeGeneration.kt
package org.gradle.internal.serialize.graph

import java.nio.file.Files
import java.nio.file.Path

internal fun generateFileTree(root: Path, depth: Int, width: Int) {
    if (depth == 0) return
    Files.createDirectories(root)

    for (i in 1..width) {
        val sub = root.resolve("sub_$i")
        generateFileTree(sub, depth - 1, width)
    }
========
package org.gradle.kotlin.dsl.normalization

import org.gradle.cache.IndexedCache
import org.gradle.internal.hash.HashCode
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scope.UserHome::class)
class KotlinDslCompileAvoidanceClasspathHashCache(val cache: IndexedCache<HashCode, HashCode>) {


    fun getHash(checksum: HashCode, supplier: () -> HashCode) = cache.get(checksum, supplier)
>>>>>>>> master:platforms/core-configuration/kotlin-dsl/src/main/kotlin/org/gradle/kotlin/dsl/normalization/KotlinDslCompileAvoidanceClasspathHashCache.kt
}
