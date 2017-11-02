/*
 * Copyright 2009 the original author or authors.
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

import com.google.common.base.Splitter
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
import org.gradle.performance.results.ResultsStore
import org.gradle.performance.results.ResultsStoreHelper
import org.gradle.performance.util.Git
import org.gradle.util.GFileUtils
import org.gradle.util.GradleVersion
import org.junit.Assume

import java.util.regex.Pattern

class CrossVersionPerformanceTestRunner extends PerformanceTestSpec {
    private static final Pattern COMMA_OR_SEMICOLON = Pattern.compile('[;,]')

    GradleDistribution current
    final IntegrationTestBuildContext buildContext
    final DataReporter<CrossVersionPerformanceResults> reporter
    TestProjectLocator testProjectLocator = new TestProjectLocator()
    final BuildExperimentRunner experimentRunner
    final ReleasedVersionDistributions releases
    final Clock clock = Time.clock()

    String testProject
    File workingDir
    boolean useDaemon = true

    List<String> tasksToRun = []
    List<String> cleanTasks = []
    List<String> args = []
    List<String> gradleOpts = []
    List<String> previousTestIds = []

    List<String> targetVersions = []
    String minimumVersion

    private CompositeBuildExperimentListener buildExperimentListeners = new CompositeBuildExperimentListener()
    private CompositeInvocationCustomizer invocationCustomizers = new CompositeInvocationCustomizer()

    CrossVersionPerformanceTestRunner(BuildExperimentRunner experimentRunner, DataReporter<CrossVersionPerformanceResults> reporter, ReleasedVersionDistributions releases, IntegrationTestBuildContext buildContext) {
        this.reporter = reporter
        this.experimentRunner = experimentRunner
        this.releases = releases
        this.buildContext = buildContext
    }

    CrossVersionPerformanceResults run() {
        if (testId == null) {
            throw new IllegalStateException("Test id has not been specified")
        }
        if (testProject == null) {
            throw new IllegalStateException("Test project has not been specified")
        }
        if (workingDir == null) {
            throw new IllegalStateException("Working directory has not been specified")
        }

        def scenarioSelector = new TestScenarioSelector()
        Assume.assumeTrue(scenarioSelector.shouldRun(testId, [testProject].toSet(), (ResultsStore) reporter))

        def results = new CrossVersionPerformanceResults(
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
            channel: ResultsStoreHelper.determineChannel()
        )

        runVersion(current, perVersionWorkingDirectory('current'), results.current)

        def baselineVersions = toBaselineVersions(releases, targetVersions, minimumVersion)

        baselineVersions.each { it ->
            def baselineVersion = results.baseline(it)
            runVersion(buildContext.distribution(baselineVersion.version), perVersionWorkingDirectory(baselineVersion.version), baselineVersion.results)
        }

        results.endTime = clock.getCurrentTime()

        results.assertEveryBuildSucceeds()

        reporter.report(results)

        return results
    }

    protected File perVersionWorkingDirectory(String version) {
        def perVersion = new File(workingDir, version.replace('+', ''))
        if (!perVersion.exists()) {
            perVersion.mkdirs()
        } else {
            GFileUtils.cleanDirectory(perVersion)
        }
        perVersion
    }

    static Iterable<String> toBaselineVersions(ReleasedVersionDistributions releases, List<String> targetVersions, String minimumVersion) {
        Iterable<String> versions
        boolean addMostRecentRelease = true
        def overrideBaselinesProperty = System.getProperty('org.gradle.performance.baselines')
        if (overrideBaselinesProperty) {
            versions = resolveOverriddenVersions(overrideBaselinesProperty, targetVersions)
            addMostRecentRelease = false
        } else {
            versions = targetVersions
        }

        def baselineVersions = new LinkedHashSet<String>()

        def mostRecentRelease = releases.mostRecentRelease.version.version
        def currentBaseVersion = GradleVersion.current().getBaseVersion().version

        for (String version : versions) {
            if (version == currentBaseVersion) {
                // current version is run by default, skip adding it to baseline
                continue
            }
            if (version == 'last') {
                addMostRecentRelease = false
                baselineVersions.add(mostRecentRelease)
                continue
            }
            if (version == 'nightly') {
                addMostRecentRelease = false
                baselineVersions.add(LatestNightlyBuildDeterminer.latestNightlyVersion)
                continue
            }
            if (version == 'none') {
                return Collections.emptyList()
            }
            if (version == 'defaults') {
                throw new IllegalArgumentException("'defaults' shouldn't be used in target versions.")
            }
            def releasedVersion = findRelease(releases, version)
            def versionObject = GradleVersion.version(version)
            if (minimumVersion != null && versionObject < GradleVersion.version(minimumVersion)) {
                //this version is not supported by this scenario, as it uses features not yet available in this version of Gradle
                continue
            }
            if (releasedVersion) {
                baselineVersions.add(releasedVersion.version.version)
            } else if (versionObject.snapshot || isRcVersion(versionObject)) {
                // for snapshots, we don't have a cheap way to check if it really exists, so we'll just
                // blindly add it to the list and trust the test author
                // Only active rc versions are listed in all-released-versions.properties that ReleasedVersionDistributions uses
                addMostRecentRelease = false
                baselineVersions.add(version)
            } else {
                throw new RuntimeException("Cannot find Gradle release that matches version '$version'")
            }
        }

        if (baselineVersions.empty || addMostRecentRelease) {
            // Always include the most recent final release if we're not testing against a nightly or a snapshot
            baselineVersions.add(mostRecentRelease)
        }

        baselineVersions
    }

    private static boolean isRcVersion(GradleVersion versionObject) {
        // there is no public API for checking for RC version, this is an internal way
        versionObject.stage.stage == 3
    }

    private static Iterable<String> resolveOverriddenVersions(String overrideBaselinesProperty, List<String> targetVersions) {
        def versions = Splitter.on(COMMA_OR_SEMICOLON)
            .omitEmptyStrings()
            .splitToList(overrideBaselinesProperty)
        versions.collectMany([] as Set) { version -> version == 'defaults' ? targetVersions : [version] }
    }

    protected static GradleDistribution findRelease(ReleasedVersionDistributions releases, String requested) {
        GradleDistribution best = null
        for (GradleDistribution release : releases.all) {
            if (release.version.version == requested) {
                return release
            }
            if (!release.version.snapshot && release.version.baseVersion.version == requested && (best == null || best.version < release.version)) {
                best = release
            }
        }

        best
    }

    private void runVersion(GradleDistribution dist, File workingDir, MeasuredOperationList results) {
        def gradleOptsInUse = resolveGradleOpts()
        def builder = GradleBuildExperimentSpec.builder()
            .projectName(testProject)
            .displayName(dist.version.version)
            .warmUpCount(warmUpRuns)
            .invocationCount(runs)
            .listener(buildExperimentListeners)
            .invocationCustomizer(invocationCustomizers)
            .invocation {
                workingDirectory(workingDir)
                distribution(dist)
                tasksToRun(this.tasksToRun as String[])
                cleanTasks(this.cleanTasks as String[])
                args(this.args as String[])
                gradleOpts(gradleOptsInUse as String[])
                useDaemon(this.useDaemon)
            }
        builder.workingDirectory = workingDir
        def spec = builder.build()
        if (experimentRunner.honestProfiler) {
            experimentRunner.honestProfiler.sessionId = "${testId}-${dist.version.version}".replaceAll('[^a-zA-Z0-9.-]', '_').replaceAll('[_]+', '_')
        }
        experimentRunner.run(spec, results)
    }

    def resolveGradleOpts() {
        PerformanceTestJvmOptions.customizeJvmOptions(this.gradleOpts)
    }

    HonestProfilerCollector getHonestProfiler() {
        return experimentRunner.honestProfiler
    }

    void addBuildExperimentListener(BuildExperimentListener buildExperimentListener) {
        buildExperimentListeners.addListener(buildExperimentListener)
    }

    void addInvocationCustomizer(InvocationCustomizer invocationCustomizer) {
        invocationCustomizers.addCustomizer(invocationCustomizer)
    }
}
