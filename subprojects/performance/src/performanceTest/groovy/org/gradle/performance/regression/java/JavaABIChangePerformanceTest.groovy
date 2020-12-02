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
import org.gradle.performance.mutator.ApplyAbiChangeToGroovySourceFileMutator
import org.gradle.profiler.mutations.ApplyAbiChangeToJavaSourceFileMutator

import static org.gradle.performance.annotations.ScenarioType.TEST
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = TEST, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject", "largeMonolithicJavaProject", "largeMonolithicGroovyProject", "largeGroovyMultiProject"])
)
class JavaABIChangePerformanceTest extends AbstractCrossVersionPerformanceTest {

    def "assemble for abi change"() {
        given:
        def testProject = JavaTestProject.projectFor(runner.testProject)
        runner.tasksToRun = ['assemble']
        boolean isGroovyProject = testProject.name().contains("GROOVY")
        runner.addBuildMutator {
            def fileToChange = new File(it.projectDir, testProject.config.fileToChangeByScenario['assemble'])
            return isGroovyProject ? new ApplyAbiChangeToGroovySourceFileMutator(fileToChange) : new ApplyAbiChangeToJavaSourceFileMutator(fileToChange)
        }
        runner.targetVersions = ["6.9-20201201230040+0000"]
        if (isGroovyProject) {
            runner.minimumBaseVersion = '5.0'
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
