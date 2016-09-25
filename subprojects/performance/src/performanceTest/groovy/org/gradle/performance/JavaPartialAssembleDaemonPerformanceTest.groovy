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

import org.gradle.performance.categories.Experiment
import org.gradle.performance.categories.JavaPerformanceTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category([JavaPerformanceTest])
class JavaPartialAssembleDaemonPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Category([Experiment])
    @Unroll("partial assemble Java software model build - #testProject")
    def "partial assemble Java software model"() {
        given:
        runner.testId = "partial assemble Java build $testProject (daemon)"
        runner.testProject = testProject
        runner.useDaemon = true
        runner.tasksToRun = [":project1:clean", ":project1:assemble"]
        runner.targetVersions = targetVersions
        runner.gradleOpts = ["-Xms1g", "-Xmx1g"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject     | targetVersions
        "bigNewJava"    | ['2.11', 'last']
        "mediumNewJava" | ['2.9', '2.10', 'last']
    }

    @Unroll("partial assemble Java build - #testProject")
    def "partial assemble"() {
        given:
        runner.testId = "partial assemble Java build $testProject (daemon)"
        if (testProject == "bigOldJavaMoreSource") {
            runner.previousTestIds = ["big project old java plugin partial build"]
        }
        runner.testProject = testProject
        runner.useDaemon = true
        runner.tasksToRun = [":project1:clean", ":project1:assemble"]
        // TODO(pepper): Revert this to 'last' when 3.2 is released
        // The regression was determined acceptable in this discussion:
        // https://issues.gradle.org/browse/GRADLE-1346
        runner.targetVersions = ['3.2-20160915000027+0000']
        runner.gradleOpts = ["-Xms1g", "-Xmx1g"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject << ["bigOldJavaMoreSource", "bigOldJava", "mediumOldJava"]
    }
}
