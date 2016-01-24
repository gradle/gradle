/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.performance.categories.JavaPerformanceTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.measure.DataAmount.mbytes
import static org.gradle.performance.measure.Duration.millis

@Category([JavaPerformanceTest])
class JavaPartialAssembleDaemonPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll("partial assemble Java build - #testProject")
    def "partial assemble"() {
        given:
        runner.testId = "partial assemble Java build $testProject (daemon)"
        runner.previousTestIds = ["big project old java plugin partial build"]
        runner.testProject = testProject
        runner.useDaemon = true
        runner.tasksToRun = [":project1:clean", ":project1:assemble"]
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression
        runner.maxMemoryRegression = mbytes(50)
        runner.targetVersions = ['2.0', '2.8', '2.11', 'last']
        runner.gradleOpts = ["-Xms1g", "-Xmx1g", "-XX:MaxPermSize=256m"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject            | maxExecutionTimeRegression
        "bigOldJavaMoreSource" | millis(1000)
    }
}
