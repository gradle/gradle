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
import spock.lang.Unroll

import static org.gradle.performance.measure.Duration.millis

class CachingClassLoadersPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll("Project '#testProject' build")
    def "build"() {
        given:
        runner.runs = 2
        runner.subRuns = 5
        runner.warmUpRuns = 1
        runner.gradleOpts = ["-Dorg.gradle.caching.classloaders=true"]
        runner.testId = "caching classloaders build $testProject"
        runner.testProject = testProject
        runner.useDaemon = true
        runner.tasksToRun = ['clean', 'build']
        runner.maxExecutionTimeRegression = maxTimeReg
        runner.maxMemoryRegression = maxMemReg
        runner.targetVersions = ['last']

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject       | maxTimeReg    | maxMemReg
        "small"           | millis(2000)  | DataAmount.kbytes(-150)
        "multi"           | millis(5000)  | DataAmount.mbytes(0)
    }
}
