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

package org.gradle.performance

import groovy.transform.InheritConstructors
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.integtests.tooling.fixture.ExternalToolingApiDistribution
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.integtests.tooling.fixture.ToolingApiClasspathProvider
import org.gradle.integtests.tooling.fixture.ToolingApiDistribution
import org.gradle.integtests.tooling.fixture.ToolingApiDistributionResolver
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.time.TimeProvider
import org.gradle.internal.time.TrueTimeProvider
import org.gradle.performance.categories.PerformanceRegressionTest
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentRunner
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.CrossVersionPerformanceTestRunner
import org.gradle.performance.fixture.DefaultBuildExperimentInvocationInfo
import org.gradle.performance.fixture.Git
import org.gradle.performance.fixture.InvocationSpec
import org.gradle.performance.fixture.OperationTimer
import org.gradle.performance.fixture.PerformanceTestDirectoryProvider
import org.gradle.performance.fixture.PerformanceTestGradleDistribution
import org.gradle.performance.fixture.PerformanceTestJvmOptions
import org.gradle.performance.fixture.TestProjectLocator
import org.gradle.performance.fixture.TestScenarioSelector
import org.gradle.performance.results.BuildDisplayInfo
import org.gradle.performance.results.CrossVersionPerformanceResults
import org.gradle.performance.results.CrossVersionResultsStore
import org.gradle.performance.results.MeasuredOperationList
import org.gradle.performance.results.ResultsStoreHelper
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GFileUtils
import org.gradle.util.GradleVersion
import org.junit.Assume
import org.junit.experimental.categories.Category
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

@Category(PerformanceRegressionTest)
abstract class AbstractToolingApiCrossVersionPerformanceTest extends Specification {
    protected final static ReleasedVersionDistributions RELEASES = new ReleasedVersionDistributions()
    protected final static GradleDistribution CURRENT = new UnderDevelopmentGradleDistribution()

    static def resultStore = new CrossVersionResultsStore()
    final TestNameTestDirectoryProvider temporaryFolder = new PerformanceTestDirectoryProvider()


    protected ToolingApiExperimentSpec experimentSpec

    protected ClassLoader tapiClassLoader

    @Shared
    private Logger logger

    public <T> Class<T> tapiClass(Class<T> clazz) {
        tapiClassLoader.loadClass(clazz.name)
    }

    def setupSpec() {
        logger = LoggerFactory.getLogger(getClass())
    }

    void experiment(String projectName, String displayName, @DelegatesTo(ToolingApiExperimentSpec) Closure<?> spec) {
        experimentSpec = new ToolingApiExperimentSpec(displayName, projectName, temporaryFolder.testDirectory, 20, 30, null, null)
        def clone = spec.rehydrate(experimentSpec, this, this)
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone.call(experimentSpec)
    }

    CrossVersionPerformanceResults performMeasurements() {
        new Measurement().run()
    }

    static {
        // TODO - find a better way to cleanup
        System.addShutdownHook {
            ((Closeable) resultStore).close()
        }
    }

    protected List<String> customizeJvmOptions(List<String> jvmOptionns) {
        PerformanceTestJvmOptions.customizeJvmOptions(jvmOptionns)
    }

    @InheritConstructors
    public static class ToolingApiExperimentSpec extends BuildExperimentSpec {
        List<String> targetVersions = []
        List<File> extraTestClassPath = []

        Closure<?> action

        void action(@DelegatesTo(ProjectConnection) Closure<?> action) {
            this.action = action
        }

        @Override
        BuildDisplayInfo getDisplayInfo() {
            new BuildDisplayInfo(projectName, displayName, [], [], [], true)
        }

        @Override
        InvocationSpec getInvocation() {
            throw new UnsupportedOperationException('Invocations are not supported for Tooling API performance tests')
        }
    }

    private class Measurement implements ToolingApiClasspathProvider {
        private final TimeProvider timeProvider = new TrueTimeProvider();

        private CrossVersionPerformanceResults run() {
            def testId = experimentSpec.displayName
            def scenarioSelector = new TestScenarioSelector()
            Assume.assumeTrue(scenarioSelector.shouldRun(testId, [experimentSpec.projectName].toSet(), resultStore))

            def testProjectLocator = new TestProjectLocator()
            def projectDir = testProjectLocator.findProjectDir(experimentSpec.projectName)
            IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
            def results = new CrossVersionPerformanceResults(
                testId: testId,
                previousTestIds: [],
                testProject: experimentSpec.projectName,
                jvm: Jvm.current().toString(),
                operatingSystem: OperatingSystem.current().toString(),
                versionUnderTest: GradleVersion.current().getVersion(),
                vcsBranch: Git.current().branchName,
                vcsCommits: [Git.current().commitId],
                startTime: timeProvider.getCurrentTime(),
                tasks: [],
                args: [],
                gradleOpts: [],
                daemon: true,
                channel: ResultsStoreHelper.determineChannel())
            def resolver = new ToolingApiDistributionResolver().withDefaultRepository()
            try {
                List<String> baselines = CrossVersionPerformanceTestRunner.toBaselineVersions(RELEASES, experimentSpec.targetVersions).toList()
                [*baselines, 'current'].each { String version ->
                    def workingDirProvider = copyTemplateTo(projectDir, experimentSpec.workingDirectory, version)
                    GradleDistribution dist = 'current' == version ? CURRENT : buildContext.distribution(version)
                    println "Testing ${dist.version}..."
                    def toolingApiDistribution = new PerformanceTestToolingApiDistribution(resolver.resolve(dist.version.version), workingDirProvider.testDirectory)
                    List<File> testClassPath = [*experimentSpec.extraTestClassPath]
                    // add TAPI test fixtures to classpath
                    testClassPath << ClasspathUtil.getClasspathForClass(ToolingApi)
                    tapiClassLoader = getTestClassLoader([:], toolingApiDistribution, testClassPath) {
                    }
                    def tapiClazz = tapiClassLoader.loadClass(ToolingApi.name)
                    assert tapiClazz != ToolingApi
                    def toolingApi = tapiClazz.newInstance(new PerformanceTestGradleDistribution(dist, workingDirProvider.testDirectory), workingDirProvider)
                    toolingApi.requireIsolatedDaemons()
                    toolingApi.requireIsolatedUserHome()
                    warmup(toolingApi, workingDirProvider.testDirectory)
                    measure(results, toolingApi, version, workingDirProvider.testDirectory)
                    toolingApi.daemons.killAll()
                }
            } finally {
                resolver.stop()
            }

            results.endTime = timeProvider.getCurrentTime()

            results.assertEveryBuildSucceeds()
            resultStore.report(results)

            results
        }

        private TestDirectoryProvider copyTemplateTo(File templateDir, File workingDir, String version) {
            TestFile perVersionDir = new TestFile(workingDir, version)
            if (!perVersionDir.exists()) {
                perVersionDir.mkdirs()
            } else {
                throw new IllegalArgumentException("Didn't expect to find an existing directory at $perVersionDir")
            }

            GFileUtils.copyDirectory(templateDir, perVersionDir)
            return new TestDirectoryProvider() {
                @Override
                TestFile getTestDirectory() {
                    perVersionDir
                }

                @Override
                void suppressCleanup() {

                }
            }
        }

        private void measure(CrossVersionPerformanceResults results, toolingApi, String version, File workingDir) {
            OperationTimer timer = new OperationTimer()
            MeasuredOperationList versionResults = 'current' == version ? results.current : results.version(version).results
            experimentSpec.with {
                def count = iterationCount("runs", invocationCount)
                count.times { n ->
                    BuildExperimentInvocationInfo info = new DefaultBuildExperimentInvocationInfo(experimentSpec, workingDir , BuildExperimentRunner.Phase.MEASUREMENT, n+1, count)
                    if (experimentSpec.listener) {
                        experimentSpec.listener.beforeInvocation(info)
                    }
                    println "Run #${n + 1}"
                    def measuredOperation = timer.measure {
                        toolingApi.withConnection(action)
                    }

                    boolean omit = false
                    BuildExperimentListener.MeasurementCallback cb = new BuildExperimentListener.MeasurementCallback() {
                        @Override
                        void omitMeasurement() {
                            omit = true
                        }
                    }
                    if (experimentSpec.listener) {
                        experimentSpec.listener.afterInvocation(info, measuredOperation, cb)
                    }
                    if (!omit) {
                        if (measuredOperation.getException() == null) {
                            if (measuredOperation.isValid()) {
                                versionResults.add(measuredOperation)
                            } else {
                                logger.error("Discarding invalid operation record {}", measuredOperation)
                            }
                        } else {
                            logger.error("Discarding invalid operation record " + measuredOperation, measuredOperation.getException())
                        }
                    }
                }
            }
        }

        private void warmup(toolingApi, File workingDir) {
            experimentSpec.with {
                def count = iterationCount("warmups", warmUpCount)
                count.times { n ->
                    BuildExperimentInvocationInfo info = new DefaultBuildExperimentInvocationInfo(experimentSpec, workingDir , BuildExperimentRunner.Phase.WARMUP, n+1, count)
                    if (experimentSpec.listener) {
                        experimentSpec.listener.beforeInvocation(info)
                    }
                    println "Warm-up #${n + 1}"
                    toolingApi.withConnection(action)
                    if (experimentSpec.listener) {
                        experimentSpec.listener.afterInvocation(info, null, null)
                    }
                }
            }
        }

        private static int iterationCount(String key, int defaultValue) {
            String value = System.getProperty("org.gradle.performance.execution.$key")
            if (value != null && !"defaults".equals(value)) {
                return Integer.valueOf(value)
            }
            return defaultValue
        }
    }

    private static class PerformanceTestToolingApiDistribution extends ExternalToolingApiDistribution {

        PerformanceTestToolingApiDistribution(ToolingApiDistribution delegate, File testDir) {
            super(delegate.version.version, copyClasspath(delegate, testDir))
        }

        private static List<File> copyClasspath(ToolingApiDistribution delegate, File testDir) {
            File tapiDir = new File(testDir, "tooling-api")
            delegate.classpath.each {
                GFileUtils.copyFile(it, new File(tapiDir, it.name))
            }
            tapiDir.listFiles()
        }
    }
}
