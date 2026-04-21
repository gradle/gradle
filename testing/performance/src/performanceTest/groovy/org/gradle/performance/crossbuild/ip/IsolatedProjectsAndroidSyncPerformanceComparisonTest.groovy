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
import org.gradle.profiler.mutations.ApplyAbiChangeToKotlinSourceFileMutator

import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["android500Kts"])
)
class IsolatedProjectsAndroidSyncPerformanceComparisonTest extends AbstractCrossBuildPerformanceTest {

    private static int maxWorkers = 8

    def setup() {
        // NOTE: see the javadoc for required environment and possible configuration
        AndroidSyncPerformanceTestFixture.configureStudio(runner)
    }

    // TODO:isolated introduce cold/warm daemon variation
    def "sync Studio after included build logic refactoring"() {
        given:
        def runner = getRunner() // otherwise, IDEA thinks it's PerformanceTestRunner despite the override

        runner.addBuildMutator { settings ->
            new ApplyAbiChangeToKotlinSourceFileMutator(new File(settings.projectDir, "build-logic/convention/src/main/kotlin/org/example/awesome/utils.kt"))
        }

        // 'Moderne' configuration that is used by performance aware teams
        runner.baseline {
            displayName("moderne")
            invocation {
                args(
                    "-Dorg.gradle.parallel=true",
                    "-Dorg.gradle.configuration-cache=true",
                    "-Dorg.gradle.configuration-cache.parallel=true",
                    "-Dorg.gradle.configureondemand=false",
                )
            }
        }

        // Moderne plus Configure-on-Demand, which is popular in Android world
        runner.baseline {
            displayName("moderne-cod")
            invocation {
                args(
                    "-Dorg.gradle.parallel=true",
                    "-Dorg.gradle.configuration-cache=true",
                    "-Dorg.gradle.configuration-cache.parallel=true",
                    "-Dorg.gradle.configureondemand=true",
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
        def moderneWithConfigureOnDemand = buildBaselineResults(result, "moderne-cod")
        def ip = result.buildResult("ip")

        // TODO:isolated assert that IP is not just faster, but faster with a scaling factor
        // TODO:isolated assert an upper bound of faster to visibly lock-in performance improvements
        println(moderne.getSpeedStatsAgainst("ip", ip))
        !moderne.significantlyFasterThan(ip)

        println(moderneWithConfigureOnDemand.getSpeedStatsAgainst("ip", ip))
        !moderneWithConfigureOnDemand.significantlyFasterThan(ip)
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
