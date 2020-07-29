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

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption
import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.internal.scan.config.fixtures.ApplyGradleEnterprisePluginFixture
import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.categories.PerformanceExperiment
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.GradleBuildExperimentSpec
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.ScenarioContext
import org.gradle.profiler.mutations.AbstractCleanupMutator
import org.gradle.profiler.mutations.ClearInstantExecutionStateMutator
import org.gradle.profiler.mutations.ClearProjectCacheMutator
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.regression.android.IncrementalAndroidTestProject.SANTA_TRACKER_JAVA
import static org.gradle.performance.regression.android.IncrementalAndroidTestProject.SANTA_TRACKER_KOTLIN

@Category(PerformanceExperiment)
class FasterIncrementalAndroidBuildsPerformanceTest extends AbstractCrossBuildPerformanceTest {
    private static final String AGP_TARGET_VERSION = "4.0"

    def setup() {
        runner.testGroup = "incremental android changes"
    }

    @Unroll
    def "faster non-abi change on #testProject (build comparison)"() {
        given:
        buildSpecForSupportedOptimizations(testProject) {
            testProject.configureForNonAbiChange(delegate)
        }

        when:
        def results = runner.run()
        then:
        results

        where:
        testProject << [SANTA_TRACKER_KOTLIN, SANTA_TRACKER_JAVA]
    }

    @Unroll
    def "faster abi-change on #testProject (build comparison)"() {
        given:
        buildSpecForSupportedOptimizations(testProject) {
            testProject.configureForAbiChange(delegate)
        }

        when:
        def results = runner.run()
        then:
        results

        where:
        testProject << [SANTA_TRACKER_KOTLIN, SANTA_TRACKER_JAVA]
    }

    private void buildSpecForSupportedOptimizations(IncrementalAndroidTestProject testProject, @DelegatesTo(GradleBuildExperimentSpec.GradleBuilder) Closure scenarioConfiguration) {
        supportedOptimizations(testProject).each { name, Set<Optimization> enabledOptimizations ->
            runner.buildSpec {
                invocation.args(*enabledOptimizations*.arguments.flatten())
                testProject.configureForLatestAgpVersionOfMinor(delegate, AGP_TARGET_VERSION)
                displayName(name)

                final Closure clonedClosure = scenarioConfiguration.clone() as Closure
                clonedClosure.setResolveStrategy(Closure.DELEGATE_FIRST)
                clonedClosure.setDelegate(delegate)
                clonedClosure.call()
            }
        }
    }

    private static Map<String, Set<Optimization>> supportedOptimizations(IncrementalAndroidTestProject testProject) {
        // Kotlin is not supported for configuration caching
        return testProject == SANTA_TRACKER_KOTLIN
            ? [
            "no optimizations": EnumSet.noneOf(Optimization),
            "FS watching": EnumSet.of(Optimization.WATCH_FS)
        ]
            : [
            "no optimizations": EnumSet.noneOf(Optimization),
            "FS watching": EnumSet.of(Optimization.WATCH_FS),
            "configuration caching": EnumSet.of(Optimization.CONFIGURATION_CACHING),
            "all optimizations": EnumSet.allOf(Optimization)
        ]
    }

    @Override
    protected void defaultSpec(BuildExperimentSpec.Builder builder) {
        if (builder instanceof GradleBuildExperimentSpec.GradleBuilder) {
            builder.invocation.args(AndroidGradlePluginVersions.OVERRIDE_VERSION_CHECK)
            builder.invocation.args(
                "-Dorg.gradle.workers.max=8",
                "--no-build-cache",
                "--no-scan"
            )
            builder.invocation.useToolingApi()
            builder.warmUpCount(1)
            builder.invocationCount(60)
            applyEnterprisePlugin(builder)
            builder.addBuildMutator { InvocationSettings invocationSettings ->
                new ClearInstantExecutionStateMutator(invocationSettings.projectDir, AbstractCleanupMutator.CleanupSchedule.SCENARIO)
            }
            builder.addBuildMutator { InvocationSettings invocationSettings ->
                new ClearProjectCacheMutator(invocationSettings.projectDir, AbstractCleanupMutator.CleanupSchedule.SCENARIO)
            }
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
