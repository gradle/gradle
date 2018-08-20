/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.testing;

import org.gradle.api.internal.tasks.options.Option;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.gradlebuild.test.integrationtests.DistributionTest;
import org.gradle.api.specs.Spec;
import org.gradle.api.Task;

import org.gradle.api.tasks.CacheableTask;
import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 * A test that checks execution time and memory consumption.
 */
@CacheableTask
public class PerformanceTest extends DistributionTest {
    private String baselines;
    private String warmups;
    private String runs;
    private String checks;
    private String channel;
    private String resultStoreClass = "org.gradle.performance.results.AllResultsStore";
    private File debugArtifactsDirectory = new File(getProject().getBuildDir(), getName());

    public PerformanceTest() {
        getOutputs().doNotCacheIf("last and nightly are not concrete version so can't be cached", new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task task) {
                List<String> baseLineList = new ArrayList<>();
                if(baselines != null ) {
                    for(String baseline: baselines.split(",")) {
                        baseLineList.add(baseline.trim());
                    }
                }

                return baseLineList.contains("last") || baseLineList.contains("nightly");
            }
        });
    }

    @Input
    public String getResultStoreClass() {
        return resultStoreClass;
    }

    public void setResultStoreClass(String resultStoreClass) {
        this.resultStoreClass = resultStoreClass;
    }

    @Option(option = "scenarios", description = "A semicolon-separated list of performance test scenario ids to run.")
    public void setScenarios(String scenarios) {
        systemProperty("org.gradle.performance.scenarios", scenarios);
    }

    @Option(option = "baselines", description = "A comma or semicolon separated list of Gradle versions to be used as baselines for comparing.")
    public void setBaselines(@Nullable String baselines) {
        this.baselines = baselines;
        systemProperty("org.gradle.performance.baselines", baselines);
    }

    @Nullable
    public String getBaselines() {
        return baselines;
    }

    @Option(option = "warmups", description = "Number of warmups before measurements")
    public void setWarmups(@Nullable String warmups) {
        this.warmups = warmups;
        systemProperty("org.gradle.performance.execution.warmups", warmups);
    }

    @Nullable
    public String getWarmups() {
        return warmups;
    }

    @Option(option = "runs", description = "Number of iterations of measurements")
    public void setRuns(@Nullable String runs) {
        this.runs = runs;
        systemProperty("org.gradle.performance.execution.runs", runs);
    }


    @Nullable
    public String getRuns() {
        return runs;
    }

    @Option(option = "checks", description = "Tells which regressions to check. One of [none, speed, all]")
    public void setChecks(@Nullable String checks) {
        this.checks = checks;
        systemProperty("org.gradle.performance.execution.checks", checks);
    }

    @Nullable
    public String getChecks() {
        return checks;
    }

    @Nullable
    public String getChannel() {
        return channel;
    }

    @Option(option = "channel", description = "Channel to use when running the performance test. By default, 'commits'.")
    public void setChannel(@Nullable String channel) {
        this.channel = channel;
        systemProperty("org.gradle.performance.execution.channel", channel);
    }

    @OutputDirectory
    public File getDebugArtifactsDirectory() {
        return debugArtifactsDirectory;
    }

    public void setDebugArtifactsDirectory(File debugArtifactsDirectory) {
        this.debugArtifactsDirectory = debugArtifactsDirectory;
    }

    @Option(option = "flamegraphs", description = "If set to 'true', activates flamegraphs and stores them into the 'flames' directory name under the debug artifacts directory.")
    public void setFlamegraphs(String flamegraphs) {
        if ("true".equals(flamegraphs)) {
            File artifactsDirectory = new File(getDebugArtifactsDirectory(), "flames");
            systemProperty("org.gradle.performance.flameGraphTargetDir", artifactsDirectory.getAbsolutePath());
        }
    }

    @Option(option = "sampling-interval", description = "How many ms to wait between two samples when profiling")
    public void setSamplingInterval(String samplingInterval) {
        //no-op, remove once build configurations have been adjusted to no longer call this
    }
}
