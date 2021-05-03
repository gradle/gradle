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

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import spock.lang.Unroll

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.results.OperatingSystem.LINUX

class NativeBuildDependentsPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def setup() {
        runner.targetVersions = ["7.1-20210427170827+0000"]
        runner.minimumBaseVersion = "4.0"
    }

    @RunFor(
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["nativeDependents"], iterationMatcher = ".*libA0.*")
    )
    @Unroll
    def "run #task"() {
        // TODO Enable once runnable on CI (google test & target platform)
        // 'largeNativeBuild'     | 'project432:buildDependentsExternalComponent111'
        // TODO Re-evaluate this scenario: memory consumption stress case, gradleOpts = ['-Xms4g', '-Xmx4g']
        // The generated dependency graph is rather complex and deep, unrealistic?
        // 'nativeDependentsDeep' | 'libA0:buildDependentsLibA0'
        given:
        runner.tasksToRun = [task]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        task << ['libA0:buildDependentsLibA0', 'project432:buildDependentsExternalComponent111']
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["nativeDependents"], iterationMatcher = ".*libA0.*")
    ])
    @Unroll
    def "run #subprojectPath:dependentComponents"() {
        // TODO Enable once runnable on CI (google test & target platform)
        // 'largeNativeBuild'     | 'project432'
        // TODO Re-evaluate this scenario: memory consumption stress case, gradleOpts = ['-Xms4g', '-Xmx4g']
        // The generated dependency graph is rather complex and deep, unrealistic?
        // 'nativeDependentsDeep' | 'libA0'
        given:
        runner.tasksToRun = ["$subprojectPath:dependentComponents"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        subprojectPath << ['libA0', 'project432']
    }
}
