/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.internal.tasks.execution.ExecuteTaskActionBuildOperationType
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption
import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.internal.scan.config.fixtures.ApplyGradleEnterprisePluginFixture
import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.GradleBuildExperimentSpec
import org.gradle.performance.fixture.IncrementalAndroidTestProject
import org.gradle.performance.fixture.IncrementalTestProject
import org.gradle.performance.fixture.TestProjects
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.ScenarioContext
import org.gradle.profiler.mutations.AbstractCleanupMutator
import org.gradle.profiler.mutations.ClearConfigurationCacheStateMutator
import org.gradle.profiler.mutations.ClearProjectCacheMutator

import static org.gradle.performance.annotations.ScenarioType.PER_WEEK
import static org.gradle.performance.results.OperatingSystem.LINUX
import static org.gradle.performance.results.OperatingSystem.MAC_OS
import static org.gradle.performance.results.OperatingSystem.WINDOWS

@RunFor(
    @Scenario(type = PER_WEEK, operatingSystems = [LINUX], testProjects = ["santaTrackerAndroidBuild", "santaTrackerAndroidJavaBuild"])
)
class FasterIncrementalAndroidBuildsPerformanceTest extends AbstractCrossBuildPerformanceTest {
    private static final String AGP_TARGET_VERSION = "4.2"
    private static final String KOTLIN_TARGET_VERSION = new KotlinGradlePluginVersions().latests.last()
    private static final String BASELINE_VERSION = "6.8-milestone-1"

    def setup() {
        runner.testGroup = "incremental android changes"
    }

    def "faster non-abi change (build comparison)"() {
        given:
        buildSpecForSupportedOptimizations(testProject) {
            testProject.configureForNonAbiChange(delegate)
        }

        when:
        def results = runner.run()
        then:
        results
    }

    def "faster abi change (build comparison)"() {
        given:
        buildSpecForSupportedOptimizations(testProject) {
            testProject.configureForAbiChange(delegate)
        }

        when:
        def results = runner.run()
        then:
        results
    }

    @RunFor([
        @Scenario(type = PER_WEEK, operatingSystems = [LINUX, MAC_OS, WINDOWS], testProjects = ["santaTrackerAndroidBuild"])
    ])
    def "file system watching baseline non-abi change (build comparison)"() {
        given:
        runner.measureBuildOperation(ExecuteTaskActionBuildOperationType.name)
        runner.buildSpec {
            displayName("with file system watching and configuration caching - baseline")
            configureForNonParallel(delegate)
            testProject.configureForNonAbiChange(delegate)
            invocation {
                args.addAll(Optimization.WATCH_FS.arguments)
                args.addAll(Optimization.CONFIGURATION_CACHING.arguments)
                distribution(buildContext.distribution(BASELINE_VERSION))
            }
        }
        runner.buildSpec {
            displayName("with file system watching and configuration caching")
            configureForNonParallel(delegate)
            testProject.configureForNonAbiChange(delegate)
            invocation {
                args.addAll(Optimization.WATCH_FS.arguments)
                args.addAll(Optimization.CONFIGURATION_CACHING.arguments)
            }
        }
        runner.buildSpec {
            displayName("without file system watching - baseline")
            configureForNonParallel(delegate)
            testProject.configureForNonAbiChange(delegate)
            invocation {
                distribution(buildContext.distribution(BASELINE_VERSION))
            }
        }
        runner.buildSpec {
            displayName("without file system watching")
            configureForNonParallel(delegate)
            testProject.configureForNonAbiChange(delegate)
        }

        when:
        def results = runner.run()
        then:
        results
    }

    private static void configureForNonParallel(GradleBuildExperimentSpec.GradleBuilder builder) {
        // We want to measure the overhead of Gradle for a certain build.
        // In order to do so we run with only one worker and measure the work execution times.
        builder.invocation {
            args.add("-Dorg.gradle.parallel=false")
            args.add("-Dorg.gradle.workers.max=1")
        }
        builder.warmUpCount(10)
        builder.invocationCount(30)
        builder.measuredBuildOperations(['org.gradle.internal.execution.steps.ExecuteStep$Operation'])
    }

    private IncrementalTestProject getTestProject() {
        TestProjects.projectFor(runner.testProject) as IncrementalTestProject
    }

    private void buildSpecForSupportedOptimizations(IncrementalTestProject testProject, @DelegatesTo(GradleBuildExperimentSpec.GradleBuilder) Closure scenarioConfiguration) {
        supportedOptimizations(testProject).each { name, Set<Optimization> enabledOptimizations ->
            runner.buildSpec {
                invocation.args(*enabledOptimizations*.arguments.flatten())
                displayName(name)

                final Closure clonedClosure = scenarioConfiguration.clone() as Closure
                clonedClosure.setResolveStrategy(Closure.DELEGATE_FIRST)
                clonedClosure.setDelegate(delegate)
                clonedClosure.call()
            }
        }
    }

    private static Map<String, Set<Optimization>> supportedOptimizations(IncrementalTestProject testProject) {
        // Kotlin is not supported for configuration caching
        return [
            "no optimizations": EnumSet.noneOf(Optimization),
            "FS watching": EnumSet.of(Optimization.WATCH_FS),
            "configuration caching": EnumSet.of(Optimization.CONFIGURATION_CACHING),
            "all optimizations": EnumSet.allOf(Optimization)
        ]
    }

    @Override
    protected void defaultSpec(GradleBuildExperimentSpec.GradleBuilder builder) {
        builder.invocation.args(AndroidGradlePluginVersions.OVERRIDE_VERSION_CHECK)
        IncrementalAndroidTestProject.configureForLatestAgpVersionOfMinor(builder, AGP_TARGET_VERSION)
        builder.invocation.args(
            "--no-build-cache",
            "--no-scan",
            "-DkotlinVersion=${KOTLIN_TARGET_VERSION}"
        )
        builder.invocation.useToolingApi()
        builder.warmUpCount(1)
        builder.invocationCount(60)
        applyEnterprisePlugin(builder)
        builder.addBuildMutator { InvocationSettings invocationSettings ->
            new ClearConfigurationCacheStateMutator(invocationSettings.projectDir, AbstractCleanupMutator.CleanupSchedule.SCENARIO)
        }
        builder.addBuildMutator { InvocationSettings invocationSettings ->
            new ClearProjectCacheMutator(invocationSettings.projectDir, AbstractCleanupMutator.CleanupSchedule.SCENARIO)
        }
    }

    enum Optimization {
        CONFIGURATION_CACHING(
            "--${ConfigurationCacheOption.LONG_OPTION}",
            "--${ConfigurationCacheProblemsOption.LONG_OPTION}=warn" // TODO remove
        ),
        WATCH_FS("--${StartParameterBuildOptions.WatchFileSystemOption.LONG_OPTION}")

        Optimization(String... arguments) {
            this.arguments = arguments
        }

        final List<String> arguments
    }

    void applyEnterprisePlugin(GradleBuildExperimentSpec.GradleBuilder builder) {
        builder.addBuildMutator { invocationSettings ->
            new BuildMutator() {
                String originalSettingsFileText
                final File settingsFile = new File(invocationSettings.projectDir, "settings.gradle")

                @Override
                void beforeScenario(ScenarioContext context) {
                    originalSettingsFileText = settingsFile.text
                    ApplyGradleEnterprisePluginFixture.applyEnterprisePlugin(settingsFile)
                }

                @Override
                void afterScenario(ScenarioContext context) {
                    settingsFile.text = originalSettingsFileText
                }
            }
        }
    }
}
