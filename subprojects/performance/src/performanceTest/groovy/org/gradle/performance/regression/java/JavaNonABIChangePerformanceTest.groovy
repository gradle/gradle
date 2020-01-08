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

import static org.gradle.performance.generator.JavaTestProject.LARGE_GROOVY_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_MONOLITHIC_GROOVY_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_MONOLITHIC_JAVA_PROJECT

class JavaNonABIChangePerformanceTest extends AbstractCrossVersionGradleInternalPerformanceTest {

    @Unroll
    def "assemble for non-abi change on #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.tasksToRun = ['assemble']
        runner.addBuildExperimentListener(new ApplyNonAbiChangeToJavaSourceFileMutator(testProject.config.fileToChangeByScenario['assemble']))
        runner.targetVersions = ["6.2-20200108160029+0000"]
        if (testProject.name().contains("GROOVY")) {
            runner.minimumBaseVersion = '5.0'
        }


        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject << [LARGE_MONOLITHIC_JAVA_PROJECT, LARGE_JAVA_MULTI_PROJECT, LARGE_MONOLITHIC_GROOVY_PROJECT, LARGE_GROOVY_MULTI_PROJECT]
    }
}
