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

import org.apache.commons.io.FileUtils
import org.gradle.performance.AbstractCrossVersionGradleInternalPerformanceTest
import org.gradle.performance.categories.SlowPerformanceRegressionTest
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.measure.MeasuredOperation
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL
import static org.gradle.performance.generator.JavaTestProject.LARGE_MONOLITHIC_JAVA_PROJECT

@Category(SlowPerformanceRegressionTest)
class JavaFirstUsePerformanceTest extends AbstractCrossVersionGradleInternalPerformanceTest {

    @Unroll
    def "first use of #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.tasksToRun = ['tasks']
        runner.runs = runs
        runner.useDaemon = false
        runner.targetVersions = ["6.4-20200326063007+0000"]
        runner.addBuildExperimentListener(new BuildExperimentListenerAdapter() {
            @Override
            void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
                runner.workingDir.eachDir {
                    FileUtils.deleteDirectory(new File(it, '.gradle'))
                    FileUtils.deleteDirectory(new File(it, 'buildSrc/.gradle'))
                    FileUtils.deleteDirectory(new File(it, 'gradle-user-home'))
                }
            }
        })

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                         | runs
        LARGE_MONOLITHIC_JAVA_PROJECT       | 10
        LARGE_JAVA_MULTI_PROJECT            | 10
        LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL | 5
    }

    @Unroll
    def "clean checkout of #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.tasksToRun = ['tasks']
        runner.useDaemon = false
        runner.targetVersions = ["6.4-20200326063007+0000"]
        runner.addBuildExperimentListener(new BuildExperimentListenerAdapter() {
            @Override
            void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
                runner.workingDir.eachDir {
                    FileUtils.deleteDirectory(new File(it, '.gradle'))
                    FileUtils.deleteDirectory(new File(it, 'buildSrc/.gradle'))
                }
            }
        })

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                              | _
        LARGE_MONOLITHIC_JAVA_PROJECT            | _
        LARGE_JAVA_MULTI_PROJECT                 | _
        LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL      | _
    }

    @Unroll
    def "cold daemon on #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.tasksToRun = ['tasks']
        runner.useDaemon = false
        runner.targetVersions = ["6.4-20200326063007+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                              | _
        LARGE_MONOLITHIC_JAVA_PROJECT            | _
        LARGE_JAVA_MULTI_PROJECT                 | _
        LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL      | _
    }
}
