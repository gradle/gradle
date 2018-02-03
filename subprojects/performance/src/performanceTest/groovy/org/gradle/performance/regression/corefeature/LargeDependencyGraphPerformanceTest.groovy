/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.WithExternalRepository
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.GradleInvocationSpec
import org.gradle.performance.measure.MeasuredOperation

class LargeDependencyGraphPerformanceTest extends AbstractCrossVersionPerformanceTest implements WithExternalRepository {

    private final static TEST_PROJECT_NAME = 'excludeRuleMergingBuild'

    def setup() {
        runner.minimumVersion = '4.0'
    }

    def "resolve large dependency graph"() {
        runner.testProject = TEST_PROJECT_NAME
        startServer()

        given:
        def baseline = "4.6-20180125002142+0000"
        runner.tasksToRun = ['resolveDependencies']
        runner.gradleOpts = ["-Xms256m", "-Xmx256m"]
        runner.targetVersions = [baseline]
        runner.args = ['-PuseHttp', "-PhttpPort=${serverPort}", "-PnoExcludes"]
        //runner.addBuildExperimentListener(createArgsTweaker(baseline))

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        cleanup:
        stopServer()
    }

    def "resolve large dependency graph (parallel)"() {
        runner.testProject = TEST_PROJECT_NAME
        startServer()

        given:
        def baseline = "4.6-20180125002142+0000"
        runner.tasksToRun = ['resolveDependencies']
        runner.gradleOpts = ["-Xms256m", "-Xmx256m"]
        runner.targetVersions = [baseline]
        runner.args = ['-PuseHttp', "-PhttpPort=${serverPort}", "-PnoExcludes", "--parallel"]
        //runner.addBuildExperimentListener(createArgsTweaker(baseline))

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        cleanup:
        stopServer()
    }

    private static BuildExperimentListener createArgsTweaker(String baseline) {
        new BuildExperimentListener() {
            @Override
            void beforeExperiment(BuildExperimentSpec experimentSpec, File projectDir) {
                GradleInvocationSpec invocation = experimentSpec.invocation as GradleInvocationSpec
                if (invocation.gradleDistribution.version.version != baseline) {
                    invocation.args << '-Dorg.gradle.advancedpomsupport=true'
                }
            }

            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {

            }

            @Override
            void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {

            }
        }
    }

}
