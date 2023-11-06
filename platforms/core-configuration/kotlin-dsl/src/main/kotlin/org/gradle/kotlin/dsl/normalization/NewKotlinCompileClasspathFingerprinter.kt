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

@file:OptIn(ExperimentalBuildToolsApi::class)

package org.gradle.kotlin.dsl.normalization

import com.google.common.collect.ImmutableMultimap
import org.gradle.api.file.FileCollection
import org.gradle.internal.execution.FileCollectionFingerprinter
import org.gradle.internal.execution.FileCollectionSnapshotter
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
import org.gradle.internal.snapshot.SnapshotVisitResult
import org.jetbrains.kotlin.buildtools.api.CompilationService
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.jvm.AccessibleClassSnapshot
import org.jetbrains.kotlin.buildtools.api.jvm.ClassSnapshot
import org.jetbrains.kotlin.buildtools.api.jvm.ClassSnapshotGranularity
import java.io.File


internal
class NewKotlinCompileClasspathFingerprinter(
    val classpathSnapshotHashesCache: KotlinDslCompileAvoidanceClasspathHashCache,
    val fileCollectionSnapshotter: FileCollectionSnapshotter
) : FileCollectionFingerprinter { // TODO: rename/replace KotlinCompileClasspathFingerprinter

    override fun getNormalizer(): FileNormalizer {
        TODO("Not yet implemented")
    }

    override fun fingerprint(files: FileCollection): CurrentFileCollectionFingerprint {
        val fingerprints: MutableMap<String, HashCode> = mutableMapOf()

        fileCollectionSnapshotter.snapshot(files).snapshot.accept { snapshot ->
            val file = File(snapshot.absolutePath)

            // if not jar file or class directory, we ignore it
            if (file.isFile && !snapshot.absolutePath.endsWith(".jar", ignoreCase = true)) {
                return@accept SnapshotVisitResult.CONTINUE
            }

            val fingerprint = classpathSnapshotHashesCache.getHash(snapshot.hash) {
                computeHashForFile(file)
            }
            fingerprints[snapshot.absolutePath] = fingerprint

            // if it's a directory, we don't visit its content (i.e. we want to snapshot only top level directories)
            if (file.isDirectory) SnapshotVisitResult.SKIP_SUBTREE else SnapshotVisitResult.CONTINUE
        }

        return when {
            fingerprints.isEmpty() -> EmptyCurrentFileCollectionFingerprint(COMPILE_CLASSPATH_IDENTIFIER)
            else -> CurrentFileCollectionFingerprintImpl(fingerprints)
        }
    }

    private
    fun computeHashForFile(file: File): HashCode {
        val snapshots = compilationService.calculateClasspathSnapshot(file, ClassSnapshotGranularity.CLASS_LEVEL).classSnapshots
        return hash(snapshots)
    }

    private
    fun hash(snapshots: Map<String, ClassSnapshot>): HashCode {
        val hasher = Hashing.newHasher()
        snapshots.entries.stream()
            .filter { it.value is AccessibleClassSnapshot }
            .map { (it.value as AccessibleClassSnapshot).classAbiHash }
            .forEach {
                hasher.putLong(it)
            }
        return hasher.hash()
    }

    override fun fingerprint(snapshot: FileSystemSnapshot, previousFingerprint: FileCollectionFingerprint?): CurrentFileCollectionFingerprint {
        TODO("Not yet implemented")
    }

    override fun empty(): CurrentFileCollectionFingerprint {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    override fun archive(factory: CurrentFileCollectionFingerprint.ArchivedFileCollectionFingerprintFactory): FileCollectionFingerprint {
        TODO("Not yet implemented")
    }

    override fun getFingerprints(): Map<String, FileSystemLocationFingerprint> {
        TODO("Not yet implemented")
    }

    override fun getRootHashes(): ImmutableMultimap<String, HashCode> {
        TODO("Not yet implemented")
    }

    override fun wasCreatedWithStrategy(strategy: FingerprintingStrategy): Boolean {
        TODO("Not yet implemented")
    }
}


internal
val compilationService = compilationServiceFor<NewKotlinCompileClasspathFingerprinter>()


internal
inline fun <reified T : Any> compilationServiceFor(): CompilationService =
    CompilationService.loadImplementation(T::class.java.classLoader)
