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

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
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
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.time.Clock
import org.gradle.internal.time.Time
import org.gradle.performance.categories.PerformanceRegressionTest
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.CrossVersionPerformanceTestRunner
import org.gradle.performance.fixture.InvocationSpec
import org.gradle.performance.fixture.OperationTimer
import org.gradle.performance.fixture.PerformanceTestConditions
import org.gradle.performance.fixture.PerformanceTestDirectoryProvider
import org.gradle.performance.fixture.PerformanceTestGradleDistribution
import org.gradle.performance.fixture.PerformanceTestIdProvider
import org.gradle.performance.fixture.Profiler
import org.gradle.performance.fixture.TestProjectLocator
import org.gradle.performance.fixture.TestScenarioSelector
import org.gradle.performance.results.BuildDisplayInfo
import org.gradle.performance.results.CrossVersionPerformanceResults
import org.gradle.performance.results.CrossVersionResultsStore
import org.gradle.performance.results.MeasuredOperationList
import org.gradle.performance.results.ResultsStoreHelper
import org.gradle.performance.util.Git
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GFileUtils
import org.gradle.util.GradleVersion
import org.junit.Assume
import org.junit.Rule
import org.junit.experimental.categories.Category
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification

import java.lang.reflect.Proxy

import static spock.lang.Retry.Mode.SETUP_FEATURE_CLEANUP

/**
 * Base class for all Tooling API performance regression tests. Subclasses can profile arbitrary actions against a {@link ProjectConnection).
 *
 * TODO collect profiling data with {@link org.gradle.performance.fixture.Profiler}
 */
@Category(PerformanceRegressionTest)
@CleanupTestDirectory
@Retry(condition = { PerformanceTestConditions.whenSlowerButNotAdhoc(failure) }, mode = SETUP_FEATURE_CLEANUP, count = 2)
abstract class AbstractToolingApiCrossVersionPerformanceTest extends Specification {
    protected final static ReleasedVersionDistributions RELEASES = new ReleasedVersionDistributions()
    protected final static GradleDistribution CURRENT = new UnderDevelopmentGradleDistribution()

    static def resultStore = new CrossVersionResultsStore()
    final TestNameTestDirectoryProvider temporaryFolder = new PerformanceTestDirectoryProvider()

    protected ToolingApiExperiment experiment

    protected ClassLoader tapiClassLoader

    @Shared
    private Logger logger

    @Rule
    PerformanceTestIdProvider performanceTestIdProvider = new PerformanceTestIdProvider()

    File repositoryMirrorScript = RepoScriptBlockUtil.createMirrorInitScript()

    Profiler profiler

    public <T> Class<T> tapiClass(Class<T> clazz) {
        tapiClassLoader.loadClass(clazz.name)
    }

    def setupSpec() {
        logger = LoggerFactory.getLogger(getClass())
    }

    void experiment(String projectName, @DelegatesTo(ToolingApiExperiment) Closure<?> spec) {
        experiment = new ToolingApiExperiment(projectName)
        performanceTestIdProvider.testSpec = experiment
        def clone = spec.rehydrate(experiment, this, this)
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone.call(experiment)
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

    public class ToolingApiExperiment {
        final String projectName
        String displayName
        List<String> targetVersions = []
        String minimumVersion
        List<File> extraTestClassPath = []
        Closure<?> action
        Integer invocationCount
        Integer warmUpCount

        ToolingApiExperiment(String projectName) {
            this.projectName = projectName
        }

        void action(@DelegatesTo(ProjectConnection) Closure<?> action) {
            this.action = action
        }
    }

    private static class ToolingApiBuildExperimentSpec extends BuildExperimentSpec {

        ToolingApiBuildExperimentSpec(String version, TestFile workingDir, ToolingApiExperiment experiment) {
            super(version, experiment.projectName, workingDir, experiment.warmUpCount ?: 10, experiment.invocationCount ?: 40, null, null)
        }

        @Override
        BuildDisplayInfo getDisplayInfo() {
            new BuildDisplayInfo(projectName, displayName, [], [], [], [], true)
        }

        @Override
        InvocationSpec getInvocation() {
            throw new UnsupportedOperationException('Invocations are not supported for Tooling API performance tests')
        }
    }

    private class Measurement implements ToolingApiClasspathProvider {
        private final Clock clock = Time.clock()

        private CrossVersionPerformanceResults run() {
            def testId = experiment.displayName
            def scenarioSelector = new TestScenarioSelector()
            Assume.assumeTrue(scenarioSelector.shouldRun(testId, [experiment.projectName].toSet(), resultStore))
            profiler = Profiler.create()
            try {
                doRun(testId)
            } finally {
                CompositeStoppable.stoppable(profiler).stop()
            }
        }

        private CrossVersionPerformanceResults doRun(String testId) {
            def testProjectLocator = new TestProjectLocator()
            def projectDir = testProjectLocator.findProjectDir(experiment.projectName)
            IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
            def results = new CrossVersionPerformanceResults(
                testId: testId,
                previousTestIds: [],
                testProject: experiment.projectName,
                jvm: Jvm.current().toString(),
                operatingSystem: OperatingSystem.current().toString(),
                host: InetAddress.getLocalHost().getHostName(),
                versionUnderTest: GradleVersion.current().getVersion(),
                vcsBranch: Git.current().branchName,
                vcsCommits: [Git.current().commitId],
                startTime: clock.getCurrentTime(),
                tasks: [],
                cleanTasks: [],
                args: [],
                gradleOpts: [],
                daemon: true,
                channel: ResultsStoreHelper.determineChannel(),
                teamCityBuildId: ResultsStoreHelper.determineTeamCityBuildId()
            )
            def resolver = new ToolingApiDistributionResolver().withDefaultRepository()
            try {
                List<String> baselines = CrossVersionPerformanceTestRunner.toBaselineVersions(RELEASES, experiment.targetVersions, experiment.minimumVersion).toList()
                [*baselines, 'current'].each { String version ->
                    def experimentSpec = new ToolingApiBuildExperimentSpec(version, temporaryFolder.testDirectory, experiment)
                    def workingDirProvider = copyTemplateTo(projectDir, experimentSpec.workingDirectory, version)
                    GradleDistribution dist = 'current' == version ? CURRENT : buildContext.distribution(version)
                    println "Testing ${dist.version}..."
                    def toolingApiDistribution = new PerformanceTestToolingApiDistribution(resolver.resolve(dist.version.version), workingDirProvider.testDirectory)
                    List<File> testClassPath = [*experiment.extraTestClassPath]
                    // add TAPI test fixtures to classpath
                    testClassPath << ClasspathUtil.getClasspathForClass(ToolingApi)
                    tapiClassLoader = getTestClassLoader([:], toolingApiDistribution, testClassPath) {
                    }
                    def tapiClazz = tapiClassLoader.loadClass(ToolingApi.name)
                    assert tapiClazz != ToolingApi
                    def toolingApi = tapiClazz.newInstance(new PerformanceTestGradleDistribution(dist, workingDirProvider.testDirectory), workingDirProvider)
                    toolingApi.requireIsolatedDaemons()
                    toolingApi.requireIsolatedUserHome()

                    warmup(toolingApi, experimentSpec)
                    profiler.start(experimentSpec)
                    measure(results, toolingApi, version, experimentSpec)
                    profiler.stop(experimentSpec)
                    toolingApi.daemons.killAll()
                }
            } finally {
                resolver.stop()
            }

            results.endTime = clock.getCurrentTime()

            resultStore.report(results)

            results
        }

        private TestDirectoryProvider copyTemplateTo(File templateDir, File workingDir, String version) {
            TestFile perVersionDir = new TestFile(workingDir, version)
            if (perVersionDir.exists()) {
                GFileUtils.cleanDirectory(perVersionDir)
            } else {
                perVersionDir.mkdirs()
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

                @Override
                void suppressCleanupErrors() {

                }
            }
        }

        private void measure(CrossVersionPerformanceResults results, toolingApi, String version, ToolingApiBuildExperimentSpec experimentSpec) {
            OperationTimer timer = new OperationTimer()
            MeasuredOperationList versionResults = 'current' == version ? results.current : results.version(version).results
            experiment.with {
                def count = iterationCount("runs", invocationCount)
                count.times { n ->
                    println "Run #${n + 1}"
                    versionResults.add(timer.measure {
                        toolingApi.withConnection(wrapAction(action, experimentSpec))
                    })
                }
            }
        }

        private void warmup(toolingApi, ToolingApiBuildExperimentSpec experimentSpec) {
            experiment.with {
                def count = iterationCount("warmups", warmUpCount)
                count.times { n ->
                    println "Warm-up #${n + 1}"
                    toolingApi.withConnection(wrapAction(action, experimentSpec))
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

        public Closure wrapAction(Closure action, ToolingApiBuildExperimentSpec spec) {
            { connection -> asPerformanceTestConnection(connection, spec).with(action) }
        }

        public asPerformanceTestConnection(Object connection, ToolingApiBuildExperimentSpec spec) {
            Proxy.newProxyInstance(tapiClassLoader, [tapiClassLoader.loadClass(ProjectConnection.name)] as Class[]) { proxy, method, args ->
                switch (method.name) {
                    case "model": return withAdditionalArgs(connection.model(args[0]), spec)
                    case "getModel":
                        if (args.length == 1) {
                            return withAdditionalArgs(connection.model(args[0]), spec).get()
                        } else {
                            return withAdditionalArgs(connection.model(args[0]), spec).get(args[2])
                        }
                    case "newBuild": return withAdditionalArgs(connection.newBuild, spec)
                    case "newTestLauncher": return withAdditionalArgs(connection.newBuild, spec)
                    case "action": return withAdditionalArgs(connection.action(args[0]), spec)
                    default: method.invoke(connection, args)
                }
            }
        }

        public withAdditionalArgs(operation, ToolingApiBuildExperimentSpec spec) {
            Proxy.newProxyInstance(tapiClassLoader, operation.class.interfaces) { proxy, method, args ->
                Stoppable stoppable = new CompositeStoppable()
                if (method.name in ["run", "get"]) {
                    def params = operation.operationParamsBuilder
                    def log = new File(spec.workingDirectory, "log.txt")
                    def out = new FileOutputStream(log, true)
                    stoppable.add(out)
                    params.stdout = out
                    params.stderr = out
                    params.arguments = params.arguments = params.arguments ?: []
                    params.arguments += ["--init-script", repositoryMirrorScript.absolutePath]
                    params.arguments += profiler.getAdditionalGradleArgs(spec)

                    params.jvmArguments = params.jvmArguments = params.jvmArguments ?: []
                    params.jvmArguments += profiler.getAdditionalJvmOpts(spec)
                }
                try {
                    def returnValue = method.invoke(operation, args)
                    return returnValue == operation ? proxy : returnValue
                } finally {
                    stoppable.stop()
                }
            }
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
