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
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import java.io.File


class ThrottlingFileAccessTrackerTest {

    private val marks = mutableListOf<File>()
    private var now = 0L
    private val tracker = ThrottlingFileAccessTracker(
        FileAccessTracker { marks.add(it) },
        intervalMillis = 100,
        clock = { now }
    )

    @Test
    fun `marks each file once within the interval`() {
        tracker.markAccessed(File("a"))
        tracker.markAccessed(File("a"))
        tracker.markAccessed(File("b"))
        now = 99
        tracker.markAccessed(File("a"))

        assertThat(marks, equalTo(listOf(File("a"), File("b"))))
    }

    @Test
    fun `marks again once the interval has elapsed`() {
        tracker.markAccessed(File("a"))
        now = 100
        tracker.markAccessed(File("a"))
        now = 150
        tracker.markAccessed(File("a"))

        assertThat(marks, equalTo(listOf(File("a"), File("a"))))
    }
}
