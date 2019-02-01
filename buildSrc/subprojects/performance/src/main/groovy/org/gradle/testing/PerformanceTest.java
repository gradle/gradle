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

import com.google.common.collect.Sets;
import org.gradle.api.JavaVersion;
import org.gradle.api.Task;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.options.Option;
import org.gradle.gradlebuild.test.integrationtests.DistributionTest;
import org.gradle.process.CommandLineArgumentProvider;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A test that checks execution time and memory consumption.
 */
@CacheableTask
public class PerformanceTest extends DistributionTest {
    public static final Set<String> NON_CACHEABLE_VERSIONS = Sets.newHashSet("last", "nightly", "flakiness-detection-commit");
    // Baselines configured by command line `--baselines`
    private Property<String> configuredBaselines = getProject().getObjects().property(String.class);
    // Baselines determined by determineBaselines task
    private Property<String> determinedBaselines = getProject().getObjects().property(String.class);
    private String scenarios;
    private String warmups;
    private String runs;
    private String checks;
    private String channel;
    private String reportGeneratorClass = "org.gradle.performance.results.DefaultReportGenerator";
    private boolean flamegraphs;

    private final Map<String, String> databaseParameters = new HashMap<>();

    private File debugArtifactsDirectory = new File(getProject().getBuildDir(), getName());

    public PerformanceTest() {
        getJvmArgumentProviders().add(new PerformanceTestJvmArgumentsProvider());
        getOutputs().cacheIf("baselines don't contain version 'flakiness-detection-commit', 'last' or 'nightly'", this::notContainsSpecialVersions);
        getOutputs().upToDateWhen(this::notContainsSpecialVersions);
    }

    private boolean notContainsSpecialVersions(Task task) {
        return Stream.of(configuredBaselines.getOrElse(""), determinedBaselines.getOrElse(""))
            .map(baseline -> baseline.split(","))
            .flatMap(Arrays::stream)
            .map(String::trim)
            .noneMatch(NON_CACHEABLE_VERSIONS::contains);
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

    @Nullable
    @Optional
    @Input
    public String getScenarios() {
        return scenarios;
    }

    @Optional
    @Input
    public Property<String> getDeterminedBaselines() {
        return determinedBaselines;
    }

    @Internal
    public Property<String> getConfiguredBaselines() {
        return configuredBaselines;
    }

    @Option(option = "baselines", description = "A comma or semicolon separated list of Gradle versions to be used as baselines for comparing.")
    public void setBaselines(@Nullable String baselines) {
        this.configuredBaselines.set(baselines);
    }

    @Option(option = "warmups", description = "Number of warmups before measurements")
    public void setWarmups(@Nullable String warmups) {
        this.warmups = warmups;
    }

    @Nullable
    @Optional
    @Input
    public String getWarmups() {
        return warmups;
    }

    @Option(option = "runs", description = "Number of iterations of measurements")
    public void setRuns(@Nullable String runs) {
        this.runs = runs;
    }

    @Nullable
    @Optional
    @Input
    public String getRuns() {
        return runs;
    }

    @Option(option = "checks", description = "Tells which regressions to check. One of [none, speed, all]")
    public void setChecks(@Nullable String checks) {
        this.checks = checks;
    }

    @Nullable
    @Optional
    @Input
    public String getChecks() {
        return checks;
    }

    @Option(option = "channel", description = "Channel to use when running the performance test. By default, 'commits'.")
    public void setChannel(@Nullable String channel) {
        this.channel = channel;
    }

    @Nullable
    @Optional
    @Input
    public String getChannel() {
        return channel;
    }

    public void setReportGeneratorClass(String reportGeneratorClass) {
        this.reportGeneratorClass = reportGeneratorClass;
    }

    @Nullable
    @Optional
    @Input
    public String getReportGeneratorClass() {
        return reportGeneratorClass;
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
            String java9Home = Stream.of(
                getProject().findProperty("java9Home"),
                System.getProperty("java9Home"),
                System.getenv("java9Home"),
                JavaVersion.current().isJava9Compatible() ? System.getProperty("java.home") : null
            ).filter(Objects::nonNull).findFirst().map(Object::toString).orElse(null);

            addSystemPropertyIfExist(result, "java9Home", java9Home);
        }

        private void addExecutionParameters(List<String> result) {
            addSystemPropertyIfExist(result, "org.gradle.performance.scenarios", scenarios);
            addSystemPropertyIfExist(result, "org.gradle.performance.baselines", determinedBaselines.getOrNull());
            addSystemPropertyIfExist(result, "org.gradle.performance.execution.warmups", warmups);
            addSystemPropertyIfExist(result, "org.gradle.performance.execution.runs", runs);
            addSystemPropertyIfExist(result, "org.gradle.performance.regression.checks", checks);
            addSystemPropertyIfExist(result, "org.gradle.performance.execution.channel", channel);

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
