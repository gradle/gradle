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

package org.gradle.internal.cc.impl

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.gradle.configurationcache.WorkGraphLoadResult
import org.gradle.configurationcache.WorkGraphStoreResult
import org.gradle.configurationcache.withWorkGraphLoadOperation
import org.gradle.configurationcache.withWorkGraphStoreOperation
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import java.util.UUID


class ConfigurationCacheBuildOperationsTest {
    @JvmField
    @Rule
    val testDirectoryProvider = TestNameTestDirectoryProvider(javaClass)

    @Test
    fun `sets progress display name on store`() {
        // given:
        val buildOperationRunner = mock<BuildOperationRunner> {
            on { call<Unit>(any()) } doReturn Unit
        }
        val stateFile = testDirectoryProvider.file("stateFile")

        // when:
        buildOperationRunner.withWorkGraphStoreOperation("key") {
            WorkGraphStoreResult(listOf(stateFile), null)
        }

        // then:
        val runnableBuildOperation = ArgumentCaptor.forClass(RunnableBuildOperation::class.java)
        verify(buildOperationRunner).run(runnableBuildOperation.capture())

        // and:
        assertThat(
            runnableBuildOperation.value.description().build().progressDisplayName,
            equalTo("Storing configuration cache state")
        )
    }

    @Test
    fun `sets progress display name on load`() {
        // given:
        val workGraph = mock<BuildTreeWorkGraph.FinalizedGraph>()
        val buildOperationRunner = mock<BuildOperationRunner> {
            on { call<Any>(any()) } doReturn workGraph
        }
        val stateFile = testDirectoryProvider.file("stateFile")

        // when:
        buildOperationRunner.withWorkGraphLoadOperation {
            WorkGraphLoadResult(listOf(stateFile), UUID.randomUUID().toString()) to workGraph
        }

        // then:
        val callableBuildOperation = ArgumentCaptor.forClass(CallableBuildOperation::class.java)
        verify(buildOperationRunner).call(callableBuildOperation.capture())

        // and:
        assertThat(
            callableBuildOperation.value.description().build().progressDisplayName,
            equalTo("Loading configuration cache state")
        )
    }
}
