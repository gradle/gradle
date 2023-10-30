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

package org.gradle.configurationcache

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.CallableBuildOperation
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any


class ConfigurationCacheBuildOperationsTest {
    @Test
    fun `sets progress display name on store`() {
        // given:
        val buildOperationExecutor = mock<BuildOperationExecutor> {
            on { call<Unit>(any()) } doReturn Unit
        }

        // when:
        buildOperationExecutor.withStoreOperation("key") {
        }

        // then:
        val callableBuildOperation = ArgumentCaptor.forClass(CallableBuildOperation::class.java)
        verify(buildOperationExecutor).call(callableBuildOperation.capture())

        // and:
        assertThat(
            callableBuildOperation.value.description().build().progressDisplayName,
            equalTo("Storing configuration cache state")
        )
    }

    @Test
    fun `sets progress display name on load`() {
        // given:
        val buildOperationExecutor = mock<BuildOperationExecutor> {
            on { call<Unit>(any()) } doReturn Unit
        }

        // when:
        buildOperationExecutor.withLoadOperation() {
        }

        // then:
        val callableBuildOperation = ArgumentCaptor.forClass(CallableBuildOperation::class.java)
        verify(buildOperationExecutor).call(callableBuildOperation.capture())

        // and:
        assertThat(
            callableBuildOperation.value.description().build().progressDisplayName,
            equalTo("Loading configuration cache state")
        )
    }
}
