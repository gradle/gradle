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
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.fixture.GradleInvocationSpec
import org.gradle.performance.fixture.InvocationCustomizer
import org.gradle.performance.fixture.InvocationSpec
import org.gradle.performance.fixture.MavenInvocationSpec
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.performance.mutator.ApplyNonAbiChangeToJavaSourceFileMutator
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.MEDIUM_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.MEDIUM_MONOLITHIC_JAVA_PROJECT

/**
 * Performance tests aimed at comparing the performance of Gradle for compiling and executing test suites, making
 * sure we are always faster than Maven.
 */
class JavaTestGradleVsMavenPerformanceTest extends AbstractGradleVsMavenPerformanceTest {

    @Unroll
    def "#gradleCleanupTask #gradleTask on #testProject (Gradle vs Maven)"() {
        given:
        runner.testGroup = "Gradle vs Maven test build using Java plugin"
        runner.testProject = testProject
        runner.jvmOpts << "-Xms${testProject.daemonMemory}" << "-Xmx${testProject.daemonMemory}"
        if (testProject.parallel) {
            runner.mvnArgs << '-T' << testProject.maxWorkers
        }
        runner.gradleTasks = gradleTask
        runner.equivalentMavenTasks = mavenTask
        if (mavenTask == "package") {
            runner.mvnArgs << "-Dmaven.test.skip=true"
        }
        runner.warmUpRuns = 4
        runner.runs = 10

        setupCleanupOnOddRounds(gradleCleanupTask, mavenCleanupTask)

        when:
        def results = runner.run()

        then:
        results.assertFasterThanMaven()

        where:
        testProject                    | gradleTask    | mavenTask | gradleCleanupTask | mavenCleanupTask
        MEDIUM_MONOLITHIC_JAVA_PROJECT | 'assemble'    | 'package' | 'clean'           | 'clean'
        MEDIUM_MONOLITHIC_JAVA_PROJECT | 'test'        | 'test'    | 'cleanTest'       | '-help'

        MEDIUM_JAVA_MULTI_PROJECT      | 'assemble'    | 'package' | 'clean'           | 'clean'
        MEDIUM_JAVA_MULTI_PROJECT      | 'test'        | 'test'    | 'cleanTest'       | '-help'
    }

    @Unroll
    def "#gradleTask for non-abi change on #testProject (Gradle vs Maven)"() {
        given:
        runner.testGroup = "Gradle vs Maven test build using Java plugin"
        runner.testProject = testProject
        runner.jvmOpts  << "-Xms${testProject.daemonMemory}" << "-Xmx${testProject.daemonMemory}"
        if (testProject.parallel) {
            runner.mvnArgs << '-T' << testProject.maxWorkers
        }
        runner.gradleTasks = gradleTask
        runner.equivalentMavenTasks = mavenTask
        if (mavenTask == "package") {
            runner.mvnArgs << "-Dmaven.test.skip=true"
        }
        runner.buildExperimentListener = new ApplyNonAbiChangeToJavaSourceFileMutator(fileToChange)

        when:
        def results = runner.run()

        then:
        results.assertFasterThanMaven()

        where:
        testProject                    | gradleTask     | mavenTask | fileToChange
        MEDIUM_MONOLITHIC_JAVA_PROJECT | 'test'         | 'test'               | "src/main/java/org/gradle/test/performance/mediummonolithicjavaproject/p0/Production0.java"
        MEDIUM_MONOLITHIC_JAVA_PROJECT | 'assemble'     | 'package'            | "src/main/java/org/gradle/test/performance/mediummonolithicjavaproject/p0/Production0.java"

        MEDIUM_JAVA_MULTI_PROJECT      | 'test'         | 'test'               | "project0/src/main/java/org/gradle/test/performance/mediumjavamultiproject/project0/p0/Production0.java"
        MEDIUM_JAVA_MULTI_PROJECT      | 'assemble'     | 'package'            | "project0/src/main/java/org/gradle/test/performance/mediumjavamultiproject/project0/p0/Production0.java"
    }

    void setupCleanupOnOddRounds(String gradleCleanupTask, String mavenCleanupTarget) {
        runner.invocationCustomizer = new InvocationCustomizer() {
            @Override
            def <T extends InvocationSpec> T customize(BuildExperimentInvocationInfo invocationInfo, T invocationSpec) {
                if (invocationInfo.iterationNumber % 2 == 1) {
                    if (invocationSpec instanceof GradleInvocationSpec) {
                        invocationSpec.withBuilder()
                        .tasksToRun([gradleCleanupTask])
                        .build() as T
                    } else {
                        (invocationSpec as MavenInvocationSpec).withBuilder()
                            .tasksToRun([mavenCleanupTarget])
                            .build() as T
                    }
                } else {
                    invocationSpec
                }
            }
        }
        runner.buildExperimentListener = new BuildExperimentListenerAdapter() {
            @Override
            void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
                if (invocationInfo.iterationNumber % 2 == 1) {
                    measurementCallback.omitMeasurement()
                }
            }
        }
    }
}
