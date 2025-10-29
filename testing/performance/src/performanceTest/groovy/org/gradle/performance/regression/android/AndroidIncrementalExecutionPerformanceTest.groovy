/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.AndroidTestProject
import org.gradle.performance.fixture.IncrementalAndroidTestProject
import org.gradle.performance.regression.corefeature.AbstractIncrementalExecutionPerformanceTest
import org.gradle.test.fixtures.file.LeaksFileHandles

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.results.OperatingSystem.LINUX
import static org.gradle.performance.results.OperatingSystem.MAC_OS
import static org.gradle.performance.results.OperatingSystem.WINDOWS

@RunFor(
    @Scenario(type = PER_COMMIT, operatingSystems = [LINUX, WINDOWS, MAC_OS], testProjects = ["nowInAndroidBuild"])
)
@LeaksFileHandles("The TAPI keeps handles to the distribution it starts open in the test JVM")
class AndroidIncrementalExecutionPerformanceTest extends AbstractIncrementalExecutionPerformanceTest implements AndroidPerformanceTestFixture {
    IncrementalAndroidTestProject testProject

    def setup() {
        testProject = AndroidTestProject.findProjectFor(runner.testProject) as IncrementalAndroidTestProject
        AndroidTestProject.useAgpLatestStableOrRcVersion(runner)
        AndroidTestProject.useKotlinLatestStableOrRcVersion(runner)
        runner.args.add('-Dorg.gradle.parallel=true')
        runner.args.addAll(["--no-build-cache", "--no-scan"])
        // use the deprecated property so it works with previous versions
        runner.args.add("-D${StartParameterBuildOptions.ConfigurationCacheProblemsOption.DEPRECATED_PROPERTY_NAME}=warn")
        runner.warmUpRuns = 20
        applyDevelocityPlugin()
    }

    def "abi change#configurationCaching"() {
        given:
        testProject.configureForAbiChange(runner)
        enableConfigurationCaching(configurationCachingEnabled)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        configurationCachingEnabled << [true, false]
        configurationCaching = configurationCachingMessage(configurationCachingEnabled)
    }

    def "non-abi change#configurationCaching"() {
        given:
        testProject.configureForNonAbiChange(runner)
        enableConfigurationCaching(configurationCachingEnabled)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        configurationCachingEnabled << [true, false]
        configurationCaching = configurationCachingMessage(configurationCachingEnabled)
    }

    @RunFor([])
    def "up-to-date assembleDebug#configurationCaching"() {
        given:
        runner.tasksToRun = [testProject.taskToRunForChange]
        enableConfigurationCaching(configurationCachingEnabled)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        configurationCachingEnabled << [true, false]
        configurationCaching = configurationCachingMessage(configurationCachingEnabled)
    }
}
