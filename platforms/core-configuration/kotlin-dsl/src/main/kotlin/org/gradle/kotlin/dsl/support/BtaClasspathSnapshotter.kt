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

@file:OptIn(ExperimentalBuildToolsApi::class)

package org.gradle.kotlin.dsl.support

import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.jvm.AccessibleClassSnapshot
import org.jetbrains.kotlin.buildtools.api.jvm.ClassSnapshot
import org.jetbrains.kotlin.buildtools.api.jvm.ClassSnapshotGranularity
import org.jetbrains.kotlin.buildtools.api.jvm.JvmPlatformToolchain
import org.jetbrains.kotlin.buildtools.api.jvm.JvmPlatformToolchain.Companion.jvm
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmClasspathSnapshottingOperation.Companion.GRANULARITY
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmClasspathSnapshottingOperation.Companion.PARSE_INLINED_LOCAL_CLASSES
import java.nio.file.Path


/**
 * Loaded once per daemon and shared by script compilation and classpath snapshotting:
 * the implementation lookup is a ServiceLoader scan over the BTA implementation jar.
 */
internal val kotlinToolchains: KotlinToolchains by lazy {
    KotlinToolchains.loadImplementation(BtaClasspathSnapshotter::class.java.classLoader)
}


internal object BtaClasspathSnapshotter {

    private var session: KotlinToolchains.BuildSession? = null

    /**
     * Snapshots [classpathEntry] with [session], shared by all compile-avoidance fingerprinting in
     * the process; created on demand, closed at the end of the build alongside the compilers' (see
     * `KotlinCompilerContextDisposer`).
     */
    fun snapshot(classpathEntry: Path, snapshotOutput: Path): HashCode =
        snapshot(kotlinToolchains.jvm, getOrCreateSession(), classpathEntry, snapshotOutput)

    // Only session access is synchronized; snapshotting runs outside the lock, concurrently.
    @Synchronized
    private fun getOrCreateSession(): KotlinToolchains.BuildSession =
        session ?: kotlinToolchains.createBuildSession().also { session = it }

    @Synchronized
    fun closeSession() {
        session?.close()
        session = null
    }

    /**
     * Snapshots [classpathEntry] (a jar or a class directory) with [jvm] and [session], writes the
     * snapshot to [snapshotOutput], and returns the ABI-rollup hash derived from the same in-memory
     * snapshot.
     */
    fun snapshot(
        jvm: JvmPlatformToolchain,
        session: KotlinToolchains.BuildSession,
        classpathEntry: Path,
        snapshotOutput: Path,
    ): HashCode {
        val operation = jvm.classpathSnapshottingOperationBuilder(classpathEntry).apply {
            this[GRANULARITY] = ClassSnapshotGranularity.CLASS_LEVEL
            // Track inline-emitted classes so an ABI change inside an inline body invalidates dependents.
            this[PARSE_INLINED_LOCAL_CLASSES] = true
        }.build()
        val snapshot = session.executeOperation(operation)
        snapshot.saveSnapshot(snapshotOutput)
        return rollupAbiHash(snapshot.classSnapshots)
    }

    /**
     * Projects the per-class ABI hashes into a single hash. Digests only the binary API each class
     * exposes to dependents, not method bodies or private members.
     */
    private fun rollupAbiHash(classSnapshots: Map<String, ClassSnapshot>): HashCode {
        val hasher = Hashing.newHasher()
        classSnapshots.values
            .filterIsInstance<AccessibleClassSnapshot>()
            .forEach { hasher.putLong(it.classAbiHash) }
        return hasher.hash()
    }
}
