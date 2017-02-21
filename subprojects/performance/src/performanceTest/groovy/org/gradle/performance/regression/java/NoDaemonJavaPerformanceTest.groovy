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

package org.gradle.performance.regression.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import spock.lang.Unroll

class NoDaemonJavaPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("configure Java build - #testProject")
    def "configure Java build"() {
        given:
        runner.testId = "configure $testProject project (no-daemon)"
        runner.previousTestIds = ["configure Java build $testProject"]
        runner.testProject = testProject
        runner.tasksToRun = ['help']
        runner.gradleOpts = ["-Xms${maxMemory}", "-Xmx${maxMemory}"]
        runner.useDaemon = false
        runner.targetVersions = ["3.5-20170221000043+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject       | maxMemory
        "bigOldJava"      | '704m'
    }


    @Unroll("Up-to-date full build - #testProject")
    def "up-to-date full build Java build"() {
        given:
        runner.testId = "up-to-date build $testProject project (no-daemon)"
        runner.previousTestIds = ["up-to-date full build Java build $testProject"]
        runner.testProject = testProject
        runner.tasksToRun = ['build']
        runner.gradleOpts = ["-Xms${maxMemory}", "-Xmx${maxMemory}"]
        runner.useDaemon = false
        runner.targetVersions = ["3.5-20170221000043+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject       | maxMemory
        "bigOldJava"      | '704m'
    }

    @Unroll("full build Java build - #testProject")
    def "full build Java build"() {
        given:
        runner.testId = "clean build $testProject Java project (no-daemon)"
        runner.testProject = testProject
        runner.tasksToRun = ['clean', 'build']
        runner.gradleOpts = ["-Xms${maxMemory}", "-Xmx${maxMemory}"]
        runner.useDaemon = false
        runner.targetVersions = ["3.5-20170221000043+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject | maxMemory
        "small"     | '128m'
    }
}
