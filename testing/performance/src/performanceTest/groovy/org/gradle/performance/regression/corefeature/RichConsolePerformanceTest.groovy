/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.regression.corefeature

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.test.fixtures.file.LeaksFileHandles

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX

class RichConsolePerformanceTest extends AbstractCrossVersionPerformanceTest {

    def setup() {
        runner.args << '--console=rich'
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeMonolithicJavaProject"], iterationMatcher = "^clean assemble.*"),
        @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject", "bigNative"], iterationMatcher = "^clean assemble.*"),
        @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["withVerboseJUnit"], iterationMatcher = "^cleanTest.*")
    ])
    @LeaksFileHandles
    def "#tasks with rich console"() {
        given:
        runner.tasksToRun = tasks.split(' ')
        runner.warmUpRuns = 5
        runner.runs = 8

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        tasks << ['clean assemble', 'cleanTest test']
    }
}
