/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.performance.categories.NativePerformanceTest
import org.gradle.performance.measure.DataAmount
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.measure.Duration.millis

@Category(NativePerformanceTest)
class NativeBuildDependentsPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("Project '#testProject' measuring build dependents speed for #subprojectPath")
    def "build dependents of native project"() {
        given:
        runner.testId = "build dependents of native project $testProject"
        runner.testProject = testProject
        runner.tasksToRun = [ "$subprojectPath:$taskName" ]
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression
        runner.targetVersions = ['nightly']
        runner.useDaemon = true

        runner.args += ["--parallel", "--max-workers=4"]

        runner.maxMemoryRegression = DataAmount.mbytes(100)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject        | subprojectPath | taskName                | maxExecutionTimeRegression
        "nativeDependents"     | ':libA0' | 'buildDependentsLibA00' | millis(1000) // has the most dependent components
        "nativeDependents"     | ':libA6' | 'buildDependentsLibA60' | millis(1000) // has a few dependent components
        "nativeDependents"     | ':exeA0' | 'buildDependentsExeA00' | millis(1000) // has no dependent components
        // TODO: Re-enable these once our memory troubles are over.
        // "nativeDependentsDeep" | ':libA0' | 'buildDependentsLibA00' | millis(1000) // has the most dependent components
        // "nativeDependentsDeep" | ':exeA0' | 'buildDependentsExeA00' | millis(1000) // has no dependent components
    }

    @Unroll("Project '#testProject' measuring build dependents report speed for #subprojectPath")
    def "build dependents report for native project"() {
        given:
        runner.testId = "build dependents of native project $testProject"
        runner.testProject = testProject
        runner.tasksToRun = [ "$subprojectPath:dependentComponents" ]
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression
        runner.targetVersions = ['nightly']
        runner.useDaemon = true

        runner.args += ["--parallel", "--max-workers=4"]

        runner.maxMemoryRegression = DataAmount.mbytes(100)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject            | subprojectPath | maxExecutionTimeRegression
        "nativeDependents"     | ':libA0'       | millis(1000) // has the most dependent components
        "nativeDependents"     | ':libA6'       | millis(1000) // has a few dependent components
        "nativeDependents"     | ':exeA0'       | millis(1000) // has no dependent components
        // TODO: Re-enable these once our memory troubles are over.
        // "nativeDependentsDeep" | ':libA0'       | millis(1000) // has the most dependent components
        // "nativeDependentsDeep" | ':exeA0'       | millis(1000) // has no dependent components
    }
}
