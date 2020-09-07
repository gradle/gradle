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
import org.gradle.profiler.mutations.ApplyNonAbiChangeToJavaSourceFileMutator
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.MEDIUM_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.MEDIUM_MONOLITHIC_JAVA_PROJECT

/**
 * Performance tests aimed at comparing the performance of Gradle for compiling and executing test suites, making
 * sure we are always faster than Maven.
 */
class JavaTestGradleVsMavenPerformanceTest extends AbstractGradleVsMavenPerformanceTest {

    @Unroll
    def "clean #gradleTask on #testProject (Gradle vs Maven)"() {
        given:
        runner.testGroup = "Gradle vs Maven test build using Java plugin"
        runner.testProject = testProject
        runner.jvmOpts << "-Xms${testProject.daemonMemory}" << "-Xmx${testProject.daemonMemory}"
        if (testProject.parallel) {
            runner.mvnArgs << '-T' << testProject.maxWorkers
        }
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
        testProject                    | gradleTask | mavenTask
        MEDIUM_MONOLITHIC_JAVA_PROJECT | 'assemble' | 'package'
        MEDIUM_MONOLITHIC_JAVA_PROJECT | 'test'     | 'test'

        MEDIUM_JAVA_MULTI_PROJECT      | 'assemble' | 'package'
        MEDIUM_JAVA_MULTI_PROJECT      | 'test'     | 'test'
    }

    @Unroll
    def "#gradleTask for non-abi change on #testProject (Gradle vs Maven)"() {
        given:
        runner.testGroup = "Gradle vs Maven test build using Java plugin"
        runner.testProject = testProject
        runner.jvmOpts << "-Xms${testProject.daemonMemory}" << "-Xmx${testProject.daemonMemory}"
        if (testProject.parallel) {
            runner.mvnArgs << '-T' << testProject.maxWorkers
        }
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
        testProject                    | gradleTask | mavenTask       | fileToChange
        MEDIUM_MONOLITHIC_JAVA_PROJECT | 'test'     | 'clean test'    | "src/main/java/org/gradle/test/performance/mediummonolithicjavaproject/p0/Production0.java"
        MEDIUM_MONOLITHIC_JAVA_PROJECT | 'assemble' | 'clean package' | "src/main/java/org/gradle/test/performance/mediummonolithicjavaproject/p0/Production0.java"

        MEDIUM_JAVA_MULTI_PROJECT      | 'test'     | 'clean test'    | "project0/src/main/java/org/gradle/test/performance/mediumjavamultiproject/project0/p0/Production0.java"
        MEDIUM_JAVA_MULTI_PROJECT      | 'assemble' | 'clean package' | "project0/src/main/java/org/gradle/test/performance/mediumjavamultiproject/project0/p0/Production0.java"
    }
}
