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

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.gradle.api.internal.project.DynamicLookupRoutine
import org.gradle.configuration.internal.DynamicCallContextTracker
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.DynamicObject
import org.junit.Test


class TrackingDynamicLookupRoutineTest {
    @Test
    fun `tracks context in all implementations`() {
        val receiver = mock<DynamicObject> {
            on { tryGetProperty(any()) }.thenReturn(DynamicInvokeResult.found())
        }

        fun shouldTrackContext(action: DynamicLookupRoutine.() -> Unit) {
            val tracker = mock<DynamicCallContextTracker>()
            val routine = TrackingDynamicLookupRoutine(tracker)
            action(routine)
            verify(tracker, times(1)).enterDynamicCall(receiver)
            verify(tracker, times(1)).leaveDynamicCall(receiver)
        }

        shouldTrackContext { property(receiver, "test") }
        shouldTrackContext { findProperty(receiver, "test") }
        shouldTrackContext { setProperty(receiver, "test", "test") }
        shouldTrackContext { hasProperty(receiver, "test") }
        shouldTrackContext { getProperties(receiver) }
        shouldTrackContext { invokeMethod(receiver, "test", arrayOf("test")) }
    }
}
