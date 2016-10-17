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

import org.gradle.performance.categories.Experiment
import org.gradle.performance.categories.JavaPerformanceTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category([JavaPerformanceTest])
class JavaFullAssembleDaemonPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Category([Experiment])
    @Unroll("full assemble Java software model build - #testProject")
    def "full assemble Java software model build"() {
        given:
        runner.testId = "full assemble Java build $testProject (daemon)"
        runner.previousTestIds = ["clean build java project $testProject which doesn't declare any API"]
        runner.testProject = testProject
        runner.tasksToRun = ['clean', 'assemble']
        runner.targetVersions = targetVersions
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms2g", "-Xmx2g"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                                  | targetVersions
        "smallJavaSwModelCompileAvoidanceWithoutApi" | ['2.11', '3.2-20161012120730+0000']
        "largeJavaSwModelCompileAvoidanceWithoutApi" | ['2.11', '3.2-20161012120730+0000']
        "smallJavaSwModelProject"                    | ['2.9', '3.2-20161012120730+0000']
        "largeJavaSwModelProject"                    | ['2.10', '3.2-20161012120730+0000']
        "bigNewJava"                                 | ['2.11', '3.2-20161012120730+0000']
        "mediumNewJava"                              | ['2.11', '3.2-20161012120730+0000']
        "smallNewJava"                               | ['2.9', '2.10', '3.2-20161012120730+0000']
    }

    @Unroll("full assemble Java build - #testProject")
    def "full assemble Java build"() {
        given:
        runner.testId = "full assemble Java build $testProject (daemon)"
        if (testProject == "bigOldJavaMoreSource") {
            runner.previousTestIds = ["big project old java plugin full build"]
        }
        runner.testProject = testProject
        runner.useDaemon = true
        runner.tasksToRun = ["clean", "assemble"]
        runner.targetVersions = targetVersions
        runner.gradleOpts = ["-Xms2g", "-Xmx2g"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject            | targetVersions
        // TODO(pepper): Revert this to 'last' when 3.2 is released
        // The regression was determined acceptable in this discussion:
        // https://issues.gradle.org/browse/GRADLE-1346
        "bigOldJavaMoreSource" | ['3.2-20160915000027+0000']
        "bigOldJava"           | ['3.2-20160915000027+0000']
        "mediumOldJava"        | ['3.2-20160915000027+0000']
        "smallOldJava"         | ['3.2-20160915000027+0000']
    }
}
