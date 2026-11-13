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

import org.gradle.internal.file.FileAccessTracker
import org.gradle.internal.hash.Hashing
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path


class KotlinDslClasspathEntrySnapshotCacheTest {

    @JvmField
    @Rule
    val tmp = TemporaryFolder()

    private val contentHash = Hashing.hashString("classpath entry")
    private val abiHash = Hashing.hashString("abi")

    private fun cacheIn(dir: Path) = KotlinDslClasspathEntrySnapshotCache(dir, FileAccessTracker { })

    private fun writeSnapshot(path: Path) = abiHash.also { Files.write(path, byteArrayOf(1, 2, 3)) }

    @Test
    fun `publishes snapshot and abi hash on first lookup`() {
        val dir = tmp.newFolder().toPath()
        val cache = cacheIn(dir)

        assertThat(cache.abiHashFor(contentHash, ::writeSnapshot), equalTo(abiHash))

        assertThat(Files.readAllBytes(dir.resolve("$contentHash.snapshot")).toList(), equalTo(listOf<Byte>(1, 2, 3)))
        assertThat(Files.list(dir).use { it.count() }, equalTo(2L))
    }

    @Test
    fun `can republish a snapshot that another reader holds open`() {
        val dir = tmp.newFolder().toPath()
        val snapshotFile = dir.resolve("$contentHash.snapshot")
        cacheIn(dir).abiHashFor(contentHash, ::writeSnapshot)
        Files.delete(dir.resolve("$contentHash.abi"))

        val republished = Files.newInputStream(snapshotFile).use {
            cacheIn(dir).abiHashFor(contentHash, ::writeSnapshot)
        }

        assertThat(republished, equalTo(abiHash))
        assertThat(Files.readAllBytes(snapshotFile).toList(), equalTo(listOf<Byte>(1, 2, 3)))
        assertThat(Files.list(dir).use { it.count() }, equalTo(2L))
    }
}
