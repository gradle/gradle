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
import org.gradle.performance.IsolatedProjectsSyncPerformanceTestFixture
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.GradleBuildExperimentSpec
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.profiler.mutations.ApplyAbiChangeToKotlinSourceFileMutator

import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX
import static org.gradle.profiler.buildops.BuildOperationMeasurementKind.TIME_TO_LAST_INCLUSIVE

/**
 * Compare android studio sync performance between vintage and IP.
 */
@RunFor(
    @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["android500Kts"])
)
class IsolatedProjectsAndroidSyncPerformanceComparisonTest extends AbstractCrossBuildPerformanceTest {

    static String cold = "cold"
    static String warm = "warm"

    def "build logic abi change with #daemon daemon"() {
        given:
        def runner = getRunner() // otherwise, IDEA thinks it's PerformanceTestRunner despite the override

        runner.measureBuildOperation("org.gradle.initialization.ConfigureBuildBuildOperationType", TIME_TO_LAST_INCLUSIVE)

        runner.addBuildMutator { settings ->
            new ApplyAbiChangeToKotlinSourceFileMutator(new File(settings.projectDir, "build-logic/convention/src/main/kotlin/org/example/awesome/utils.kt"))
        }

        AndroidSyncPerformanceTestFixture.configureStudio(runner)
        IsolatedProjectsSyncPerformanceTestFixture.configureStudio(runner)

        // 'Moderne' configuration that is used by performance aware teams
        runner.baseline {
            displayName("moderne")
            invocation {
                jvmArgs(
                    "-Dorg.gradle.caching=true",
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
                jvmArgs(
                    "-Dorg.gradle.caching=true",
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
                jvmArgs(
                    "-Dorg.gradle.caching=true",
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

        println(moderne.getSpeedStatsAgainst("ip", ip))
        !moderne.significantlyFasterThan(ip)

        println(moderneWithConfigureOnDemand.getSpeedStatsAgainst("ip", ip))
        !moderneWithConfigureOnDemand.significantlyFasterThan(ip)

        where:
        daemon | _
        cold   | _
        warm   | _
    }

    @Override
    protected void defaultSpec(GradleBuildExperimentSpec.GradleBuilder builder) {
        builder.warmUpCount(20)
            .invocationCount(20)
    }
}
