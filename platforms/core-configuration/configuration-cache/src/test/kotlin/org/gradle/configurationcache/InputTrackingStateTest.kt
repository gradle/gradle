/*
 * Copyright 2022 the original author or authors.
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

import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.IllegalStateException
import java.util.concurrent.Executors


class InputTrackingStateTest {

    @Test
    fun `input tracking is enabled by default`() {
        val state = InputTrackingState()

        assertTrue(state.isEnabledForCurrentThread())
    }

    @Test
    fun `input tracking can be disabled`() {
        val state = InputTrackingState()
        state.disableForCurrentThread()

        assertFalse(state.isEnabledForCurrentThread())
    }

    @Test
    fun `input tracking can be re-enabled after disabled`() {
        val state = InputTrackingState()
        state.disableForCurrentThread()
        state.restoreForCurrentThread()

        assertTrue(state.isEnabledForCurrentThread())
    }

    @Test
    fun `input tracking is disabled per-thread`() {
        val state = InputTrackingState()
        state.disableForCurrentThread()

        val otherThread = Executors.newSingleThreadExecutor()
        val enabledOnOtherThread: Boolean
        try {
            enabledOnOtherThread = otherThread.submit(state::isEnabledForCurrentThread).get()
        } finally {
            otherThread.shutdown()
        }

        assertTrue(enabledOnOtherThread)
    }

    @Test
    fun `input tracking is restored to the state when it was disabled`() {
        val state = InputTrackingState()
        state.disableForCurrentThread() // <1>
        state.disableForCurrentThread() // <2>

        state.restoreForCurrentThread() // Revert call at <2>, restore to "disabled" state set by call at <1>
        assertFalse(state.isEnabledForCurrentThread())

        state.restoreForCurrentThread() // Revert call at <1>, restore to "enabled" state
        assertTrue(state.isEnabledForCurrentThread())
    }

    @Test
    fun `disable and restore calls can be mixed non-trivially`() {
        val state = InputTrackingState()
        state.disableForCurrentThread() // <1>
        state.disableForCurrentThread() // <2>
        state.restoreForCurrentThread() // Revert <2>
        state.disableForCurrentThread() // <3>

        assertFalse(state.isEnabledForCurrentThread())
        state.restoreForCurrentThread() // Revert <3>

        assertFalse(state.isEnabledForCurrentThread())
        state.restoreForCurrentThread() // Revert <1>

        assertTrue(state.isEnabledForCurrentThread())
    }

    @Test
    fun `initial restore is an exception`() {
        val state = InputTrackingState()
        try {
            state.restoreForCurrentThread()
            Assert.fail("Exception expected")
        } catch (ignored: IllegalStateException) {
            // Expected exception
        }
    }

    @Test
    fun `unmatched restore is an exception`() {
        val state = InputTrackingState()
        state.disableForCurrentThread()
        state.restoreForCurrentThread()

        try {
            state.restoreForCurrentThread()
            Assert.fail("Exception expected")
        } catch (ignored: IllegalStateException) {
            // Expected exception
        }
    }
}
