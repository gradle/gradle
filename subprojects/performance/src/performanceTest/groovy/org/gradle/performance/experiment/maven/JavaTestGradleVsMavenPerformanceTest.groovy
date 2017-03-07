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
import org.gradle.performance.mutator.ApplyNonAbiChangeToJavaSourceFileMutator
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_MONOLITHIC_JAVA_PROJECT

/**
 * Performance tests aimed at comparing the performance of Gradle for compiling and executing test suites, making
 * sure we are always faster than Maven.
 */
class JavaTestGradleVsMavenPerformanceTest extends AbstractGradleVsMavenPerformanceTest {

    @Unroll
    def "#gradleTasks on #testProject (Gradle vs Maven)"() {
        given:
        runner.testGroup = "Gradle vs Maven test build using Java plugin"
        runner.testProject = testProject
        runner.jvmOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.gradleTasks = gradleTasks
        runner.equivalentMavenTasks = equivalentMavenTasks

        runner.configure()

        when:
        def results = runner.run()

        then:
        results.assertComparesWithMaven()

        where:
        testProject                   | gradleTasks       | equivalentMavenTasks
        LARGE_MONOLITHIC_JAVA_PROJECT | 'cleanTest test'  | 'test'
        LARGE_MONOLITHIC_JAVA_PROJECT | 'clean test'      | 'clean test'
        LARGE_MONOLITHIC_JAVA_PROJECT | 'clean build'     | 'clean verify'
        LARGE_MONOLITHIC_JAVA_PROJECT | 'cleanTest build' | 'verify'

        LARGE_JAVA_MULTI_PROJECT      | 'cleanTest test'  | 'test'
        LARGE_JAVA_MULTI_PROJECT      | 'clean test'      | 'clean test'
        LARGE_JAVA_MULTI_PROJECT      | 'clean build'     | 'clean verify'
        LARGE_JAVA_MULTI_PROJECT      | 'cleanTest build' | 'verify'
    }

    @Unroll
    def "test change on #testProject (Gradle vs Maven)"() {
        given:
        runner.testGroup = "Gradle vs Maven test build using Java plugin"
        runner.testProject = testProject
        runner.jvmOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.gradleTasks = gradleTasks
        runner.equivalentMavenTasks = equivalentMavenTasks
        runner.buildExperimentListener = new ApplyNonAbiChangeToJavaSourceFileMutator(fileToChange)

        runner.configure()

        when:
        def results = runner.run()

        then:
        results.assertComparesWithMaven()

        where:
        testProject                   | gradleTasks | equivalentMavenTasks | fileToChange
        LARGE_MONOLITHIC_JAVA_PROJECT | 'build'     | 'verify'             | "src/main/java/org/gradle/test/performance/largemonolithicjavaproject/p0/Production0.java"
        LARGE_JAVA_MULTI_PROJECT      | 'build'     | 'verify'             | "project450/src/main/java/org/gradle/test/performance/largejavamultiproject/project450/p2250/Production45000.java"
    }
}
