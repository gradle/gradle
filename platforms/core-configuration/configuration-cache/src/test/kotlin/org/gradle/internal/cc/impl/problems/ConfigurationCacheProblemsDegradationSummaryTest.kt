/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl.problems

import org.gradle.internal.cc.impl.problems.ConfigurationCacheProblems.DegradationSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigurationCacheProblemsDegradationSummaryTest {
    @Test
    fun `degradation summary`() {
        assertMessage(" because incompatible task was found.", emptyList(), 1)
        assertMessage(" because incompatible tasks were found.", emptyList(), 3)
        assertMessage(" because incompatible feature usage (feature A) was found.", listOf("feature A"), 0)
        assertMessage(" because incompatible feature usage (feature A, feature B) was found.", listOf("feature A", "feature B"), 0)
        assertMessage(" because incompatible task and feature usage (feature A) were found.", listOf("feature A"), 1)
        assertMessage(" because incompatible tasks and feature usage (feature A) were found.", listOf("feature A"), 3)
        assertMessage(" because incompatible tasks and feature usage (feature A, feature B) were found.", listOf("feature A", "feature B"), 3)
    }

    private fun assertMessage(expected: String, degradingFeatures: List<String>, degradingTaskCount: Int) {
        assertEquals(expected, renderSummary(degradingFeatures, degradingTaskCount))
    }

    private fun renderSummary(degradingFeatures: List<String>, degradingTaskCount: Int) = DegradationSummary(
        degradingFeatures,
        degradingTaskCount
    ).render()
}
