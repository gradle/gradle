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
import org.gradle.performance.categories.BasicPerformanceTest
import org.junit.experimental.categories.Category

import static org.gradle.performance.measure.DataAmount.mbytes
import static org.gradle.performance.measure.Duration.millis

@Category(BasicPerformanceTest)
class ManyEmptyProjectsHelpPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def run() {
        given:
        runner.testId = "many empty projects help"
        runner.testProject = "bigEmpty"
        runner.tasksToRun = ['help']
        // TODO: Tighten this threshold, once 1.0 is no longer the fastest ever
        runner.maxExecutionTimeRegression = millis(3500)
        // TODO: Tighten this threshold, once we reduce the base memory used per project
        runner.maxMemoryRegression = mbytes(300)
        runner.targetVersions = ['nightly']

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
