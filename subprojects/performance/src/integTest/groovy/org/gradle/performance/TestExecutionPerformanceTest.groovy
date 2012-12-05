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
    def "verbose tests with report"() {
        when:
        def result = new PerformanceTestRunner(testProject: testProject,
                tasksToRun: ['cleanTest', 'test'],
                runs: runs,
                args: ['-q'],
                warmUpRuns: 1,
                targetVersions: versions,
                maxExecutionTimeRegression: maxExecutionTimeRegression,
                maxMemoryRegression: maxMemoryRegression
        ).run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject         | runs | versions        | maxExecutionTimeRegression   | maxMemoryRegression
        //needs to be tuned when html report is efficient
        "withTestNG"        | 4    | ['1.2', 'last'] | [millis(1200), millis(200)]  | [kbytes(0), kbytes(0)]
        "withVerboseJUnits" | 4    | ['last']        | [millis(1200)]               | [kbytes(0)]
    }
}