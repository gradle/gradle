/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.performance

import org.gradle.performance.categories.JavaPerformanceTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.measure.DataAmount.mbytes
import static org.gradle.performance.measure.Duration.millis

@Category(JavaPerformanceTest)
class JavaSoftwareModelBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("Project '#testProject' measuring up-to-date checking speed")
    def "build java software model project"() {
        given:
        runner.testId = "build new java project $testProject" + (parallelWorkers ? " (parallel)" : "")
        runner.testProject = testProject
        runner.tasksToRun = ['build']
        runner.maxExecutionTimeRegression = maxTimeRegression
        runner.maxMemoryRegression = maxMemoryRegression
        runner.targetVersions = ['2.9', 'last']
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms2g", "-Xmx2g", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError"]
        if (parallelWorkers) {
            runner.args += ["--parallel", "--max-workers=$parallelWorkers".toString()]
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject               | maxTimeRegression | maxMemoryRegression | parallelWorkers
        "smallJavaSwModelProject" | millis(200)       | mbytes(5)           | 0
        "smallJavaSwModelProject" | millis(200)       | mbytes(5)           | 4
        "largeJavaSwModelProject" | millis(1000)      | mbytes(50)          | 0
        "largeJavaSwModelProject" | millis(1000)      | mbytes(50)          | 4
    }

    @Unroll("Project '#testProject' measuring clean build when no API is declared")
    def "clean build java software model project without API"() {
        given:
        runner.testId = "clean build java project $testProject which doesn't declare any API"
        runner.testProject = testProject
        runner.tasksToRun = ['clean', 'assemble']
        runner.maxExecutionTimeRegression = maxTimeRegression
        runner.maxMemoryRegression = maxMemoryRegression
        runner.targetVersions = ['2.9', '2.10', 'last']
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms2g", "-Xmx2g", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                                  | maxTimeRegression | maxMemoryRegression
        "smallJavaSwModelCompileAvoidanceWithoutApi" | millis(500)       | mbytes(5)
        "largeJavaSwModelCompileAvoidanceWithoutApi" | millis(4000)      | mbytes(50)
    }

    @Unroll("Project '#testProject' measuring incremental build when no API is declared")
    def "incremental build java software model project without API"() {
        given:
        runner.testId = "incremental build java project $testProject which doesn't declare any API"
        runner.testProject = testProject
        runner.tasksToRun = ['assemble']
        runner.maxExecutionTimeRegression = maxTimeRegression
        runner.maxMemoryRegression = maxMemoryRegression
        runner.targetVersions = ['2.10', '2.11', 'last']
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms2g", "-Xmx2g", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError"]
        runner.buildExperimentListener = new JavaSoftwareModelSourceFileUpdater(10, 0, 0)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                                  | maxTimeRegression | maxMemoryRegression
        "smallJavaSwModelCompileAvoidanceWithoutApi" | millis(500)       | mbytes(5)
        "largeJavaSwModelCompileAvoidanceWithoutApi" | millis(2000)      | mbytes(50)
    }

    @Unroll("Checking overhead of API stubbing when #cardinality.description")
    def "checks overhead of API stubbing when some files are updated"() {
        given:
        runner.testId = "overhead of API jar generation when ${cardinality.description}"
        runner.testProject = 'tinyJavaSwApiJarStubbingWithoutApi'
        runner.tasksToRun = ['project1:mainApiJar']
        runner.maxExecutionTimeRegression = maxTimeRegression
        runner.maxMemoryRegression = maxMemoryRegression
        runner.targetVersions = ['2.10', '2.11', 'last']
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms2g", "-Xmx2g", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError"]
        def updater = new JavaSoftwareModelSourceFileUpdater(100, 0, 0, cardinality)
        runner.buildExperimentListener = updater

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()


        where:
        cardinality                       | maxTimeRegression | maxMemoryRegression
        SourceUpdateCardinality.ONE_FILE  | millis(500)       | mbytes(5)
        SourceUpdateCardinality.ALL_FILES | millis(1000)      | mbytes(10)
    }
}
