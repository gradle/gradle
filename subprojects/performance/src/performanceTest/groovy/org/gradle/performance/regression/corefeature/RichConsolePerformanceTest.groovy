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

package org.gradle.performance.regression.corefeature

import org.gradle.performance.AbstractCrossVersionGradleProfilerPerformanceTest
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_MONOLITHIC_JAVA_PROJECT

class RichConsolePerformanceTest extends AbstractCrossVersionGradleProfilerPerformanceTest {

    private static final String CLEAN_ASSEMBLE_TASKS = 'clean assemble'

    def setup() {
        runner.args << '--console=rich'
    }

    @Unroll
    def "#tasks on #testProject with rich console"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = tasks.split(' ')
        runner.gradleOpts = ["-Xms${daemonMemory}", "-Xmx${daemonMemory}"]
        runner.warmUpRuns = 5
        runner.runs = 8
        runner.targetVersions = ["6.2-20200108160029+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                   | tasks                 | daemonMemory
        LARGE_JAVA_MULTI_PROJECT      | CLEAN_ASSEMBLE_TASKS  | LARGE_JAVA_MULTI_PROJECT.daemonMemory
        LARGE_MONOLITHIC_JAVA_PROJECT | CLEAN_ASSEMBLE_TASKS  | LARGE_MONOLITHIC_JAVA_PROJECT.daemonMemory
        'bigNative'                   | CLEAN_ASSEMBLE_TASKS  | '1g'
        'withVerboseJUnit'            | 'cleanTest test'      | '256m'
    }
}
