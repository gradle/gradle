/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.performance.fixture.PerformanceTestRunner
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.performance.fixture.DataAmount.kbytes
import static org.gradle.performance.fixture.Duration.millis

/**
 * by Szczepan Faber, created at: 2/9/12
 */
class TestExecutionPerformanceTest extends Specification {
    @Unroll("Project '#testProject'")
    def "test execution"() {
        when:
        def result = new PerformanceTestRunner(testProject: testProject,
                tasksToRun: ['cleanTest', 'test'],
                runs: 4,
                args: ['-q'],
                warmUpRuns: 1,
                targetVersions: ['1.0', '1.2', 'last'],
                maxExecutionTimeRegression: [maxExecutionTimeRegression, maxExecutionTimeRegression, maxExecutionTimeRegression],
                maxMemoryRegression: [kbytes(500), kbytes(3000), kbytes(3000)]
        ).run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject         | maxExecutionTimeRegression
        "withTestNG"        | millis(500)
        "withJUnit"         | millis(500)
        "withVerboseTestNG" | millis(500)
        "withVerboseJUnit"  | millis(500)
    }
}