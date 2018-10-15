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

import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.gradlebuild.test.integrationtests.DistributionTest;
import org.gradle.api.specs.Spec;
import org.gradle.api.Task;

import org.gradle.api.tasks.CacheableTask;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Map.Entry;
import javax.inject.Inject;

import org.gradle.process.CommandLineArgumentProvider;

/**
 * A test that checks execution time and memory consumption.
 */
@CacheableTask
public class PerformanceTest extends DistributionTest {
    private String scenarios;
    private String baselines;
    private String warmups;
    private String runs;
    private String checks;
    private String channel;
    private String resultStoreClass = "org.gradle.performance.results.AllResultsStore";
    private boolean flamegraphs;

    private final Map<String, String> databaseParameters = new HashMap<>();

    private File debugArtifactsDirectory = new File(getProject().getBuildDir(), getName());

    public PerformanceTest() {
        getJvmArgumentProviders().add(new PerformanceTestJvmArgumentsProvider());

        Spec<Task> spec = new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task task) {
                List<String> baseLineList = new ArrayList<>();
                if (baselines != null) {
                    for (String baseline : baselines.split(",")) {
                        baseLineList.add(baseline.trim());
                    }
                }

                return !(baseLineList.contains("last") || baseLineList.contains("nightly"));
            }
        };

        getOutputs().cacheIf("baselines don't contain version 'last' or 'nightly'", spec);
        getOutputs().upToDateWhen(spec);
    }

    public void setDebugArtifactsDirectory(File debugArtifactsDirectory) {
        this.debugArtifactsDirectory = debugArtifactsDirectory;
    }

    @OutputDirectory
    public File getDebugArtifactsDirectory() {
        return debugArtifactsDirectory;
    }

    @Option(option = "scenarios", description = "A semicolon-separated list of performance test scenario ids to run.")
    public void setScenarios(String scenarios) {
        this.scenarios = scenarios;
    }

    @Nullable @Optional @Input
    public String getScenarios() {
        return scenarios;
    }

    @Option(option = "baselines", description = "A comma or semicolon separated list of Gradle versions to be used as baselines for comparing.")
    public void setBaselines(@Nullable String baselines) {
        this.baselines = baselines;
    }

    @Nullable @Optional @Input
    public String getBaselines() {
        return baselines;
    }

    @Option(option = "warmups", description = "Number of warmups before measurements")
    public void setWarmups(@Nullable String warmups) {
        this.warmups = warmups;
    }

    @Nullable @Optional @Input
    public String getWarmups() {
        return warmups;
    }

    @Option(option = "runs", description = "Number of iterations of measurements")
    public void setRuns(@Nullable String runs) {
        this.runs = runs;
    }

    @Nullable @Optional @Input
    public String getRuns() {
        return runs;
    }

    @Option(option = "checks", description = "Tells which regressions to check. One of [none, speed, all]")
    public void setChecks(@Nullable String checks) {
        this.checks = checks;
    }

    @Nullable @Optional @Input
    public String getChecks() {
        return checks;
    }

    @Option(option = "channel", description = "Channel to use when running the performance test. By default, 'commits'.")
    public void setChannel(@Nullable String channel) {
        this.channel = channel;
    }

    @Nullable @Optional @Input
    public String getChannel() {
        return channel;
    }

    public void setResultStoreClass(String resultStoreClass) {
        this.resultStoreClass = resultStoreClass;
    }

    @Nullable @Optional @Input
    public String getResultStoreClass() {
        return resultStoreClass;
    }

    @Option(option = "flamegraphs", description = "If set to 'true', activates flamegraphs and stores them into the 'flames' directory name under the debug artifacts directory.")
    public void setFlamegraphs(String flamegraphs) {
        this.flamegraphs = Boolean.parseBoolean(flamegraphs);
    }

    @Input
    public boolean isFlamegraphs() {
        return flamegraphs;
    }

    @Input
    public String getDatabaseUrl() {
        return databaseParameters.get("org.gradle.performance.db.url");
    }

    @Internal
    protected Map<String, String> getDatabaseParameters() {
        return databaseParameters;
    }

    public void addDatabaseParameters(Map<String, String> databaseConnectionParameters) {
        this.databaseParameters.putAll(databaseConnectionParameters);
    }

    private class PerformanceTestJvmArgumentsProvider implements CommandLineArgumentProvider {
        @Override
        public Iterable<String> asArguments() {
            List<String> result = new ArrayList<>();
            addExecutionParameters(result);
            addDatabaseParameters(result);
            addJava9HomeSystemProperty(result);
            return result;
        }

        private void addJava9HomeSystemProperty(List<String> result) {
            Object java9Home = getProject().findProperty("java9Home");
            if (java9Home != null) {
                addSystemPropertyIfExist(result, "java9Home", java9Home.toString());
            } else {
                addSystemPropertyIfExist(result, "java9Home", System.getProperty("java9Home"));
            }
        }

        private void addExecutionParameters(List<String> result) {
            addSystemPropertyIfExist(result, "org.gradle.performance.scenarios", scenarios);
            addSystemPropertyIfExist(result, "org.gradle.performance.baselines", baselines);
            addSystemPropertyIfExist(result, "org.gradle.performance.execution.warmups", warmups);
            addSystemPropertyIfExist(result, "org.gradle.performance.execution.runs", runs);
            addSystemPropertyIfExist(result, "org.gradle.performance.execution.checks", checks);
            addSystemPropertyIfExist(result, "org.gradle.performance.execution.channel", channel);
            addSystemPropertyIfExist(result, "org.gradle.ignoreBuildJavaVersionCheck", true);

            if (flamegraphs) {
                File artifactsDirectory = new File(getDebugArtifactsDirectory(), "flames");
                addSystemPropertyIfExist(result, "org.gradle.performance.flameGraphTargetDir", artifactsDirectory.getAbsolutePath());
            }
        }

        private void addDatabaseParameters(List<String> result) {
            for (Entry<String, String> entry : databaseParameters.entrySet()) {
                addSystemPropertyIfExist(result, entry.getKey(), entry.getValue());
            }
        }

        private void addSystemPropertyIfExist(List<String> result, String propertyName, Object propertyValue) {
            if (propertyValue != null) {
                result.add("-D" + propertyName + "=" + propertyValue.toString());
            }
        }
    }
}
