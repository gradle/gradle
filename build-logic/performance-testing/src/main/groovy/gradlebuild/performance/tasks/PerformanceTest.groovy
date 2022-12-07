/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.performance.tasks

import com.google.common.collect.Sets
import gradlebuild.integrationtests.tasks.DistributionTest
import gradlebuild.performance.PerformanceTestService
import gradlebuild.performance.ScenarioBuildResultData
import gradlebuild.performance.reporter.PerformanceReporter
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import org.apache.commons.io.FileUtils
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
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
    public static final Set<String> NON_CACHEABLE_VERSIONS = Sets.newHashSet("last", "nightly", "flakiness-detection-commit")
    private final Property<String> baselines = getProject().getObjects().property(String.class)

    private final Map<String, String> databaseParameters = new HashMap<>()

    @Override
    String getPrefix() {
        "performance"
    }

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
    @Internal
    String channel

    /************** properties configured by PerformanceTestPlugin ***************/
    @Internal
    String buildId

    @Internal
    String branchName

    @Internal
    abstract Property<String> getCommitId()

    @Input
    abstract Property<String> getProjectName()

    @OutputDirectory
    File reportDir

    @OutputFile
    File resultsJson

    @Input
    abstract Property<String> getReportGeneratorClass()

    @Internal
    abstract DirectoryProperty getCommitDistributionsDir()

    private final PerformanceReporter performanceReporter = project.objects.newInstance(PerformanceReporter)

    @Internal
    abstract Property<PerformanceTestService> getPerformanceTestService()

    PerformanceTest() {
        getJvmArgumentProviders().add(new PerformanceTestJvmArgumentsProvider())
        getOutputs().doNotCacheIf("baselines contain version 'flakiness-detection-commit', 'last' or 'nightly'", { containsSpecialVersions() })
        getOutputs().doNotCacheIf("flakiness detection", { flakinessDetection })
        getOutputs().upToDateWhen { !containsSpecialVersions() && !flakinessDetection }

        projectName.set(project.name)
    }

    private boolean containsSpecialVersions() {
        return baselines.getOrElse("")
            .split(",")
            .collect { it.toString().trim() }
            .any { NON_CACHEABLE_VERSIONS.contains(it) }
    }

    private boolean isFlakinessDetection() {
        return channel.startsWith("flakiness-detection")
    }

    @Override
    @TaskAction
    void executeTests() {
        performanceTestService.get()
        if (getScenarios() == null) {
            moveMethodFiltersToScenarios()
        }
        try {
            super.executeTests()
        } catch (GradleException e) {
            // Ignore test failure message, so the reporter can report the failures
            if (databaseUrl != null &&
                e.getMessage()?.startsWith("There were failing tests")
            ) {
                logger.warn(e.getMessage())
            } else {
                throw e
            }
        } finally {
            generateResultsJson()
            performanceReporter.report(
                reportGeneratorClass.get(),
                reportDir,
                [resultsJson],
                databaseParameters,
                channel,
                [] as Set,
                branchName,
                commitId.get(),
                classpath,
                projectName.get(),
                buildId,
                false
            )
        }
    }

    /**
     * Remove method filters from the filters passed via the commandline and add the as scenarios.
     *
     * We currently can't filter single unrolled methods due to a limitation with Spock and JUnit 4/5
     * So we handle selecting the right methods by some custom code in our performance test infrastructure (`TestScenarioSelector.shouldRun()`).
     * The classes filters are still handled by Gradle's/JUnit's infrastructure.
     */
    private void moveMethodFiltersToScenarios() {
        DefaultTestFilter filter = getFilter() as DefaultTestFilter
        List<String> scenarios = []
        Set<String> classOnlyFilters = new LinkedHashSet<>()
        filter.getCommandLineIncludePatterns().each { includePattern ->
            def lastDot = includePattern.lastIndexOf(".")
            if (lastDot == -1) {
                classOnlyFilters.add(includePattern)
            } else {
                def className = includePattern.substring(0, lastDot)
                def methodName = includePattern.substring(lastDot + 1)
                if (Character.isLowerCase(methodName.charAt(0))) {
                    scenarios.add(methodName)
                    classOnlyFilters.add(className)
                } else {
                    classOnlyFilters.add(includePattern)
                }
            }
        }
        filter.setCommandLineIncludePatterns(classOnlyFilters)
        setScenarios(scenarios.join(";"))
    }

    @Option(option = "scenarios", description = "A semicolon-separated list of performance test scenario ids to run.")
    void setScenarios(String scenarios) {
        this.scenarios = scenarios
    }

    @Optional
    @Input
    @Option(option = "baselines", description = "A comma or semicolon separated list of Gradle versions to be used as baselines for comparing.")
    Property<String> getBaselines() {
        return baselines
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

    @Option(option = "profiler", description = "Allows configuring a profiler to use. The same options as for Gradle profilers --profiler command line option are available and 'none' to disable profiling")
    @Optional
    @Input
    abstract Property<String> getProfiler()

    @Option(option = "cross-version-only", description = "Only run cross version performance tests")
    @Input
    final Property<Boolean> crossVersionOnly = project.objects.property(Boolean.class).convention(false)

    @Optional
    @Input
    String getDatabaseUrl() {
        return databaseParameters.get("org.gradle.performance.db.url")
    }

    @Internal
    Map<String, String> getDatabaseParameters() {
        return databaseParameters
    }

    @Optional
    @Input
    abstract Property<String> getTestProjectName()

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    abstract ConfigurableFileCollection getTestProjectFiles()

    void addDatabaseParameters(Map<String, String> databaseConnectionParameters) {
        this.databaseParameters.putAll(databaseConnectionParameters)
    }

    void generateResultsJson() {
        Collection<File> xmls = reports.junitXml.outputLocation.get().asFile.listFiles().findAll { it.path.endsWith(".xml") }
        List<ScenarioBuildResultData> resultData = xmls
            .collect { JUnitMarshalling.unmarshalTestSuite(new FileInputStream(it)) }
            .collect { extractResultFromTestSuite(it, getTestProjectName().get()) }
            .flatten() as List<ScenarioBuildResultData>
        FileUtils.write(resultsJson, JsonOutput.toJson(resultData), Charset.defaultCharset())
    }

    static String collectFailures(JUnitTestCase testCase) {
        List<JUnitFailure> failures = testCase.failures ?: []
        return failures.collect { it.value }.join("\n")
    }

    private List<ScenarioBuildResultData> extractResultFromTestSuite(JUnitTestSuite testSuite, String testProject) {
        List<JUnitTestCase> testCases = testSuite.testCases ?: []
        return testCases.findAll { !it.skipped }.collect {
            def agentName = System.getenv("BUILD_AGENT_NAME") ?: null
            new ScenarioBuildResultData(
                scenarioName: it.name,
                scenarioClass: it.className,
                testProject: testProject,
                webUrl: TC_URL + buildId,
                teamCityBuildId: buildId,
                agentName: agentName,
                status: (it.errors || it.failures) ? "FAILURE" : "SUCCESS",
                testFailure: collectFailures(it))
        }
    }

    private class PerformanceTestJvmArgumentsProvider implements CommandLineArgumentProvider {
        @Override
        Iterable<String> asArguments() {
            List<String> result = []
            addExecutionParameters(result)
            addDatabaseParameters(result)
            return result
        }

        private void addExecutionParameters(List<String> result) {
            addSystemPropertyIfExist(result, "integTest.commitDistributionsDir", commitDistributionsDir.get().asFile.absolutePath)
            addSystemPropertyIfExist(result, "org.gradle.performance.scenarios", scenarios)
            addSystemPropertyIfExist(result, "org.gradle.performance.testProject", getTestProjectName().getOrNull())
            addSystemPropertyIfExist(result, "org.gradle.performance.baselines", baselines.getOrNull())
            addSystemPropertyIfExist(result, "org.gradle.performance.crossVersionOnly", crossVersionOnly.get())
            addSystemPropertyIfExist(result, "org.gradle.performance.execution.warmups", warmups)
            addSystemPropertyIfExist(result, "org.gradle.performance.execution.runs", runs)
            addSystemPropertyIfExist(result, "org.gradle.performance.regression.checks", checks)
            addSystemPropertyIfExist(result, "org.gradle.performance.execution.channel", channel)
            addSystemPropertyIfExist(result, "org.gradle.performance.debugArtifactsDirectory", getDebugArtifactsDirectory())
            addSystemPropertyIfExist(result, "gradleBuildBranch", branchName)

            if (profiler.isPresent() && profiler.get() != "none") {
                addSystemPropertyIfExist(result, "org.gradle.performance.profiler", profiler.get())
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
