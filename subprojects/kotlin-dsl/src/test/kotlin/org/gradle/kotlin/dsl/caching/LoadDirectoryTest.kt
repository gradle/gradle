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

import org.gradle.kotlin.dsl.cache.LoadDirectory
import org.gradle.kotlin.dsl.cache.PackMetadata
import org.gradle.kotlin.dsl.cache.ScriptBuildCacheKey

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import java.io.InputStream


class LoadDirectoryTest : TestWithTempFiles() {

    @Test
    fun `returns correct OriginMetadata based on buildInvocationId`() {

        // given:
        val previousBuildInvocation = UniqueId.generate()
        val currentBuildInvocation = UniqueId.generate()
        val subject = LoadDirectory(
            newFolder(),
            ScriptBuildCacheKey("test-key", "test-key/42"),
            currentBuildInvocation
        )

        // then:
        assertTrue(
            subject.load(packProducedBy(currentBuildInvocation))!!.metadata.isProducedByCurrentBuild
        )

        // and:
        assertFalse(
            subject.load(packProducedBy(previousBuildInvocation))!!.metadata.isProducedByCurrentBuild
        )
    }

    private
    fun packProducedBy(buildInvocationId: UniqueId): InputStream =
        packToByteArray(newFolder(), PackMetadata(buildInvocationId, 1L)).second.inputStream()
}
