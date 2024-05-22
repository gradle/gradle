/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.configurationcache.problems

import org.gradle.internal.Describables
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class ConfigurationCacheProblemsSummaryTest {

    @Test
    fun `keeps track of unique problems upto maxCollectedProblems`() {
        val subject = ConfigurationCacheProblemsSummary(maxCollectedProblems = 2)
        assertTrue(
            "1st problem",
            subject.onProblem(buildLogicProblem("build.gradle", "failure"), ProblemSeverity.Failure)
        )
        assertTrue(
            "2nd problem (same message as 1st but different location)",
            subject.onProblem(buildLogicProblem("build.gradle.kts", "failure"), ProblemSeverity.Failure)
        )
        assertFalse(
            "overflow",
            subject.onProblem(buildLogicProblem("build.gradle", "another failure"), ProblemSeverity.Failure)
        )
        assertThat(
            subject.get().uniqueProblemCount,
            equalTo(2)
        )
    }

    @Test
    fun `keeps track of total problem count`() {
        val subject = ConfigurationCacheProblemsSummary(maxCollectedProblems = 2)
        assertTrue(
            "1st problem",
            subject.onProblem(buildLogicProblem("build.gradle", "failure"), ProblemSeverity.Failure)
        )
        assertTrue(
            "2nd problem",
            subject.onProblem(buildLogicProblem("build.gradle", "failure"), ProblemSeverity.Failure)
        )
        assertFalse(
            "overflow",
            subject.onProblem(buildLogicProblem("build.gradle", "failure"), ProblemSeverity.Failure)
        )

        val summary = subject.get()
        assertThat(
            "Keeps track of total problem count regardless of maxCollectedProblems",
            summary.problemCount,
            equalTo(3)
        )
    }

    private
    fun buildLogicProblem(location: String, message: String) = PropertyProblem(
        PropertyTrace.BuildLogic(Describables.of(location), 1),
        StructuredMessage.build { text(message) }
    )
}
