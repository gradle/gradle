/*
 * Copyright 2016 the original author or authors.
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
class JavaCleanDaemonPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("clean Java software model build - #testProject")
    def "clean Java software model build"() {
        given:
        runner.testId = "clean Java build $testProject (daemon)"
        runner.testProject = testProject
        runner.tasksToRun = ['clean']
        runner.maxExecutionTimeRegression = maxTimeRegression
        runner.maxMemoryRegression = maxMemoryRegression
        runner.targetVersions = ['2.9', '2.11', 'last']
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms1g", "-Xmx1g", "-XX:MaxPermSize=256m"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject               | maxTimeRegression | maxMemoryRegression
        "largeJavaSwModelProject" | millis(1000)      | mbytes(50)
        "bigNewJava"              | millis(1000)      | mbytes(50)
        "mediumNewJava"           | millis(500)       | mbytes(50)
        "smallNewJava"            | millis(500)       | mbytes(5)
    }

    @Unroll("clean Java build - #testProject")
    def "clean Java build"() {
        given:
        runner.testId = "clean Java build $testProject (daemon)"
        runner.testProject = testProject
        runner.useDaemon = true
        runner.tasksToRun = ["clean"]
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
        "bigOldJava"           | millis(1000)
        "mediumOldJava"        | millis(1000)
        "smallOldJava"         | millis(1000)
    }
}
