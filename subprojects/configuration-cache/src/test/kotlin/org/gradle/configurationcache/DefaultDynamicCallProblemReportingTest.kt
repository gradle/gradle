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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread


class DefaultDynamicCallProblemReportingTest {
    @Test
    fun `tracks unreported problems in nested dynamic calls`() {
        val reporting = DefaultDynamicCallProblemReporting()
        val entryPoint1 = Any()
        val entryPoint2 = Any()
        val key1 = Any()
        val key2 = Any()

        reporting.enterDynamicCall(entryPoint1)
        assertTrue(reporting.unreportedProblemInCurrentCall(key1))
        assertFalse(reporting.unreportedProblemInCurrentCall(key1))
        assertTrue(reporting.unreportedProblemInCurrentCall(key2))
        assertFalse(reporting.unreportedProblemInCurrentCall(key2))

        reporting.enterDynamicCall(entryPoint2)
        assertTrue(reporting.unreportedProblemInCurrentCall(key1))
        assertTrue(reporting.unreportedProblemInCurrentCall(key2))
        assertFalse(reporting.unreportedProblemInCurrentCall(key1))
        assertFalse(reporting.unreportedProblemInCurrentCall(key2))

        reporting.leaveDynamicCall(entryPoint2)
        assertFalse(reporting.unreportedProblemInCurrentCall(key1))
        assertFalse(reporting.unreportedProblemInCurrentCall(key2))
    }

    @Test
    fun `throws exception on mismatched leave calls`() {
        val reporting = DefaultDynamicCallProblemReporting()
        reporting.enterDynamicCall(Any())
        assertThrows(IllegalStateException::class.java) {
            reporting.leaveDynamicCall(Any())
        }
    }

    @Test
    fun `keeps state per thread`() {
        val reporting = DefaultDynamicCallProblemReporting()

        val latch = CountDownLatch(3)
        val problem = Any()
        val results = Collections.synchronizedList(mutableListOf<Boolean>())

        fun doTest() {
            reporting.enterDynamicCall(this)
            latch.countDown()
            latch.await()
            results += reporting.unreportedProblemInCurrentCall(problem)
        }

        (1..3).map { thread { doTest() } }.forEach(Thread::join)
        assertEquals(listOf(true, true, true), results)
    }
}
