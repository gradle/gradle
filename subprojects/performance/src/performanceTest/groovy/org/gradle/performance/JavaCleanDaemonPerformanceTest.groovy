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

import org.gradle.performance.categories.Experiment
import org.gradle.performance.categories.JavaPerformanceTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category([JavaPerformanceTest])
class JavaCleanDaemonPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Category([Experiment])
    @Unroll("clean Java software model build - #testProject")
    def "clean Java software model build"() {
        given:
        runner.testId = "clean Java build $testProject (daemon)"
        runner.testProject = testProject
        runner.tasksToRun = ['clean']
        runner.targetVersions = targetVersions
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms1g", "-Xmx1g"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject               | targetVersions
        "largeJavaSwModelProject" | ['2.11', 'last']
        "bigNewJava"              | ['2.11', 'last']
        "mediumNewJava"           | ['2.11', 'last']
        "smallNewJava"            | ['2.9', '2.11', 'last']
    }

    @Unroll("clean Java build - #testProject")
    def "clean Java build"() {
        given:
        runner.testId = "clean Java build $testProject (daemon)"
        runner.testProject = testProject
        runner.useDaemon = true
        runner.tasksToRun = ["clean"]
        runner.targetVersions = targetVersions
        runner.gradleOpts = ["-Xms1g", "-Xmx1g"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject            | targetVersions
        "bigOldJava"           | ['2.8', 'last']
        // TODO(pepper): Revert this to 'last' when 3.2 is released
        // The regression was determined acceptable in this discussion:
        // https://issues.gradle.org/browse/GRADLE-1346
        "mediumOldJava"        | ['3.2-20160915000027+0000']
        "smallOldJava"         | ['3.2-20160915000027+0000']
    }
}
