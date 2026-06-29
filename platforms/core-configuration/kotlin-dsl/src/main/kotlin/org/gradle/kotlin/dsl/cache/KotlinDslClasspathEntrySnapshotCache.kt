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

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.gradle.internal.file.FileAccessTracker
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
 * Value: two by-products of a single snapshotting pass over that entry, each persisted as its own
 * content-addressed file under `snapshots/`, one per consumer of the classpath:
 *  - `<contentHash>.abi` — an *ABI hash* used for compile avoidance. It digests only the entry's
 *    binary API (the class/method/field signatures visible to dependents, not method bodies or
 *    private members), and is folded into the workspace cache key — so a classpath change that
 *    leaves the API untouched never reaches the compiler. Read by [abiHashFor] on every build.
 *  - `<contentHash>.snapshot` — the BTA snapshot used for incremental compilation. BTA compares the
 *    classpath's snapshots against those it recorded on the previous compile to find which
 *    dependency classes changed, and recompiles only the scripts affected. Read by [snapshotFileFor]
 *    only when a script is actually (re)compiled.
 *
 * Both files are immutable and content-addressed, so this cache needs no per-key locking: they
 * publish via atomic rename, producing identical bytes from any writer with no half-written file
 * ever seen. The store's LRU cleanup reclaims the two files independently, matching their access
 * frequencies — the abi file stays hot (avoidance reads it every build) while an untouched snapshot
 * ages out. Each lookup marks its file accessed and regenerates it if cleanup already removed it. If
 * a snapshot is reclaimed between [snapshotFileFor] returning and BTA reading it, incremental
 * compilation falls back to a full compile, so the build never fails over a missing snapshot. See
 * [KotlinDslClasspathEntrySnapshotStore]'s cleanup note.
 */
@ServiceScope(Scope.UserHome::class)
internal class KotlinDslClasspathEntrySnapshotCache(
    private val snapshotsCacheDirectory: Path,
    private val fileAccessTracker: FileAccessTracker,
) {
    private val abiHashInMemoryMap: Cache<HashCode, HashCode> =
        CacheBuilder.newBuilder().maximumSize(MAX_ABI_HASHES_IN_MEMORY).build()

    /**
     * The ABI hash for compile avoidance.
     */
    fun abiHashFor(contentHash: HashCode, generate: (Path) -> HashCode): HashCode {
        val abiFile = abiFile(contentHash)
        fileAccessTracker.markAccessed(abiFile.toFile())
        abiHashInMemoryMap.getIfPresent(contentHash)?.let { return it }
        readAbiHash(abiFile)?.let {
            abiHashInMemoryMap.put(contentHash, it)
            return it
        }
        return generateAndPersist(contentHash, generate)
    }

    /**
     * The snapshot file for incremental compilation.
     */
    fun snapshotFileFor(contentHash: HashCode, generate: (Path) -> HashCode): Path {
        val snapshotFile = snapshotFile(contentHash)
        fileAccessTracker.markAccessed(snapshotFile.toFile())
        if (Files.notExists(snapshotFile)) {
            generateAndPersist(contentHash, generate)
        }
        return snapshotFile
    }

    /**
     * Runs the single snapshotting pass and publishes both by-products: [generate] writes the
     * snapshot into a temp file we atomically rename into place, and returns the abi rollup we store
     * beside it. The two files aren't published as one atomic step, but a crash between the writes is
     * harmless: the values are immutable and content-addressed, so the next lookup of whichever file
     * is missing regenerates it identically.
     */
    private fun generateAndPersist(contentHash: HashCode, generate: (Path) -> HashCode): HashCode {
        val abiHash = publishSnapshot(contentHash, generate)
        publishAbiHash(contentHash, abiHash)
        abiHashInMemoryMap.put(contentHash, abiHash)
        return abiHash
    }

    private fun publishSnapshot(contentHash: HashCode, generate: (Path) -> HashCode): HashCode {
        val tmp = Files.createTempFile(snapshotsCacheDirectory, "$contentHash.", ".snapshot.tmp")
        return try {
            val abiHash = generate(tmp)
            Files.move(tmp, snapshotFile(contentHash), REPLACE_EXISTING, ATOMIC_MOVE)
            abiHash
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun publishAbiHash(contentHash: HashCode, abiHash: HashCode) {
        val tmp = Files.createTempFile(snapshotsCacheDirectory, "$contentHash.", ".abi.tmp")
        try {
            Files.write(tmp, abiHash.toByteArray())
            Files.move(tmp, abiFile(contentHash), REPLACE_EXISTING, ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    /** The persisted ABI hash for [abiFile], or null if no sidecar has been written for it yet. */
    private fun readAbiHash(abiFile: Path): HashCode? =
        if (Files.exists(abiFile)) HashCode.fromBytes(Files.readAllBytes(abiFile)) else null

    private fun snapshotFile(contentHash: HashCode): Path =
        snapshotsCacheDirectory.resolve("$contentHash.snapshot")

    private fun abiFile(contentHash: HashCode): Path =
        snapshotsCacheDirectory.resolve("$contentHash.abi")
}


private const val MAX_ABI_HASHES_IN_MEMORY = 10_000L
