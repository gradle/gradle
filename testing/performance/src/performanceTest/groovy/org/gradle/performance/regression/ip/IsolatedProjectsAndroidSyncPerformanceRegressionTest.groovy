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
import org.gradle.performance.IsolatedProjectsSyncPerformanceTestFixture
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.results.CrossVersionPerformanceResults
import org.gradle.profiler.mutations.ApplyAbiChangeToKotlinSourceFileMutator

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.results.OperatingSystem.LINUX

/**
 * Verifies android studio sync performance with IP enabled does not regress between versions.
 */
@RunFor(
    @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["android500Kts"])
)
class IsolatedProjectsAndroidSyncPerformanceRegressionTest extends AbstractCrossVersionPerformanceTest {

    def "sync with build logic abi change"() {
        given:
        runner.warmUpRuns = 2
        runner.runs = 5
        runner.args << "-Dorg.gradle.unsafe.isolated-projects=true"

        runner.measuredBuildOperations << "org.gradle.initialization.ConfigureBuildBuildOperationType"
        runner.addBuildMutator { settings ->
            new ApplyAbiChangeToKotlinSourceFileMutator(new File(settings.projectDir, "build-logic/convention/src/main/kotlin/org/example/awesome/utils.kt"))
        }

        AndroidSyncPerformanceTestFixture.configureStudio(runner)
        IsolatedProjectsSyncPerformanceTestFixture.configureStudioForIp(runner)

        when:
        CrossVersionPerformanceResults result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

}
