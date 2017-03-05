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
import org.gradle.performance.fixture.JavaSourceFileUpdater
import spock.lang.Unroll

/**
 * Performance tests aimed at comparing the performance of Gradle for compiling and executing test suites, making
 * sure we are always faster than Maven.
 */
class JavaTestGradleVsMavenPerformanceTest extends AbstractGradleVsMavenPerformanceTest {
    @Unroll("Gradle vs Maven #description build for #template")
    def "cleanTest test performance test"() {
        given:
        runner.testGroup = "Gradle vs Maven test build using Java plugin"
        runner.testId = "$size $description with Java plugin"
        equivalentGradleAndMavenBuilds(template, description, gradleTasks, equivalentMavenTasks)

        when:
        def results = runner.run()

        then:
        results.assertComparesWithMaven(maxDiffMillis, maxDiffMB)

        where:
        template          | size     | description                 | gradleTasks           | equivalentMavenTasks | maxDiffMillis | maxDiffMB
        'mediumWithJUnit' | 'medium' | 'runs tests only'           | ['cleanTest', 'test'] | ['test']             | 10000         | 100
        'mediumWithJUnit' | 'medium' | 'clean build and run tests' | ['clean', 'test']     | ['clean', 'test']    | 5000          | 100
    }

    @Unroll("Gradle vs Maven #description build for #template")
    def "build performance test"() {
        given:
        runner.testGroup = "Gradle vs Maven build using Java plugin"
        runner.testId = "$size $description with Java plugin"
        equivalentGradleAndMavenBuilds(template, description, gradleTasks, equivalentMavenTasks)

        when:
        def results = runner.run()

        then:
        results.assertComparesWithMaven(maxDiffMillis, maxDiffMB)

        where:
        template          | size     | description        | gradleTasks            | equivalentMavenTasks | maxDiffMillis | maxDiffMB
        'mediumWithJUnit' | 'medium' | 'clean build'      | ['clean', 'build']     | ['clean', 'verify']  | 1000          | 100
        'mediumWithJUnit' | 'medium' | 'up-to-date build' | ['cleanTest', 'build'] | ['verify']           | 5000          | 100
    }

    @Unroll("Gradle vs Maven #description incremental build for #template")
    def "incremental build performance test"() {
        given:
        runner.testGroup = "Gradle vs Maven incremental build using Java plugin"
        runner.testId = "$size $description with Java plugin"
        runner.buildExperimentListener = new JavaSourceFileUpdater(10)
        equivalentGradleAndMavenBuilds(template, description, gradleTasks, equivalentMavenTasks)

        when:
        def results = runner.run()

        then:
        results.assertComparesWithMaven(maxDiffMillis, maxDiffMB)

        where:
        template          | size     | description         | gradleTasks | equivalentMavenTasks | maxDiffMillis | maxDiffMB
        'mediumWithJUnit' | 'medium' | 'incremental build' | ['build']   | ['verify']           | 0             | 100
        'largeWithJUnit'  | 'large'  | 'incremental build' | ['build']   | ['verify']           | 0             | 200
    }

    private void equivalentGradleAndMavenBuilds(String template, String description, List<String> gradleTasks, List<String> equivalentMavenTasks) {
        runner.baseline {
            projectName(template).displayName("Gradle $description for project $template").invocation {
                tasksToRun(gradleTasks).useDaemon().gradleOpts('-Xms1G', '-Xmx1G')
            }
        }
        runner.mavenBuildSpec {
            projectName(template).displayName("Maven $description for project $template").invocation {
                tasksToRun(equivalentMavenTasks).mavenOpts('-Xms1G', '-Xmx1G')
                    .args('-q', '-Dsurefire.printSummary=false')
            }
        }
    }
}
