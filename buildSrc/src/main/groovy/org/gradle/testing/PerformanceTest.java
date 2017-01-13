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

package org.gradle.testing;

import org.gradle.api.internal.tasks.options.Option;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.Optional;
import java.io.File;

/**
 * A test that checks execution time and memory consumption.
 */
public class PerformanceTest extends DistributionTest {

    private String baselines;
    private String warmups;
    private String runs;
    private String checks;
    private String channel;
    private File debugArtifactsDirectory = new File(getProject().getBuildDir(), getName());

    @Option(option = "scenarios", description = "A semicolon-separated list of performance test scenario ids to run.")
    public void setScenarios(String scenarios) {
        systemProperty("org.gradle.performance.scenarios", scenarios);
    }

    @Option(option = "baselines", description = "A comma or semicolon separated list of Gradle versions to be used as baselines for comparing.")
    public void setBaselines(String baselines) {
        this.baselines = baselines;
        systemProperty("org.gradle.performance.baselines", baselines);
    }

    @Input @Optional
    public String getBaselines() {
        return baselines;
    }

    @Option(option = "warmups", description = "Number of warmups before measurements")
    public void setWarmups(String warmups) {
        this.warmups = warmups;
        systemProperty("org.gradle.performance.execution.warmups", warmups);
    }

    @Input @Optional
    public String getWarmups() {
        return warmups;
    }

    @Option(option = "runs", description = "Number of iterations of measurements")
    public void setRuns(String runs) {
        this.runs = runs;
        systemProperty("org.gradle.performance.execution.runs", runs);
    }


    @Input @Optional
    public String getRuns() {
        return runs;
    }

    @Option(option = "checks", description = "Tells which regressions to check. One of [none, speed, all]")
    public void setChecks(String checks) {
        this.checks = checks;
        systemProperty("org.gradle.performance.execution.checks", checks);
    }

    @Input @Optional
    public String getChecks() {
        return checks;
    }

    @Input @Optional
    public String getChannel() {
        return channel;
    }

    @Option(option = "channel", description = "Channel to use when running the performance test. By default, 'commits'.")
    public void setChannel(String channel) {
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
            systemProperty("org.gradle.performance.honestprofiler", artifactsDirectory.getAbsolutePath());
        }
    }
}
