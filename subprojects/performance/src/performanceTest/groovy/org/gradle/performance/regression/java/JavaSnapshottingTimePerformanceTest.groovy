/*
 * Copyright 2019 the original author or authors.
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


import org.gradle.performance.AbstractCrossVersionGradleProfilerPerformanceTest
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.MEDIUM_JAVA_MULTI_PROJECT

class JavaSnapshottingTimePerformanceTest extends AbstractCrossVersionGradleProfilerPerformanceTest {

    @Unroll
    def "up-to-date assemble on #testProject (parallel #parallel - gradle profiler)"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.tasksToRun = ['assemble']
        runner.targetVersions = ["5.7-20190722220035+0000"]
        runner.args += ["-Dorg.gradle.parallel=$parallel"]
        runner.warmUpRuns = 3
        runner.runs = 5

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject               | parallel
        MEDIUM_JAVA_MULTI_PROJECT | false
    }
}
