/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.performance.measure.DataAmount

import static org.gradle.performance.measure.Duration.millis

class ManyEmptyProjectsHelpPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def run() {
        given:
        runner.testId = "many empty projects help"
        runner.testProject = "bigEmpty"
        runner.tasksToRun = ['help']
        runner.maxExecutionTimeRegression = millis(2000)
        runner.maxMemoryRegression = DataAmount.mbytes(200)
        runner.targetVersions = ['1.0', '2.0', '2.2.1', '2.4', 'last']

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}