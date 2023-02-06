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

package org.gradle.performance.fixture

import org.apache.commons.io.FileUtils
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.time.Clock
import org.gradle.internal.time.Time
import org.gradle.performance.results.CrossVersionPerformanceResults
import org.gradle.performance.results.DataReporter
import org.gradle.performance.results.MeasuredOperationList
import org.gradle.performance.results.ResultsStoreHelper
import org.gradle.performance.util.Git
import org.gradle.profiler.AndroidStudioSyncAction
import org.gradle.profiler.BuildAction
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.GradleInvoker
import org.gradle.profiler.GradleInvokerBuildAction
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.ToolingApiGradleClient
import org.gradle.profiler.studio.tools.StudioFinder
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GradleVersion
import org.junit.Assume

import java.util.function.Consumer
import java.util.function.Function

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.gradlePluginRepositoryMirrorUrl
import static org.gradle.performance.fixture.BaselineVersionResolver.toBaselineVersions
import static org.gradle.test.fixtures.server.http.MavenHttpPluginRepository.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY

/**
 * Runs cross version performance tests using Gradle profiler.
 */
class CrossVersionPerformanceTestRunner extends PerformanceTestSpec {

    private final IntegrationTestBuildContext buildContext
    private final DataReporter<CrossVersionPerformanceResults> reporter
    private final ReleasedVersionDistributions releases
    private final Clock clock = Time.clock()

    final BuildExperimentRunner experimentRunner

    GradleDistribution current

    String testProject
    File workingDir
    boolean useDaemon = true
    boolean useToolingApi = false
    boolean useAndroidStudio = false
    List<String> studioJvmArgs = []
    File studioInstallDir

    List<String> tasksToRun = []
    List<String> cleanTasks = []
    List<String> args = []
    List<String> gradleOpts = []
    List<String> previousTestIds = []

    List<String> targetVersions = []
    /**
     * Minimum base version to be used. For example, a 6.0-nightly target version is OK if minimumBaseVersion is 6.0.
     */
    String minimumBaseVersion
    boolean measureGarbageCollection = true
    private final List<Function<InvocationSettings, BuildMutator>> buildMutators = []
    private final List<String> measuredBuildOperations = []
    private BuildAction buildAction

    CrossVersionPerformanceTestRunner(BuildExperimentRunner experimentRunner, DataReporter<CrossVersionPerformanceResults> reporter, ReleasedVersionDistributions releases, IntegrationTestBuildContext buildContext) {
        this.reporter = reporter
        this.experimentRunner = experimentRunner
        this.releases = releases
        this.buildContext = buildContext
        this.testProject = TestScenarioSelector.loadConfiguredTestProject()
    }

    void addBuildMutator(Function<InvocationSettings, BuildMutator> buildMutator) {
        buildMutators.add(buildMutator)
    }

    List<String> getMeasuredBuildOperations() {
        return measuredBuildOperations
    }

    CrossVersionPerformanceResults run() {
        assumeShouldRun()

        def results = new CrossVersionPerformanceResults(
            testClass: testClassName,
            testId: testId,
            previousTestIds: previousTestIds.collect { it.toString() }, // Convert GString instances
            testProject: testProject,
            tasks: tasksToRun.collect { it.toString() },
            cleanTasks: cleanTasks.collect { it.toString() },
            args: args.collect { it.toString() },
            gradleOpts: resolveGradleOpts(),
            daemon: useDaemon,
            jvm: Jvm.current().toString(),
            host: InetAddress.getLocalHost().getHostName(),
            operatingSystem: OperatingSystem.current().toString(),
            versionUnderTest: GradleVersion.current().getVersion(),
            vcsBranch: Git.current().branchName,
            vcsCommits: [Git.current().commitId],
            startTime: clock.getCurrentTime(),
            channel: ResultsStoreHelper.determineChannel(),
            teamCityBuildId: ResultsStoreHelper.determineTeamCityBuildId()
        )

        def baselineVersions = toBaselineVersions(releases, targetVersions, minimumBaseVersion).collect { results.baseline(it) }
        try {
            int runIndex = 0
            runVersion(testId, current, perVersionWorkingDirectory(runIndex++), results.current)

            baselineVersions.each { baselineVersion ->
                runVersion(testId, buildContext.distribution(baselineVersion.version), perVersionWorkingDirectory(runIndex++), baselineVersion.results)
            }
        } catch (Exception e) {
            // Print the exception here, so it is reported even when the reporting fails
            e.printStackTrace()
            throw e
        } finally {
            results.endTime = clock.getCurrentTime()
            reporter.report(results)
        }

        return results
    }

    void assumeShouldRun() {
        if (testId == null) {
            throw new IllegalStateException("Test id has not been specified")
        }
        if (testProject == null) {
            throw new IllegalStateException("Test project has not been specified")
        }
        if (workingDir == null) {
            throw new IllegalStateException("Working directory has not been specified")
        }

        Assume.assumeTrue(TestScenarioSelector.shouldRun(testId))
        TestProjects.validateTestProject(testProject)
    }

    private File perVersionWorkingDirectory(int runIndex) {
        def versionWorkingDirName = String.format('%03d', runIndex)
        def perVersion = new File(workingDir, versionWorkingDirName)
        return cleanOrCreate(perVersion)
    }

    private File perVersionStudioSandboxDirectory(File workingDir) {
        File studioSandboxDir = new File(workingDir, "studio-sandbox")
        return cleanOrCreate(studioSandboxDir)
    }

    private static File cleanOrCreate(File directory) {
        if (!directory.exists()) {
            directory.mkdirs()
        } else {
            FileUtils.cleanDirectory(directory)
        }
        directory
    }

    private static boolean versionMeetsLowerBaseVersionRequirement(String targetVersion, String minimumBaseVersion) {
        return minimumBaseVersion == null || GradleVersion.version(targetVersion).baseVersion >= GradleVersion.version(minimumBaseVersion)
    }

    private void runVersion(String displayName, GradleDistribution dist, File workingDir, MeasuredOperationList results) {
        File studioSandboxDirAsFile = perVersionStudioSandboxDirectory(workingDir)
        def gradleOptsInUse = resolveGradleOpts()
        def builder = GradleBuildExperimentSpec.builder()
            .projectName(testProject)
            .displayName(displayName)
            .warmUpCount(warmUpRuns)
            .invocationCount(runs)
            .buildMutators(buildMutators)
            .crossVersion(true)
            .measuredBuildOperations(measuredBuildOperations)
            .measureGarbageCollection(measureGarbageCollection)
            .invocation {
                workingDirectory(workingDir)
                distribution(new PerformanceTestGradleDistribution(dist, workingDir))
                tasksToRun(this.tasksToRun as String[])
                cleanTasks(this.cleanTasks as String[])
                args((this.args + ['--stacktrace', '-I', RepoScriptBlockUtil.createMirrorInitScript().absolutePath, "-D${PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY}=${gradlePluginRepositoryMirrorUrl()}".toString()]) as String[])
                jvmArgs(gradleOptsInUse as String[])
                useDaemon(this.useDaemon)
                useToolingApi(this.useToolingApi)
                useAndroidStudio(this.useAndroidStudio)
                studioJvmArgs(this.studioJvmArgs)
                studioInstallDir(this.studioInstallDir)
                studioSandboxDir(studioSandboxDirAsFile)
                buildAction(this.buildAction)
            }
        builder.workingDirectory = workingDir
        def spec = builder.build()
        experimentRunner.run(testId, spec, results)
    }

    private List<String> resolveGradleOpts() {
        PerformanceTestJvmOptions.normalizeJvmOptions(this.gradleOpts)
    }

    def <T extends LongRunningOperation> ToolingApiAction<T> toolingApi(String displayName, Function<ProjectConnection, T> initialAction) {
        useToolingApi = true
        def tapiAction = new ToolingApiAction<T>(displayName, initialAction)
        this.buildAction = tapiAction
        return tapiAction
    }

    def setupAndroidStudioSync() {
        useAndroidStudio = true
        buildAction = new AndroidStudioSyncAction()
        studioInstallDir = StudioFinder.findStudioHome()
        studioJvmArgs = System.getProperty("studioJvmArgs") != null
            ? System.getProperty("studioJvmArgs").split(",").collect()
            : []
    }
}

class ToolingApiAction<T extends LongRunningOperation> extends GradleInvokerBuildAction {
    private final Function<ProjectConnection, T> initialAction
    private final String displayName
    private Consumer<T> tapiAction

    ToolingApiAction(String displayName, Function<ProjectConnection, T> initialAction) {
        this.displayName = displayName
        this.initialAction = initialAction
    }

    void run(Consumer<T> tapiAction) {
        this.tapiAction = tapiAction
    }

    @Override
    boolean isDoesSomething() {
        return true
    }

    @Override
    String getDisplayName() {
        return displayName
    }

    @Override
    String getShortDisplayName() {
        return displayName
    }

    @Override
    void run(GradleInvoker buildInvoker, List<String> gradleArgs, List<String> jvmArgs) {
        def toolingApiInvoker = (ToolingApiGradleClient) buildInvoker
        toolingApiInvoker.runOperation(initialAction) { builder ->
            builder.setJvmArguments(jvmArgs)
            builder.withArguments(gradleArgs)
            tapiAction.accept(builder)
        }
    }
}
