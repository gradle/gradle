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

import org.gradle.cache.IndexedCache
import org.gradle.internal.hash.HashCode
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING


/**
 * Content-addressed cache of Kotlin script compilation classpath snapshots, backed by
 * [KotlinDslClasspathEntrySnapshotStore].
 *
 * Key: a classpath entry's content hash (a jar or a class directory).
 * Value: two by-products of a single snapshotting pass over that entry, one per consumer of the
 * classpath:
 *  - an *ABI hash* in [snapshotIndex], used for compile avoidance. It digests only the entry's
 *    binary API (the class/method/field signatures visible to dependents, not method bodies or
 *    private members), and is folded into the workspace cache key — so a classpath change that
 *    leaves the API untouched never reaches the compiler.
 *  - a `snapshots/<contentHash>.snapshot` file, used for incremental compilation. BTA compares the
 *    classpath's snapshots against those it recorded on the previous compile to find which
 *    dependency classes changed, and recompiles only the scripts affected.
 *
 * Entries are immutable and content-addressed, so this cache needs no per-key locking:
 * the index relies on the store's coarse cache-level lock, and snapshot files publish via
 * atomic rename — the same content always yields the same bytes, so concurrent writers can't
 * disagree and a half-written file is never seen.
 */
@ServiceScope(Scope.UserHome::class)
class KotlinDslClasspathEntrySnapshotCache(
    val snapshotsCacheDirectory: Path,
    private val snapshotIndex: IndexedCache<HashCode, HashCode>,
) {
    fun snapshotAndAbiHashFor(contentHash: HashCode, generate: (Path) -> HashCode): SnapshotAndAbiHash {
        val snapshotFile = snapshotsCacheDirectory.resolve("$contentHash.snapshot")
        val abiHash = snapshotIndex.get(contentHash) { _ ->
            val tmp = Files.createTempFile(snapshotsCacheDirectory, "$contentHash.", ".snapshot.tmp")
            try {
                val abiRollup = generate(tmp)
                Files.move(tmp, snapshotFile, REPLACE_EXISTING, ATOMIC_MOVE)
                abiRollup
            } finally {
                Files.deleteIfExists(tmp)
            }
        }
        return SnapshotAndAbiHash(snapshotFile, abiHash)
    }

    class SnapshotAndAbiHash(val snapshotFile: Path, val abiHash: HashCode)
}
