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

package org.gradle.performance.experiment.declarativedsl

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.results.OperatingSystem.LINUX
import static org.gradle.performance.results.OperatingSystem.MAC_OS
import static org.gradle.performance.results.OperatingSystem.WINDOWS

@RunFor(
    @Scenario(type = PER_COMMIT, operatingSystems = [LINUX, MAC_OS, WINDOWS], testProjects = ["largeEmptyMultiProjectDeclarativeDsl"])
)
class DeclarativeDslConfigurationPerformanceTest extends AbstractCrossVersionPerformanceTest {
    def "configure"() {
        given:
        runner.tasksToRun = ['help']
        runner.minimumBaseVersion = '9.4'

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
