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

package org.gradle.performance.regression.nativeplatform

import org.gradle.performance.AbstractCrossVersionGradleProfilerPerformanceTest
import spock.lang.Unroll

class NativeBuildDependentsPerformanceTest extends AbstractCrossVersionGradleProfilerPerformanceTest {

    def setup() {
        runner.targetVersions = ["6.2-20200108160029+0000"]
        runner.minimumBaseVersion = "4.0"
    }

    @Unroll
    def "#task on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = [ task ]
        runner.args += ["--parallel", "--max-workers=4"]
        runner.gradleOpts = ["-Xms3g", "-Xmx3g"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject            | task
        'nativeDependents'     | 'libA0:buildDependentsLibA0'
        // TODO Enable once runnable on CI (google test & target platform)
        // 'largeNativeBuild'     | 'project432:buildDependentsExternalComponent111'
        // TODO Re-evaluate this scenario: memory consumption stress case, gradleOpts = ['-Xms4g', '-Xmx4g']
        // The generated dependency graph is rather complex and deep, unrealistic?
        // 'nativeDependentsDeep' | 'libA0:buildDependentsLibA0'
    }

    @Unroll
    def "#subprojectPath:dependentComponents on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = [ "$subprojectPath:dependentComponents" ]
        runner.args += ["--parallel", "--max-workers=4"]
        runner.gradleOpts = ["-Xms3g", "-Xmx3g"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject            | subprojectPath
        'nativeDependents'     | 'libA0'
        // TODO Enable once runnable on CI (google test & target platform)
        // 'largeNativeBuild'     | 'project432'
        // TODO Re-evaluate this scenario: memory consumption stress case, gradleOpts = ['-Xms4g', '-Xmx4g']
        // The generated dependency graph is rather complex and deep, unrealistic?
        // 'nativeDependentsDeep' | 'libA0'
    }
}
