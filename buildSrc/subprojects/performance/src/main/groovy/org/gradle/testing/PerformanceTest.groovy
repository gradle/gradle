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

import com.google.common.collect.Sets
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import org.apache.commons.io.FileUtils
import org.gradle.api.JavaVersion
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.gradlebuild.test.integrationtests.DistributionTest
import org.gradle.process.CommandLineArgumentProvider
import org.openmbee.junit.JUnitMarshalling
import org.openmbee.junit.model.JUnitFailure
import org.openmbee.junit.model.JUnitTestCase
import org.openmbee.junit.model.JUnitTestSuite

import javax.annotation.Nullable
import java.nio.charset.Charset

/**
 * A test that checks execution time and memory consumption.
 */
@CacheableTask
@CompileStatic
abstract class PerformanceTest extends DistributionTest {
    public static final String TC_URL = "https://builds.gradle.org/viewLog.html?buildId="
    public static final Set<String> NON_CACHEABLE_VERSIONS = Sets.newHashSet("last", "nightly", "flakiness-detection-commit");
    // Baselines configured by command line `--baselines`
    private Property<String> configuredBaselines = getProject().getObjects().property(String.class);
    // Baselines determined by determineBaselines task
    private Property<String> determinedBaselines = getProject().getObjects().property(String.class);

    private final Map<String, String> databaseParameters = new HashMap<>();

    @OutputDirectory
    File debugArtifactsDirectory = new File(getProject().getBuildDir(), getName())

    /************** properties configured by command line arguments ***************/
    @Nullable
    @Optional
    @Input
    String scenarios

    @Nullable
    @Optional
    @Input
    String warmups

    @Nullable
    @Optional
    @Input
    String runs

    @Nullable
    @Optional
    @Input
    String checks

    @Nullable
    @Optional
    @Input
    String channel

    @Input
    boolean flamegraphs

    /************** properties configured by PerformanceTestPlugin ***************/
    @Internal
    String buildId

    @Internal
    String branchName

    @OutputDirectory
    File reportDir

    @LocalState
    File resultsJson

    // Disable report by default, we don't need it in worker build - unless it's AdHoc performance test
    @Internal
    PerformanceReporter performanceReporter = PerformanceReporter.NoOpPerformanceReporter.INSTANCE

    PerformanceTest() {
        getJvmArgumentProviders().add(new PerformanceTestJvmArgumentsProvider());
        getOutputs().cacheIf("baselines don't contain version 'flakiness-detection-commit', 'last' or 'nightly'", { notContainsSpecialVersions() });
        getOutputs().upToDateWhen { notContainsSpecialVersions() }
    }

    private boolean notContainsSpecialVersions() {
        return ![configuredBaselines.getOrElse(""), determinedBaselines.getOrElse("")]
            .collect { it.split(",") }
            .flatten()
            .collect { it.toString().trim() }
            .any { NON_CACHEABLE_VERSIONS.contains(it) }
    }

    @Override
    @TaskAction
    void executeTests() {
        try {
            super.executeTests()
        } finally {
            performanceReporter.report(this)
        }
    }

    @Option(option = "scenarios", description = "A semicolon-separated list of performance test scenario ids to run.")
    void setScenarios(String scenarios) {
        this.scenarios = scenarios
    }

    @Optional
    @Input
    Property<String> getDeterminedBaselines() {
        return determinedBaselines
    }

    @Internal
    @Option(option = "baselines", description = "A comma or semicolon separated list of Gradle versions to be used as baselines for comparing.")
    Property<String> getConfiguredBaselines() {
        return configuredBaselines
    }

    @Option(option = "warmups", description = "Number of warmups before measurements")
    void setWarmups(@Nullable String warmups) {
        this.warmups = warmups
    }

    @Option(option = "runs", description = "Number of iterations of measurements")
    void setRuns(@Nullable String runs) {
        this.runs = runs
    }

    @Option(option = "checks", description = "Tells which regressions to check. One of [none, speed, all]")
    void setChecks(@Nullable String checks) {
        this.checks = checks
    }

    @Option(option = "channel", description = "Channel to use when running the performance test. By default, 'commits'.")
    void setChannel(@Nullable String channel) {
        this.channel = channel
    }

    @Option(option = "flamegraphs", description = "If set to 'true', activates flamegraphs and stores them into the 'flames' directory name under the debug artifacts directory.")
    void setFlamegraphs(String flamegraphs) {
        this.flamegraphs = Boolean.parseBoolean(flamegraphs)
    }

    @Input
    String getDatabaseUrl() {
        return databaseParameters.get("org.gradle.performance.db.url")
    }

    @Internal
    protected Map<String, String> getDatabaseParameters() {
        return databaseParameters
    }

    void addDatabaseParameters(Map<String, String> databaseConnectionParameters) {
        this.databaseParameters.putAll(databaseConnectionParameters)
    }

    protected void generateResultsJson() {
        Collection<File> xmls = reports.junitXml.destination.listFiles().findAll { it.path.endsWith(".xml") }
        List<ScenarioBuildResultData> resultData = xmls
            .collect { JUnitMarshalling.unmarshalTestSuite(new FileInputStream(it)) }
            .collect { extractResultFromTestSuite(it) }
            .flatten() as List<ScenarioBuildResultData>
        FileUtils.write(resultsJson, JsonOutput.toJson(resultData), Charset.defaultCharset())
    }

    static String collectFailures(JUnitTestSuite testSuite) {
        List<JUnitTestCase> testCases = testSuite.testCases ?: []
        List<JUnitFailure> failures = testCases.collect { it.failures ?: [] }.flatten() as List<JUnitFailure>
        return failures.collect { it.value }.join("\n")
    }

    private List<ScenarioBuildResultData> extractResultFromTestSuite(JUnitTestSuite testSuite) {
        List<JUnitTestCase> testCases = testSuite.testCases ?: []
        return testCases.findAll { !it.skipped }.collect {
            new ScenarioBuildResultData(scenarioName: it.name,
                webUrl: TC_URL + buildId,
                status: (it.errors || it.failures) ? "FAILURE" : "SUCCESS",
                testFailure: collectFailures(testSuite))
        }
    }

    private class PerformanceTestJvmArgumentsProvider implements CommandLineArgumentProvider {
        @Override
        Iterable<String> asArguments() {
            List<String> result = []
            addExecutionParameters(result)
            addDatabaseParameters(result)
            addJava9HomeSystemProperty(result)
            return result
        }

        private void addJava9HomeSystemProperty(List<String> result) {
            String java9Home =
                [
                    getProject().findProperty("java9Home"),
                    System.getProperty("java9Home"),
                    System.getenv("java9Home"),
                    JavaVersion.current().isJava9Compatible() ? System.getProperty("java.home") : null
                ].findAll().first()

            addSystemPropertyIfExist(result, "java9Home", java9Home)
        }

        private void addExecutionParameters(List<String> result) {
            addSystemPropertyIfExist(result, "org.gradle.performance.scenarios", scenarios)
            addSystemPropertyIfExist(result, "org.gradle.performance.baselines", determinedBaselines.getOrNull())
            addSystemPropertyIfExist(result, "org.gradle.performance.execution.warmups", warmups)
            addSystemPropertyIfExist(result, "org.gradle.performance.execution.runs", runs)
            addSystemPropertyIfExist(result, "org.gradle.performance.regression.checks", checks)
            addSystemPropertyIfExist(result, "org.gradle.performance.execution.channel", channel)
            addSystemPropertyIfExist(result, "org.gradle.performance.debugArtifactsDirectory", getDebugArtifactsDirectory())

            if (flamegraphs) {
                File artifactsDirectory = new File(getDebugArtifactsDirectory(), "flames")
                addSystemPropertyIfExist(result, "org.gradle.performance.flameGraphTargetDir", artifactsDirectory.getAbsolutePath())
            }
        }

        private void addDatabaseParameters(List<String> result) {
            databaseParameters.each { String key, String value ->
                addSystemPropertyIfExist(result, key, value)
            }
        }

        private void addSystemPropertyIfExist(List<String> result, String propertyName, Object propertyValue) {
            if (propertyValue != null) {
                result.add("-D${propertyName}=${propertyValue}".toString())
            }
        }
    }
}
