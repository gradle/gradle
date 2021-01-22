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
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.JavaTestProject
import org.gradle.performance.mutator.ApplyNonAbiChangeToGroovySourceFileMutator
import org.gradle.profiler.mutations.ApplyNonAbiChangeToJavaSourceFileMutator
import spock.lang.Unroll

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor([
    @Scenario(type = PER_COMMIT, operatingSystems = [LINUX],
        testProjects = ["largeJavaMultiProject", "largeGroovyMultiProject", "largeMonolithicJavaProject", "largeMonolithicGroovyProject"])
])
class JavaNonABIChangePerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll
    def "assemble for non-abi change"() {
        given:
        def testProject = JavaTestProject.projectFor(runner.testProject)
        runner.tasksToRun = ['assemble']
        runner.targetVersions = ["7.0-20210122131800+0000"]
        boolean isGroovyProject = testProject.name().contains("GROOVY")
        runner.addBuildMutator {
            def fileToChange = new File(it.projectDir, testProject.config.fileToChangeByScenario['assemble'])
            return isGroovyProject ? new ApplyNonAbiChangeToGroovySourceFileMutator(fileToChange) : new ApplyNonAbiChangeToJavaSourceFileMutator(fileToChange)
        }
        if (isGroovyProject) {
            runner.minimumBaseVersion = '5.0'
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
