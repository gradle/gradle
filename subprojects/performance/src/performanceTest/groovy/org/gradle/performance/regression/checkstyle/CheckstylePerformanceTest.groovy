/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.performance.regression.checkstyle

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.TestProjectLocator

import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["gradleBuildCurrent"])
)
class CheckstylePerformanceTest extends AbstractCrossVersionPerformanceTest {

    def "run checkstyle"() {
        given:
        runner.warmUpRuns = 2
        runner.runs = 10
        runner.targetVersions = ["7.5-20220228232029+0000"]
        runner.tasksToRun = ["clean", "checkstyleMain", "checkstyleTest"]
        runner.args.addAll(["--no-build-cache", "--no-configuration-cache", "--info"])
        File gradlePropertiesFile = new File(TestProjectLocator.findProjectDir(runner.testProject), "gradle.properties")
        gradlePropertiesFile << "\norg.gradle.java.home=/opt/jdk/adoptium-open-jdk-11\n"

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
