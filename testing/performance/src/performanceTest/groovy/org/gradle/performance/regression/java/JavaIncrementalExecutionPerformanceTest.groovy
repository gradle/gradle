/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.JavaTestProject
import org.gradle.performance.mutator.ApplyAbiChangeToGroovySourceFileMutator
import org.gradle.performance.mutator.ApplyNonAbiChangeToGroovySourceFileMutator
import org.gradle.performance.regression.corefeature.AbstractIncrementalExecutionPerformanceTest
import org.gradle.profiler.mutations.AbstractScheduledMutator
import org.gradle.profiler.mutations.ApplyAbiChangeToJavaSourceFileMutator
import org.gradle.profiler.mutations.ApplyNonAbiChangeToJavaSourceFileMutator
import org.gradle.profiler.mutations.ClearBuildCacheMutator
import org.gradle.test.fixtures.file.LeaksFileHandles

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX
import static org.gradle.performance.results.OperatingSystem.MAC_OS
import static org.gradle.performance.results.OperatingSystem.WINDOWS

@LeaksFileHandles("The TAPI keeps handles to the distribution it starts open in the test JVM")
class JavaIncrementalExecutionPerformanceTest extends AbstractIncrementalExecutionPerformanceTest {
    JavaTestProject testProject
    boolean isGroovyProject

    def setup() {
        testProject = JavaTestProject.findProjectFor(runner.testProject)
        isGroovyProject = testProject?.name()?.contains("GROOVY")
        if (isGroovyProject) {
            runner.minimumBaseVersion = '5.0'
        }
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = ["largeGroovyMultiProject", "largeMonolithicJavaProject", "largeMonolithicGroovyProject"], iterationMatcher = "assemble for non-abi change"),
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX, WINDOWS, MAC_OS], testProjects = "largeJavaMultiProject")
    ])
    def "assemble for non-abi change#configurationCaching"() {
        given:
        runner.tasksToRun = ['assemble']
        runner.addBuildMutator {
            def fileToChange = new File(it.projectDir, testProject.config.fileToChangeByScenario['assemble'])
            return isGroovyProject ? new ApplyNonAbiChangeToGroovySourceFileMutator(fileToChange) : new ApplyNonAbiChangeToJavaSourceFileMutator(fileToChange)
        }
        enableConfigurationCaching(configurationCachingEnabled)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        configurationCachingEnabled << [true, false]
        configurationCaching = configurationCachingMessage(configurationCachingEnabled)
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = ["largeGroovyMultiProject"], iterationMatcher = "assemble for abi change"),
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX, WINDOWS, MAC_OS], testProjects = "largeJavaMultiProject"),
        @Scenario(type = PER_DAY, operatingSystems = LINUX, testProjects = ["largeMonolithicGroovyProject", "largeMonolithicJavaProject"], iterationMatcher = "assemble for abi change")
    ])
    def "assemble for abi change#configurationCaching"() {
        given:
        def testProject = JavaTestProject.projectFor(runner.testProject)
        runner.tasksToRun = ['assemble']
        runner.addBuildMutator {
            def fileToChange = new File(it.projectDir, testProject.config.fileToChangeByScenario['assemble'])
            return isGroovyProject ? new ApplyAbiChangeToGroovySourceFileMutator(fileToChange) : new ApplyAbiChangeToJavaSourceFileMutator(fileToChange)
        }
        enableConfigurationCaching(configurationCachingEnabled)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        configurationCachingEnabled << [true, false]
        configurationCaching = configurationCachingMessage(configurationCachingEnabled)
    }

    @RunFor(
        @Scenario(type = PER_DAY, operatingSystems = LINUX, testProjects = ["largeJavaMultiProject", "mediumJavaMultiProjectWithTestNG", "largeMonolithicJavaProject"])
    )
    def "test for non-abi change"() {
        given:
        def testProject = JavaTestProject.projectFor(runner.testProject)
        runner.warmUpRuns = 2
        runner.runs = 6
        runner.tasksToRun = ['test']
        // Pre-4.0 versions run into memory problems with this test
        runner.minimumBaseVersion = "4.0"
        runner.addBuildMutator { new ApplyNonAbiChangeToJavaSourceFileMutator(new File(it.projectDir, testProject.config.fileToChangeByScenario['test'])) }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX, WINDOWS], testProjects = "largeJavaMultiProject", iterationMatcher = '.*parallel true.*'),
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = ["largeJavaMultiProject", "largeMonolithicJavaProject"], iterationMatcher = '.*parallel false.*'),
    ])
    def "up-to-date assemble (parallel #parallel)"() {
        given:
        runner.tasksToRun = ['assemble']
        runner.args += ["-Dorg.gradle.parallel=$parallel"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        parallel << [true, false]
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = "largeJavaMultiProject", iterationMatcher = '.*parallel true.*'),
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = ["largeJavaMultiProject", "largeMonolithicJavaProject"], iterationMatcher = '.*parallel false.*'),
    ])
    def "up-to-date assemble with local build cache enabled (parallel #parallel)"() {
        given:
        runner.tasksToRun = ['assemble']
        runner.minimumBaseVersion = "3.5"
        runner.args += ["-Dorg.gradle.parallel=$parallel", "-D${StartParameterBuildOptions.BuildCacheOption.GRADLE_PROPERTY}=true"]
        runner.addBuildMutator { invocationSettings ->
            new ClearBuildCacheMutator(invocationSettings.getGradleUserHome(), AbstractScheduledMutator.Schedule.SCENARIO)
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        parallel << [true, false]
    }
}
