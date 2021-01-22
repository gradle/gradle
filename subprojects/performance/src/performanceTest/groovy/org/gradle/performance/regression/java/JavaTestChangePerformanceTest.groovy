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
import org.gradle.profiler.mutations.ApplyNonAbiChangeToJavaSourceFileMutator

import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject", "mediumJavaMultiProjectWithTestNG", "largeMonolithicJavaProject"])
)
class JavaTestChangePerformanceTest extends AbstractCrossVersionPerformanceTest {
    def "test for non-abi change"() {
        given:
        def testProject = JavaTestProject.projectFor(runner.testProject)
        runner.warmUpRuns = 2
        runner.runs = 6
        runner.tasksToRun = ['test']
        runner.targetVersions = ["7.0-20210122131800+0000"]
        // Pre-4.0 versions run into memory problems with this test
        runner.minimumBaseVersion = "4.0"
        runner.addBuildMutator { new ApplyNonAbiChangeToJavaSourceFileMutator(new File(it.projectDir, testProject.config.fileToChangeByScenario['test'])) }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
