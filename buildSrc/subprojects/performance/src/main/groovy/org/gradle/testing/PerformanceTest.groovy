/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing

import groovy.transform.CompileStatic
import org.gradle.api.internal.tasks.options.Option
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.gradlebuild.test.integrationtests.DistributionTest

import javax.annotation.Nullable

/**
 * A test that checks execution time and memory consumption.
 */
@CacheableTask
@CompileStatic
class PerformanceTest extends DistributionTest {
    @Nullable
    String baselines

    @Nullable
    String warmups

    @Nullable
    String runs

    @Nullable
    String checks

    @Nullable
    String channel

    @Input
    String resultStoreClass = "org.gradle.performance.results.AllResultsStore"

    @OutputDirectory
    File debugArtifactsDirectory = new File(getProject().getBuildDir(), getName())

    @OutputDirectory
    File reportDir

    PerformanceTest() {
        outputs.doNotCacheIf("last and nightly version don't need cache at all", {
            List baselineList = baselines == null ? [] : baselines.split(',').collect { String baseline -> baseline.trim() }
            baselineList.contains('last') || baselineList.contains('nightly')
        })
    }

    @Option(option = "scenarios", description = "A semicolon-separated list of performance test scenario ids to run.")
    void setScenarios(String scenarios) {
        systemProperty("org.gradle.performance.scenarios", scenarios)
    }

    @Option(option = "baselines", description = "A comma or semicolon separated list of Gradle versions to be used as baselines for comparing.")
    void setBaselines(@Nullable String baselines) {
        this.baselines = baselines
        systemProperty("org.gradle.performance.baselines", baselines)
    }

    @Option(option = "warmups", description = "Number of warmups before measurements")
    void setWarmups(@Nullable String warmups) {
        this.warmups = warmups
        systemProperty("org.gradle.performance.execution.warmups", warmups)
    }

    @Option(option = "runs", description = "Number of iterations of measurements")
    void setRuns(@Nullable String runs) {
        this.runs = runs
        systemProperty("org.gradle.performance.execution.runs", runs)
    }

    @Option(option = "checks", description = "Tells which regressions to check. One of [none, speed, all]")
    void setChecks(@Nullable String checks) {
        this.checks = checks
        systemProperty("org.gradle.performance.execution.checks", checks)
    }

    @Option(option = "channel", description = "Channel to use when running the performance test. By default, 'commits'.")
    void setChannel(@Nullable String channel) {
        this.channel = channel
        systemProperty("org.gradle.performance.execution.channel", channel)
    }

    void setDebugArtifactsDirectory(File debugArtifactsDirectory) {
        this.debugArtifactsDirectory = debugArtifactsDirectory
    }

    @Option(option = "flamegraphs", description = "If set to 'true', activates flamegraphs and stores them into the 'flames' directory name under the debug artifacts directory.")
    void setFlamegraphs(String flamegraphs) {
        if ("true".equals(flamegraphs)) {
            File artifactsDirectory = new File(getDebugArtifactsDirectory(), "flames")
            systemProperty("org.gradle.performance.flameGraphTargetDir", project.rootDir.toPath().relativize(artifactsDirectory.toPath()))
        }
    }

    @Option(option = "sampling-interval", description = "How many ms to wait between two samples when profiling")
    void setSamplingInterval(String samplingInterval) {
        //no-op, remove once build configurations have been adjusted to no longer call this
    }

}
