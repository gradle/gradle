/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.performance.regression.android

import org.gradle.api.JavaVersion
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.AndroidTestProject

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.results.OperatingSystem.LINUX
import static org.gradle.performance.results.OperatingSystem.MAC_OS
import static org.gradle.performance.results.OperatingSystem.WINDOWS

class NowInAndroidBuildPerformanceTest extends AbstractCrossVersionPerformanceTest implements AndroidPerformanceTestFixture {

    //TODO: is PER_COMMIT what we want?

    //TODO: is it ok to run on all major operating systems?

    def setup() {
        AndroidTestProject.useJavaVersion(runner, JavaVersion.VERSION_17)
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = [MAC_OS, LINUX, WINDOWS], testProjects = "nowInAndroidBuild"),
    ])
    def "run #tasks"() {
        given:
        AndroidTestProject testProject = androidTestProject
        testProject.configure(runner)
        runner.tasksToRun = tasks.split(' ')
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        applyEnterprisePlugin()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        tasks                                        | warmUpRuns | runs
        'help'                                       | null       | null
    }

}
