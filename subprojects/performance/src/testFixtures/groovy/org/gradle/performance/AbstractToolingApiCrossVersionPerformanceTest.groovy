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

    protected final static ReleasedVersionDistributions RELEASES = new ReleasedVersionDistributions()
    protected final static UnderDevelopmentGradleDistribution CURRENT = new UnderDevelopmentGradleDistribution()

    static def resultStore = ResultsStoreHelper.maybeUseResultStore { new CrossVersionResultsStore() }
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()


    protected ToolingApiExperimentSpec experimentSpec
    // caching class loaders at this level because for performance experiments
    // we don't want caches of the TAPI to be visible between different experiments
    protected final Map<String, ClassLoader> testClassLoaders = [:]

    protected ClassLoader tapiClassLoader

    public <T> Class<T> tapiClass(Class<T> clazz) {
        tapiClassLoader.loadClass(clazz.name)
    }

    void experiment(String projectName, String displayName, @DelegatesTo(ToolingApiExperimentSpec) Closure<?> spec) {
        experimentSpec = new ToolingApiExperimentSpec(displayName, projectName, 3, 10, 5000L, 500L, null)
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

    private class Measurement implements ToolingApiClasspathProvider {

        private CrossVersionPerformanceResults run() {
            def testProjectLocator = new TestProjectLocator()
            def projectDir = testProjectLocator.findProjectDir(experimentSpec.projectName)
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
            def resolver = new ToolingApiDistributionResolver().withDefaultRepository()
            def tplDir = copyTemplateToDir(projectDir)
            try {
                List<String> baselines = CrossVersionPerformanceTestRunner.toBaselineVersions(RELEASES, experimentSpec.targetVersions, ResultsStoreHelper.ADHOC_RUN).toList()
                [*baselines, 'current'].each { String version ->
                    GradleDistribution dist = 'current' == version ? CURRENT : buildContext.distribution(version)
                    println "Testing ${dist.version}..."
                    if ('current' != version) {
                        def baselineVersion = results.baseline(version)
                        baselineVersion.maxExecutionTimeRegression = experimentSpec.maxExecutionTimeRegression
                        baselineVersion.maxMemoryRegression = experimentSpec.maxMemoryRegression
                    }
                    def toolingApiDistribution = resolver.resolve(dist.version.version)
                    def testClassPath = []
                    // add TAPI test fixtures to classpath
                    testClassPath << ClasspathUtil.getClasspathForClass(ToolingApi)
                    tapiClassLoader = getTestClassLoader(testClassLoaders, toolingApiDistribution, testClassPath) {
                    }
                    def tapiClazz = tapiClassLoader.loadClass(ToolingApi.name)
                    def toolingApi = tapiClazz.newInstance(dist, tplDir)
                    assert toolingApi != ToolingApi
                    warmup(toolingApi)
                    println "Waiting ${experimentSpec.sleepAfterWarmUpMillis}ms before measurements"
                    sleep(experimentSpec.sleepAfterWarmUpMillis)
                    measure(results, toolingApi, version)
                }
            } finally {
                resolver.stop()
            }

            results.assertEveryBuildSucceeds()
            resultStore.report(results)

            results.assertCurrentVersionHasNotRegressed()

            results
        }

        private TestDirectoryProvider copyTemplateToDir(File templateDir) {
            def directory = temporaryFolder.getTestDirectory()
            directory.copyFrom(templateDir)
            new TestDirectoryProvider() {
                @Override
                TestFile getTestDirectory() {
                    directory
                }

                @Override
                void suppressCleanup() {

                }
            }
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
