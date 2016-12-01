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

import spock.lang.Unroll

class JavaTestExecutionPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("#description build for #template")
    def "cleanTest test performance non regression test"() {
        given:
        runner.testId = "$size $description with old Java plugin"
        runner.testProject = template
        runner.tasksToRun = gradleTasks
        runner.useDaemon = true
        runner.gradleOpts = ['-Xms256m', '-Xmx256m']

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        template          | size     | description                 | gradleTasks
        'mediumWithJUnit' | 'medium' | 'runs tests only'           | ['cleanTest', 'test']
        'mediumWithJUnit' | 'medium' | 'clean build and run tests' | ['clean', 'test']
    }

    @Unroll("#description build for #template")
    def "incremental test build performance non regression test"() {
        given:
        runner.testId = "$size $description with old Java plugin"
        runner.testProject = template
        runner.tasksToRun = gradleTasks
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms${maxMemory}", "-Xmx${maxMemory}"]
        runner.buildExperimentListener = new JavaOldModelSourceFileUpdater(10)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        template          | size     | description              | gradleTasks   | maxMemory
        'mediumWithJUnit' | 'medium' | 'incremental test build' | ['test']      | '256m'
        'largeWithJUnit'  | 'large'  | 'incremental test build' | ['test']      | '512m'
    }
}
