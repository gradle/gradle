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

package org.gradle.internal.cc.impl

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File


class ExecutionTimeOnlyOptionsManifestServiceTest {

    @JvmField
    @Rule
    val testDirectoryProvider = TestNameTestDirectoryProvider(javaClass)

    private val rootDir: File
        get() = testDirectoryProvider.testDirectory

    private val service: ExecutionTimeOnlyOptionsManifestService by lazy {
        ExecutionTimeOnlyOptionsManifestService(
            mock { on { cacheDir } doReturn this@ExecutionTimeOnlyOptionsManifestServiceTest.rootDir }
        )
    }

    @Test
    fun `read returns empty when manifest file does not exist`() {
        assertThat(service.taskOptionNames(), equalTo(emptySet()))
    }

    @Test
    fun `round-trip preserves names`() {
        val names = setOf("tests", "filter", "rerun")
        service.write(names)
        assertThat(service.taskOptionNames(), equalTo(names))
    }

    @Test
    fun `write empties set produces a header-only file readable as empty set`() {
        service.write(emptySet())
        assertThat(service.taskOptionNames(), equalTo(emptySet()))
    }

    @Test
    fun `write sorts names on disk regardless of input iteration order`() {
        service.write(linkedSetOf("c", "a", "b"))
        val lines = service.manifestFile.readLines()
        // First line is the header, remaining lines are sorted names.
        assertThat(lines.first().startsWith("#"), equalTo(true))
        assertThat(lines.drop(1), equalTo(listOf("a", "b", "c")))
    }

    @Test
    fun `write overwrites existing manifest atomically`() {
        service.write(setOf("old"))
        service.write(setOf("new"))
        assertThat(service.taskOptionNames(), equalTo(setOf("new")))
    }

    @Test
    fun `write leaves no leftover temp file on the happy path`() {
        service.write(setOf("tests"))
        val leftover = rootDir.listFiles { f -> f.name.endsWith(".tmp") }
        assertThat(leftover?.toList(), equalTo(emptyList()))
    }

    @Test
    fun `read of file with no header is treated as corrupt and returns empty`() {
        // A manifest produced by a non-conforming writer (no leading '#' header) must not be
        // mistaken for a v1 manifest — option names would then be silently dropped or, worse,
        // a stray line could be interpreted as an option name and propagated into the CC key.
        service.manifestFile.also {
            it.parentFile.mkdirs()
            it.writeText("tests\nfilter\n")
        }
        assertThat(service.taskOptionNames(), equalTo(emptySet()))
    }

    @Test
    fun `read of unknown header version returns empty for forward compatibility`() {
        // A future writer may bump the header to #v2; an older reader must fail-safe rather than
        // attempt to interpret the body as v1, which could surface as wrong option names in the CC key.
        service.manifestFile.also {
            it.parentFile.mkdirs()
            it.writeText("#v999\ntests\nfilter\n")
        }
        assertThat(service.taskOptionNames(), equalTo(emptySet()))
    }

    @Test
    fun `read tolerates blank lines and surrounding whitespace`() {
        service.manifestFile.also {
            it.parentFile.mkdirs()
            it.writeText("#v1\n\n  tests  \n\nfilter\n")
        }
        assertThat(service.taskOptionNames(), equalTo(setOf("filter", "tests")))
    }

    @Test
    fun `read of empty file returns empty set`() {
        service.manifestFile.also {
            it.parentFile.mkdirs()
            it.writeText("")
        }
        assertThat(service.taskOptionNames(), equalTo(emptySet()))
    }

    @Test
    fun `write creates the configuration cache directory if it does not yet exist`() {
        val nested = File(rootDir, "subdir-that-does-not-exist")
        val nestedService = ExecutionTimeOnlyOptionsManifestService(
            mock { on { cacheDir } doReturn nested }
        )
        nestedService.write(setOf("tests"))
        assertThat(nestedService.taskOptionNames(), equalTo(setOf("tests")))
    }
}
