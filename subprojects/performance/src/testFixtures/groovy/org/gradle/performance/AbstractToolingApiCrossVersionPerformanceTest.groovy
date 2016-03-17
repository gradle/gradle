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
import groovy.transform.TupleConstructor
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.integtests.tooling.fixture.ToolingApiClasspathProvider
import org.gradle.integtests.tooling.fixture.ToolingApiDistributionResolver
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.fixture.*
import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.Duration
import org.gradle.performance.results.CrossVersionResultsStore
import org.gradle.performance.results.ResultsStoreHelper
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GradleVersion
import spock.lang.Specification

abstract class AbstractToolingApiCrossVersionPerformanceTest extends Specification {
    private static final Map<String, ClassLoader> TEST_CLASS_LOADERS = [:]

    static def resultStore = ResultsStoreHelper.maybeUseResultStore { new CrossVersionResultsStore() }
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    ToolingApiExperimentSpec experimentSpec

    void rootDir(Closure spec) { new FileTreeBuilder(projectDir).call(spec) }

    TestFile getProjectDir() {
        temporaryFolder.testDirectory
    }

    void experiment(String projectName, String displayName, @DelegatesTo(ToolingApiExperimentSpec) Closure<?> spec) {
        experimentSpec = new ToolingApiExperimentSpec(displayName, projectName, 3, 10, 5000L, 500L, null)
        def clone = spec.rehydrate(experimentSpec, this, this)
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone.call(experimentSpec)
    }

    CrossVersionPerformanceResults performMeasurements() {
        new Measurement(experimentSpec).run(temporaryFolder)
    }

    static {
        // TODO - find a better way to cleanup
        System.addShutdownHook {
            ((Closeable) resultStore).close()
        }
    }

    @InheritConstructors
    private static class ToolingApiExperimentSpec extends BuildExperimentSpec {

        Amount<Duration> maxExecutionTimeRegression = Duration.millis(0)
        Amount<DataAmount> maxMemoryRegression = DataAmount.mbytes(0)

        List<String> targetVersions = []

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

    @TupleConstructor
    private static class Measurement implements ToolingApiClasspathProvider {
        private final static ReleasedVersionDistributions releases = new ReleasedVersionDistributions()
        private final static UnderDevelopmentGradleDistribution current = new UnderDevelopmentGradleDistribution()
        private final static ToolingApiDistributionResolver resolver = new ToolingApiDistributionResolver().withDefaultRepository()

        final ToolingApiExperimentSpec experimentSpec

        private CrossVersionPerformanceResults run(TestDirectoryProvider temporaryFolder) {
            IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
            def results = new CrossVersionPerformanceResults(
                testId: experimentSpec.displayName,
                previousTestIds: [],
                testProject: experimentSpec.projectName,
                jvm: Jvm.current().toString(),
                operatingSystem: OperatingSystem.current().toString(),
                versionUnderTest: GradleVersion.current().getVersion(),
                vcsBranch: Git.current().branchName,
                vcsCommits: [Git.current().commitId],
                testTime: System.currentTimeMillis(),
                tasks: [],
                args: [],
                gradleOpts: [],
                daemon: true)
            experimentSpec.with {
                List<String> baselines = CrossVersionPerformanceTestRunner.toBaselineVersions(releases, targetVersions, ResultsStoreHelper.ADHOC_RUN).toList()
                [*baselines, 'current'].each { String version ->
                    GradleDistribution dist = 'current' == version ? current : buildContext.distribution(version)
                    println "Testing ${dist.version}..."
                    if ('current' != version) {
                        def baselineVersion = results.baseline(version)
                        baselineVersion.maxExecutionTimeRegression = maxExecutionTimeRegression
                        baselineVersion.maxMemoryRegression = maxMemoryRegression
                    }
                    def toolingApiDistribution = resolver.resolve(dist.version.version)
                    def testClassPath = []
                    // add TAPI test fixtures to classpath
                    testClassPath << ClasspathUtil.getClasspathForClass(ToolingApi)
                    def testClassLoader = getTestClassLoader(TEST_CLASS_LOADERS, toolingApiDistribution, testClassPath) {}
                    def tapiClazz = testClassLoader.loadClass('org.gradle.integtests.tooling.fixture.ToolingApi')
                    def toolingApi = tapiClazz.newInstance(dist, temporaryFolder)
                    warmup(toolingApi)
                    println "Waiting ${sleepAfterWarmUpMillis}ms before measurements"
                    sleep(sleepAfterWarmUpMillis)
                    measure(results, toolingApi, version)
                }
            }

            results.assertEveryBuildSucceeds()
            resultStore.report(results)

            results.assertCurrentVersionHasNotRegressed()

            results
        }

        private void measure(CrossVersionPerformanceResults results, toolingApi, String version) {
            OperationTimer timer = new OperationTimer()
            MeasuredOperationList versionResults = 'current' == version ? results.current : results.version(version).results
            experimentSpec.with {
                invocationCount.times { n ->
                    println "Run #${n + 1}"
                    def measuredOperation = timer.measure {
                        toolingApi.withConnection(action)
                    }
                    measuredOperation.configurationTime = Duration.millis(0)
                    measuredOperation.executionTime = Duration.millis(0)
                    // TODO: cc find a way to collect memory stats
                    measuredOperation.maxCommittedHeap = DataAmount.mbytes(0)
                    measuredOperation.maxHeapUsage = DataAmount.mbytes(0)
                    measuredOperation.maxUncollectedHeap = DataAmount.mbytes(0)
                    measuredOperation.totalHeapUsage = DataAmount.mbytes(0)
                    measuredOperation.totalMemoryUsed = DataAmount.mbytes(0)
                    versionResults.add(measuredOperation)
                    sleep(sleepAfterTestRoundMillis)
                }
            }
        }

        private void warmup(toolingApi) {
            experimentSpec.with {
                warmUpCount.times { n ->
                    println "Warm-up #${n + 1}"
                    toolingApi.withConnection(action)
                    sleep(sleepAfterTestRoundMillis)
                }
            }
        }
    }
}
