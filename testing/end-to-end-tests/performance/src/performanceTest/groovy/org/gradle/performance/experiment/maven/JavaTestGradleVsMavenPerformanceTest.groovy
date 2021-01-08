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

package org.gradle.performance.experiment.maven

import org.gradle.performance.AbstractGradleVsMavenPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.JavaTestProject
import org.gradle.profiler.mutations.ApplyNonAbiChangeToJavaSourceFileMutator
import spock.lang.Unroll

import static org.gradle.performance.annotations.ScenarioType.PER_WEEK
import static org.gradle.performance.results.OperatingSystem.LINUX

/**
 * Performance tests aimed at comparing the performance of Gradle for compiling and executing test suites, making
 * sure we are always faster than Maven.
 */
@RunFor(
    @Scenario(type = PER_WEEK, operatingSystems = [LINUX], testProjects =  ["mediumMonolithicJavaProject", "mediumJavaMultiProject"])
)
class JavaTestGradleVsMavenPerformanceTest extends AbstractGradleVsMavenPerformanceTest {

    @Unroll
    def "clean #gradleTask (Gradle vs Maven)"() {
        given:
        runner.testGroup = "Gradle vs Maven test build using Java plugin"
        configureMavenOptions(JavaTestProject.projectFor(runner.testProject))
        runner.gradleTasks = ["clean", gradleTask]
        runner.equivalentMavenTasks = ["clean", mavenTask]
        if (mavenTask == "package") {
            runner.mvnArgs << "-Dmaven.test.skip=true"
        }
        runner.warmUpRuns = 2
        runner.runs = 5

        when:
        def results = runner.run()

        then:
        results.assertFasterThanMaven()

        where:
        gradleTask | mavenTask
        'assemble' | 'package'
        'test'     | 'test'
    }

    @Unroll
    def "#gradleTask for non-abi change (Gradle vs Maven)"() {
        given:
        runner.testGroup = "Gradle vs Maven test build using Java plugin"
        def testProject = JavaTestProject.projectFor(runner.testProject)
        def fileToChange = testProject.config.fileToChangeByScenario["assemble"]
        configureMavenOptions(testProject)
        runner.gradleTasks = gradleTask.split(' ')
        runner.equivalentMavenTasks = mavenTask.split(' ')
        if (runner.equivalentMavenTasks.contains('package')) {
            runner.mvnArgs << "-Dmaven.test.skip=true"
        }
        runner.addBuildMutator { new ApplyNonAbiChangeToJavaSourceFileMutator(new File(it.projectDir, fileToChange)) }
        runner.warmUpRuns = 4
        runner.runs = 10

        when:
        def results = runner.run()

        then:
        results.assertFasterThanMaven()

        where:
        gradleTask | mavenTask
        'assemble' | 'clean package'
        'test'     | 'clean test'
    }

    void configureMavenOptions(JavaTestProject testProject) {
        def daemonMemory = testProject.daemonMemory
        runner.jvmOpts.addAll(["-Xms${daemonMemory}", "-Xmx${daemonMemory}"])
        if (testProject.parallel) {
            runner.mvnArgs << '-T' << testProject.maxWorkers
        }
    }
}
