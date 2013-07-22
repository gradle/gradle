/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.performance

import org.gradle.performance.fixture.AbstractPerformanceTest
import spock.lang.Unroll

import static org.gradle.performance.measure.Duration.millis

class DependencyReportPerformanceTest extends AbstractPerformanceTest {
    @Unroll("Project '#testProject' dependency report")
    def "dependency report"() {
        given:
        runner.testId = "dependencyReport $testProject"
        runner.testProject = testProject
        runner.tasksToRun = ['dependencyReport']
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject       | maxExecutionTimeRegression
        "lotDependencies" | millis(500)
    }
}