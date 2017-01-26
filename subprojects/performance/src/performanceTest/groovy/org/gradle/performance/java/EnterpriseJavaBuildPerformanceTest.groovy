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

package org.gradle.performance.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.categories.PerformanceExperiment
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(PerformanceExperiment)
class EnterpriseJavaBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll("Builds '#testProject' calling #tasks (daemon)")
    def "build"() {
        given:
        runner.testId = "Enterprise Java $testProject ${tasks.join(' ')} (daemon)"
        runner.testProject = testProject
        runner.tasksToRun = tasks
        runner.gradleOpts = ["-Xms8g", "-Xmx8g"]
        setupRunner()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject            | tasks
        'largeEnterpriseBuild' | ['idea']
        'largeEnterpriseBuild' | ['assemble']
    }

    private void setupRunner() {
        runner.warmUpRuns = 2
        runner.runs = 6
        runner.setupCleanupOnOddRounds()
    }

    @Unroll("Builds '#testProject' calling #tasks (daemon) in parallel")
    def "build parallel"() {
        given:
        runner.testId = "Enterprise Java $testProject ${tasks.join(' ')} (daemon, parallel)"
        runner.testProject = testProject
        runner.tasksToRun = tasks
        runner.gradleOpts = ["-Xms8g", "-Xmx8g"]
        runner.args = ['-Dorg.gradle.parallel=true', '-Dorg.gradle.parallel.intra=true']
        setupRunner()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject            | tasks
        'largeEnterpriseBuild' | ['idea']
        'largeEnterpriseBuild' | ['assemble']
    }
}
