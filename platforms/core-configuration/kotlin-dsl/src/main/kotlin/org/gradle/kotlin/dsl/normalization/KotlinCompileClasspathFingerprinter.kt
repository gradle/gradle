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

@file:OptIn(ExperimentalBuildToolsApi::class)

package org.gradle.kotlin.dsl.normalization

import com.google.common.collect.ImmutableMultimap
import org.gradle.internal.execution.FileCollectionFingerprinter
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.fingerprint.FileNormalizer
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint
import org.gradle.internal.fingerprint.FingerprintingStrategy
import org.gradle.internal.fingerprint.FingerprintingStrategy.COMPILE_CLASSPATH_IDENTIFIER
import org.gradle.internal.fingerprint.impl.EmptyCurrentFileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshotVisitResult
import org.gradle.kotlin.dsl.cache.KotlinDslClasspathEntrySnapshotCache
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.jvm.AccessibleClassSnapshot
import org.jetbrains.kotlin.buildtools.api.jvm.ClassSnapshot
import org.jetbrains.kotlin.buildtools.api.jvm.ClassSnapshotGranularity
import org.jetbrains.kotlin.buildtools.api.jvm.JvmPlatformToolchain
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmClasspathSnapshottingOperation
import java.io.File
import java.nio.file.Path


internal
class KotlinCompileClasspathFingerprinter(
    private val cache: KotlinDslClasspathEntrySnapshotCache
) : FileCollectionFingerprinter {

    private val snapshotter by lazy { Snapshotter() }

    override fun getNormalizer(): FileNormalizer {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun fingerprint(fileSystemSnapshot: FileSystemSnapshot, previousFingerprint: FileCollectionFingerprint?): CurrentFileCollectionFingerprint {
        val fingerprints: MutableMap<String, HashCode> = mutableMapOf()

        fileSystemSnapshot.accept { snapshot ->
            // if it doesn't exist, we ignore it
            if (snapshot is MissingFileSnapshot) {
                return@accept SnapshotVisitResult.CONTINUE
            }

            // if not jar file or class directory, we ignore it
            if (snapshot is RegularFileSnapshot && !snapshot.absolutePath.endsWith(".jar", ignoreCase = true)) {
                return@accept SnapshotVisitResult.CONTINUE
            }

            // Shared with BTA incremental compilation via the content-addressed snapshot store:
            // whichever layer asks first generates the BTA snapshot, the other gets a cache hit.
            // On hit, only the rollup HashCode is read from the in-memory index — the snapshot
            // file itself is not deserialized here.
            val abiHash = cache.snapshotAndAbiHashFor(snapshot.hash) { snapshotPath ->
                snapshotter.snapshotAndSave(File(snapshot.absolutePath), snapshotPath)
            }.abiHash
            fingerprints[snapshot.absolutePath] = abiHash

            // if it's a directory, we don't visit its content (i.e. we want to snapshot only top level directories)
            SnapshotVisitResult.SKIP_SUBTREE
        }

        return when {
            fingerprints.isEmpty() -> EmptyCurrentFileCollectionFingerprint(COMPILE_CLASSPATH_IDENTIFIER)
            else -> CurrentFileCollectionFingerprintImpl(fingerprints)
        }
    }

    override fun empty(): CurrentFileCollectionFingerprint {
        throw UnsupportedOperationException("Not implemented")
    }
}


private
class CurrentFileCollectionFingerprintImpl(private val fingerprints: Map<String, HashCode>) : CurrentFileCollectionFingerprint {

    private
    val hashCode: HashCode by lazy {
        val hasher = Hashing.newHasher()
        fingerprints.values.forEach {
            hasher.putHash(it)
        }
        hasher.hash()
    }

    override fun getHash(): HashCode = hashCode

    override fun getStrategyIdentifier(): String = COMPILE_CLASSPATH_IDENTIFIER

    override fun getSnapshot(): FileSystemSnapshot {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun isEmpty(): Boolean {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun archive(factory: CurrentFileCollectionFingerprint.ArchivedFileCollectionFingerprintFactory): FileCollectionFingerprint {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun getFingerprints(): Map<String, FileSystemLocationFingerprint> {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun getRootHashes(): ImmutableMultimap<String, HashCode> {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun wasCreatedWithStrategy(strategy: FingerprintingStrategy): Boolean {
        throw UnsupportedOperationException("Not implemented")
    }
}


private class Snapshotter {

    private val toolchains = KotlinToolchains.loadImplementation(this::class.java.classLoader)

    private val buildSession = toolchains.createBuildSession()

    private val jvmToolchains = toolchains.getToolchain(JvmPlatformToolchain::class.java)

    /**
     * Computes the BTA classpath snapshot for [file], writes it to [snapshotPath], and returns
     * the ABI-rollup hash derived from the same in-memory snapshot. Single BTA invocation
     * produces both outputs that the cache requires.
     */
    fun snapshotAndSave(file: File, snapshotPath: Path): HashCode {
        val operation = jvmToolchains.classpathSnapshottingOperationBuilder(file.toPath())
            .apply {
                this[JvmClasspathSnapshottingOperation.GRANULARITY] = ClassSnapshotGranularity.CLASS_LEVEL
                // Must match the option used by BTA incremental compilation in KotlinCompiler —
                // the snapshot file is shared by content hash, so a mismatch would mean the two
                // layers compute different snapshot bytes for the same input.
                this[JvmClasspathSnapshottingOperation.PARSE_INLINED_LOCAL_CLASSES] = true
            }.build()
        val snapshot = buildSession.executeOperation(operation)
        snapshot.saveSnapshot(snapshotPath)
        return rollupAbiHash(snapshot.classSnapshots)
    }

    // Duplicated, by design, in BTACompiler in KotlinCompiler.kt — see the comment there.
    private fun rollupAbiHash(classSnapshots: Map<String, ClassSnapshot>): HashCode {
        val hasher = Hashing.newHasher()
        classSnapshots.values
            .filterIsInstance<AccessibleClassSnapshot>()
            .forEach { hasher.putLong(it.classAbiHash) }
        return hasher.hash()
    }
}
