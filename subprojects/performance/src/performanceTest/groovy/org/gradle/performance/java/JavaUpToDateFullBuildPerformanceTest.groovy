/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import spock.lang.Unroll

class JavaUpToDateFullBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll("Up-to-date build - #testProject")
    def "up-to-date build Java project"() {
        given:
        runner.testId = "up-to-date build Java project $testProject"
        runner.previousTestIds = ["up-to-date full build Java build $testProject (daemon)"]
        runner.testProject = testProject
        runner.tasksToRun = ['build']
        runner.gradleOpts = ["-Xms${maxMemory}", "-Xmx${maxMemory}"]
        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
        where:
        testProject       |  maxMemory
        "bigOldJava"      | '576m'
        "lotDependencies" | '256m'
    }
}
