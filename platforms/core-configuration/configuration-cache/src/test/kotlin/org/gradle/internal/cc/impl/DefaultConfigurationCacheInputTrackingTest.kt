/*
 * Copyright 2026 Gradle and contributors.
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test


class DefaultConfigurationCacheInputTrackingTest {

    private val inputTrackingState = InputTrackingState()
    private val inputTracking = DefaultConfigurationCacheInputTracking(inputTrackingState)

    @Test
    fun `disables input tracking while action runs and restores it afterward`() {
        val result = inputTracking.withInputTrackingDisabledUnsafe {
            assertFalse(inputTrackingState.isEnabledForCurrentThread())
            "result"
        }

        assertEquals("result", result)
        assertTrue(inputTrackingState.isEnabledForCurrentThread())
    }

    @Test
    fun `restores input tracking after action fails`() {
        val failure = RuntimeException("broken")

        try {
            inputTracking.withInputTrackingDisabledUnsafe<String> {
                assertFalse(inputTrackingState.isEnabledForCurrentThread())
                throw failure
            }
            fail("Expected the action to fail")
        } catch (ex: RuntimeException) {
            assertSame(failure, ex)
        }

        assertTrue(inputTrackingState.isEnabledForCurrentThread())
    }
}
