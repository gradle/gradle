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
import org.junit.experimental.categories.Category
import spock.lang.Ignore
import spock.lang.Unroll

@Ignore
@Category(NativePerformanceTest)
class NativeBuildDependentsPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("Project '#testProject' measuring build dependents speed for #subprojectPath")
    def "build dependents of native project"() {
        given:
        runner.testId = "build dependents of native project $testProject"
        runner.testProject = testProject
        runner.tasksToRun = [ "$subprojectPath:$taskName" ]
        runner.targetVersions = ['nightly']
        runner.useDaemon = true

        runner.args += ["--parallel", "--max-workers=4"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject        | subprojectPath | taskName
        "nativeDependents" | ':libA0'       | 'buildDependentsLibA00'
        "nativeDependents" | ':libA6'       | 'buildDependentsLibA60'
        "nativeDependents" | ':exeA0'       | 'buildDependentsExeA00'
        // TODO: Re-enable these once our memory troubles are over.
        // "nativeDependentsDeep" | ':libA0' | 'buildDependentsLibA00'
        // "nativeDependentsDeep" | ':exeA0' | 'buildDependentsExeA00'
    }

    @Unroll("Project '#testProject' measuring build dependents report speed for #subprojectPath")
    def "build dependents report for native project"() {
        given:
        runner.testId = "build dependents of native project $testProject"
        runner.testProject = testProject
        runner.tasksToRun = [ "$subprojectPath:dependentComponents" ]
        runner.targetVersions = ['nightly']
        runner.useDaemon = true

        runner.args += ["--parallel", "--max-workers=4"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject        | subprojectPath
        "nativeDependents" | ':libA0'
        "nativeDependents" | ':libA6'
        "nativeDependents" | ':exeA0'
        // TODO: Re-enable these once our memory troubles are over.
        // "nativeDependentsDeep" | ':libA0'
        // "nativeDependentsDeep" | ':exeA0'
    }
}
