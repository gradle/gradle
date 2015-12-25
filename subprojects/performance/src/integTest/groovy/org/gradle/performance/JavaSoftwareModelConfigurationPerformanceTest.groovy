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
import org.gradle.performance.measure.DataAmount
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.measure.Duration.millis

@Category([JavaPerformanceTest])
class JavaSoftwareModelConfigurationPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll("Project '#testProject' measuring configuration time")
    def "configure java software model project"() {
        given:
        runner.testId = "configure new java project $testProject"
        runner.testProject = testProject
        runner.tasksToRun = ['help']
        runner.targetVersions = ['2.8', 'last']
        runner.useDaemon = true
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression
        runner.maxMemoryRegression = DataAmount.mbytes(150)
        runner.gradleOpts = ["-Xms1g", "-Xmx1g", "-XX:MaxPermSize=256m"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject               | maxExecutionTimeRegression
        "largeJavaSwModelProject" | millis(500)
        "bigNewJava"              | millis(500)
        // TODO: these 2 template projects should be merged
    }
}
