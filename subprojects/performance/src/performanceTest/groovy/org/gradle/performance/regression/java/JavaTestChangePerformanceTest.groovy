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

import org.gradle.performance.AbstractCrossVersionGradleInternalPerformanceTest
import org.gradle.performance.mutator.ApplyNonAbiChangeToJavaSourceFileMutator
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_MONOLITHIC_JAVA_PROJECT
import static org.gradle.performance.generator.JavaTestProject.MEDIUM_JAVA_MULTI_PROJECT_WITH_TEST_NG

class JavaTestChangePerformanceTest extends AbstractCrossVersionGradleInternalPerformanceTest {

    @Unroll
    def "test for non-abi change on #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        runner.tasksToRun = ['test']
        runner.addBuildExperimentListener(new ApplyNonAbiChangeToJavaSourceFileMutator(testProject.config.fileToChangeByScenario['test']))
        runner.targetVersions = ["6.2-20200108160029+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                            | warmUpRuns | runs
        LARGE_JAVA_MULTI_PROJECT               | 2          | 6
        MEDIUM_JAVA_MULTI_PROJECT_WITH_TEST_NG | 2          | 6
        LARGE_MONOLITHIC_JAVA_PROJECT          | 2          | 6

        //monolithicJavaTestNGProject" - testNG requires more test workers, which take too long to start up
    }
}
