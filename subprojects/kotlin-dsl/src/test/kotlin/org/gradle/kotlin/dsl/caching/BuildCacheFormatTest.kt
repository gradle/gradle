/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.caching

import org.gradle.internal.id.UniqueId

import org.gradle.kotlin.dsl.cache.PackMetadata
import org.gradle.kotlin.dsl.cache.pack
import org.gradle.kotlin.dsl.cache.unpack
import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.withFolders
import org.gradle.kotlin.dsl.support.normalisedPathRelativeTo

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File


class BuildCacheFormatTest : TestWithTempFiles() {

    @Test
    fun `can unpack directory tree`() {

        // given:
        root.withFolders {
            "input" {
                "sub1" {
                    withFile("f1.txt", "42")
                }
                "sub2" {
                    "sub3" {
                        withFile("f2.txt", "21")
                    }
                }
            }
        }

        // when:
        val metadata = PackMetadata(UniqueId.generate(), 42L)
        val inputDir = file("input")
        val (packedEntryCount, bytes) = packToByteArray(inputDir, metadata)
        assertThat(
            packedEntryCount,
            equalTo(5L)
        )

        // then:
        val outputDir = file("output").apply { mkdir() }
        val (unpackedMetadata, unpackedEntryCount) =
            unpack(ByteArrayInputStream(bytes), outputDir)

        assertThat(
            unpackedEntryCount,
            equalTo(packedEntryCount)
        )

        assertThat(
            unpackedMetadata,
            equalTo(metadata)
        )

        assertThat(
            filesIn(inputDir),
            equalTo(filesIn(outputDir))
        )
    }

    private
    fun filesIn(inputDir: File) =
        inputDir.walkTopDown().map { descriptorFor(it, inputDir) }.sorted().toList()

    private
    fun descriptorFor(f: File, baseDir: File) =
        f.normalisedPathRelativeTo(baseDir) + (if (f.isFile) ":${f.readText()}" else "/")
}


internal
fun packToByteArray(inputDir: File, metadata: PackMetadata): Pair<Long, ByteArray> =
    ByteArrayOutputStream().use { it ->
        pack(inputDir, metadata, it) to it.toByteArray()
    }
