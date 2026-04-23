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

package org.gradle.performance.regression.ip

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.AndroidSyncPerformanceTestFixture
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.profiler.mutations.ApplyAbiChangeToKotlinSourceFileMutator

import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX

class IsolatedProjectsAndroidSyncPerformanceRegressionTest extends AbstractCrossVersionPerformanceTest {

    private static String warm = "warm"
    private static String cold = "cold"

    private static int maxWorkers = 8

    def setup() {
        // NOTE: see the javadoc for required environment and possible configuration
        AndroidSyncPerformanceTestFixture.configureStudio(runner)
    }

    @RunFor([
        @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["android500Kts"])
    ])
    def "sync Studio after included build logic refactoring with #daemon daemon"() {
        def runner = getRunner() // otherwise, IDEA thinks it's PerformanceTestRunner despite the override
        runner.useDaemon = daemon == warm
        // Use multiple warm-ups for cold scenario to warm-up Android Studio itself
        runner.warmUpRuns = 5
        runner.runs = 20

        runner.args.addAll([
            // realistic defaults
            "-Dorg.gradle.caching=true",
            // scenario
            "-Dorg.gradle.unsafe.isolated-projects=true",
            "-Dorg.gradle.workers.max=$maxWorkers",
            "--no-scan", // TODO:isolated benchmark with Develocity plugin as well
        ])

        runner.addBuildMutator { settings ->
            new ApplyAbiChangeToKotlinSourceFileMutator(new File(settings.projectDir, "build-logic/convention/src/main/kotlin/org/example/awesome/utils.kt"))
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        daemon | _
        warm   | _
        cold   | _
    }

}
