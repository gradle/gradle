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

package org.gradle.performance.regression.kotlindsl

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.mutator.RetryingClearGradleUserHomeMutator
import org.gradle.profiler.mutations.AbstractScheduledMutator
import org.gradle.profiler.mutations.ClearProjectCacheMutator
import spock.lang.Issue

import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX

@Issue("https://github.com/gradle/gradle/issues/37279")
@RunFor(
    @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["kotlinDslManyAccessors"])
)
class KotlinDslAccessorsPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def "generate accessors"() {
        given:
        runner.tasksToRun = ['help']
        runner.runs = 6
        runner.useDaemon = false
        // Clear the caches before each build so the Kotlin DSL type-safe accessors are regenerated
        // and re-snapshotted every measured build, exercising the accessor generation output path.
        runner.addBuildMutator { invocationSettings ->
            new RetryingClearGradleUserHomeMutator(invocationSettings.gradleUserHome, AbstractScheduledMutator.Schedule.BUILD)
        }
        runner.addBuildMutator { invocationSettings ->
            new ClearProjectCacheMutator(invocationSettings.projectDir, AbstractScheduledMutator.Schedule.BUILD)
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
