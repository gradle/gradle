/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.performance.crossbuild.ip

import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.AndroidSyncPerformanceTestFixture
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.GradleBuildExperimentSpec
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.performance.results.SpeedupAssertions
import org.gradle.profiler.mutations.ApplyAbiChangeToSourceFileMutator

import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["android100Kts", "android100Groovy", "nowInAndroidBuild"])
)
class IsolatedProjectsAndroidSyncPerformanceComparisonTest extends AbstractCrossBuildPerformanceTest {

    private static int maxWorkers = 8

    // Invoked from feature methods rather than Spock's setup() because
    // :performance:writeTmpPerformanceScenarioDefinitions (run by sanityCheck)
    // executes setup() for every feature even though the bodies are skipped.
    // configureStudio() requires ANDROID_SDK_ROOT, which would then break
    // sanityCheck on machines without that variable set.
    private void studioSetup() {
        // NOTE: see the javadoc for required environment and possible configuration
        AndroidSyncPerformanceTestFixture.configureStudio(runner)
    }

    // TODO:isolated introduce cold/warm daemon variation
    def "sync Studio after build logic ABI change"() {
        given:
        studioSetup()
        def runner = getRunner() // otherwise, IDEA thinks it's PerformanceTestRunner despite the override

        def testProject = runner.testProject
        def abiChangeSource = [
            "android100Kts"    : "build-logic/convention/src/main/java/org/example/awesome/AwesomeStringUtils.java",
            "android100Groovy" : "build-logic/convention/src/main/java/org/example/awesome/AwesomeStringUtils.java",
            "nowInAndroidBuild": "build-logic/convention/src/main/kotlin/com/google/samples/apps/nowinandroid/AndroidCompose.kt",
        ][testProject]
        assert abiChangeSource != null: "No build-logic ABI change source configured for test project '$testProject'"

        runner.addBuildMutator { settings ->
            new ApplyAbiChangeToSourceFileMutator(new File(settings.projectDir, abiChangeSource))
        }

        // 'Moderne' configuration that is used by performance aware teams
        runner.baseline {
            displayName("moderne")
            invocation {
                args(
                    // ensure IP is disabled for sensible comparison for projects that already enabled it
                    "-Dorg.gradle.unsafe.isolated-projects=false",

                    "-Dorg.gradle.parallel=true",
                    "-Dorg.gradle.configuration-cache=true",
                    "-Dorg.gradle.configuration-cache.parallel=true",
                    "-Dorg.gradle.configureondemand=false",
                )
            }
        }

        runner.buildSpec {
            displayName("ip")
            invocation {
                args(
                    "-Dorg.gradle.unsafe.isolated-projects=true"
                )
            }
        }

        when:
        CrossBuildPerformanceResults result = runner.run()

        then:
        def moderne = buildBaselineResults(result, "moderne")
        def ip = result.buildResult("ip")

        println(moderne.getSpeedStatsAgainst("ip", ip))

        // Speedup of IP over moderne expected on this scenario.
        // The ceiling sits +0.10 above the floor: any improvement that pushes the speedup past it
        // fails the build, prompting us to ratchet both ends in the same PR rather than letting the
        // win silently erode later. Each check has its own ±5% noise band, so the practical pass
        // zone is roughly [floor − 5%, ceiling + 5%]. Do not delete the assertions when they fire.
        double minSpeedup = 2.4
        double maxSpeedup = minSpeedup + 0.10
        def location = "${this.class.simpleName}[testProject=${runner.testProject}, baseline=moderne]"
        SpeedupAssertions.assertSpeedupAtLeast(moderne.results, ip, minSpeedup, location)
        SpeedupAssertions.assertSpeedupAtMost(moderne.results, ip, maxSpeedup, location)
    }

    @Override
    protected void defaultSpec(GradleBuildExperimentSpec.GradleBuilder builder) {
        builder.warmUpCount(5)
            .invocationCount(10)
            .invocation {
                args(
                    // realistic defaults
                    "-Dorg.gradle.caching=true",
                    // scenario
                    "-Dorg.gradle.workers.max=$maxWorkers",
                    "--no-scan", // TODO:isolated benchmark with Develocity plugin as well
                )
            }
    }
}
