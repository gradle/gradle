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

package org.gradle.performance.experiment.declarativedsl

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.mutator.RetryingClearGradleUserHomeMutator
import org.gradle.profiler.mutations.AbstractScheduledMutator
import org.gradle.profiler.mutations.ClearProjectCacheMutator

import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX
import static org.gradle.performance.results.OperatingSystem.MAC_OS
import static org.gradle.performance.results.OperatingSystem.WINDOWS

@RunFor(
    @Scenario(type = PER_DAY, operatingSystems = [LINUX, MAC_OS, WINDOWS], testProjects = ["largeEmptyMultiProjectDeclarativeDsl"])
)
class DeclarativeDslFirstUsePerformanceTest extends AbstractCrossVersionPerformanceTest {

    private static final int WARMUP_RUNS = 5

    private static final int MEASUREMENT_RUNS = 10

    private static final String MINIMUM_BASE_VERSION = "8.8" // Declarative DSL not present in earlier versions

    def "first use"() {
        given:
        runner.tasksToRun = ['tasks']
        runner.warmUpRuns = WARMUP_RUNS
        runner.runs = MEASUREMENT_RUNS
        runner.minimumBaseVersion = MINIMUM_BASE_VERSION
        runner.useDaemon = false
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

    def "clean checkout"() {
        given:
        runner.tasksToRun = ['tasks']
        runner.warmUpRuns = WARMUP_RUNS
        runner.runs = MEASUREMENT_RUNS
        runner.minimumBaseVersion = MINIMUM_BASE_VERSION
        runner.useDaemon = false
        runner.addBuildMutator { invocationSettings ->
            new ClearProjectCacheMutator(invocationSettings.projectDir, AbstractScheduledMutator.Schedule.BUILD)
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "cold daemon"() {
        given:
        runner.tasksToRun = ['tasks']
        runner.warmUpRuns = WARMUP_RUNS
        runner.runs = MEASUREMENT_RUNS
        runner.minimumBaseVersion = MINIMUM_BASE_VERSION
        runner.useDaemon = false

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
