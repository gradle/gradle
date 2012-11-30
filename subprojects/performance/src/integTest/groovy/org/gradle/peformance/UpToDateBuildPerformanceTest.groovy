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

package org.gradle.peformance

import org.gradle.peformance.fixture.PerformanceTestRunner
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.peformance.fixture.DataAmount.kbytes
import static org.gradle.peformance.fixture.Duration.millis

/**
 * by Szczepan Faber, created at: 2/9/12
 */
class UpToDateBuildPerformanceTest extends Specification {
   @Unroll("Project '#testProject' up-to-date build")
    def "build"() {
        expect:
        def result = new PerformanceTestRunner(testProject: testProject,
                tasksToRun: ['build'],
                runs: runs,
                warmUpRuns: 1,
                maxExecutionTimeRegression: [maxExecutionTimeRegression],
                maxMemoryRegression: [kbytes(1400)]
        ).run()
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject       | runs | maxExecutionTimeRegression
        "small"           | 5    | millis(500)
        "multi"           | 5    | millis(1000)
        "lotDependencies" | 5    | millis(1000)
    }
}