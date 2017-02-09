/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner

import groovy.transform.Sortable
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AbstractMultiTestRunner
import org.gradle.integtests.fixtures.RetryRuleUtil
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.scopes.DefaultGradleUserHomeScopeServiceRegistry
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.internal.util.RetryRule
import org.gradle.testkit.runner.fixtures.CustomDaemonDirectory
import org.gradle.testkit.runner.fixtures.Debug
import org.gradle.testkit.runner.fixtures.InjectsPluginClasspath
import org.gradle.testkit.runner.fixtures.InspectsBuildOutput
import org.gradle.testkit.runner.fixtures.InspectsExecutedTasks
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.testkit.runner.fixtures.WithNoSourceTaskOutcome
import org.gradle.testkit.runner.internal.GradleProvider
import org.gradle.testkit.runner.internal.feature.TestKitFeature
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.gradle.wrapper.GradleUserHomeLookup
import org.junit.Rule
import org.junit.runner.RunWith

import java.lang.annotation.Annotation

import static org.gradle.testkit.runner.internal.ToolingApiGradleExecutor.TEST_KIT_DAEMON_DIR_NAME

@RunWith(Runner)
abstract class BaseGradleRunnerIntegrationTest extends AbstractIntegrationSpec {

    public static final GradleVersion MIN_TESTED_VERSION = TestKitFeature.RUN_BUILDS.since
    public static final GradleVersion CUSTOM_DAEMON_DIR_SUPPORT_VERSION = GradleVersion.version("2.2")
    public static final GradleVersion NO_SOURCE_TASK_OUTCOME_SUPPORT_VERSION = GradleVersion.version("3.4")

    // Context set by multi run infrastructure
    public static GradleVersion gradleVersion
    public static GradleProvider gradleProvider
    public static boolean debug
    public static boolean crossVersion

    @Rule
    SetSystemProperties setSystemProperties = new SetSystemProperties(
        (NativeServices.NATIVE_DIR_OVERRIDE): buildContext.nativeServicesDir.absolutePath,
        (GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY): buildContext.gradleUserHomeDir.absolutePath
    )

    boolean requireIsolatedTestKitDir

    TestFile getTestKitDir() {
        requireIsolatedTestKitDir ? file("test-kit-workspace") : buildContext.gradleUserHomeDir
    }

    TestFile getProjectDir() {
        temporaryFolder.testDirectory
    }

    String getRootProjectName() {
        testDirectory.name
    }

    GradleRunner runner(List<String> arguments) {
        runner(arguments as String[])
    }

    GradleRunner runner(String... arguments) {
        boolean closeServices = (debug && requireIsolatedTestKitDir) || arguments.contains("-g")
        List<String> allArgs = arguments as List
        if (closeServices) {
            // Do not keep user home dir services open when running embedded or when using a custom user home dir
            allArgs.add(("-D" + DefaultGradleUserHomeScopeServiceRegistry.REUSE_USER_HOME_SERVICES + "=false") as String)
        }
        def gradleRunner = GradleRunner.create()
            .withTestKitDir(testKitDir)
            .withProjectDir(testDirectory)
            .withArguments(allArgs)
            .withDebug(debug)

        gradleProvider.applyTo(gradleRunner)
        gradleRunner
    }

    static String helloWorldTask() {
        """
        task helloWorld {
            doLast {
                println 'Hello world!'
            }
        }
        """
    }

    String getReleasedGradleVersion() {
        return gradleVersion.baseVersion.version
    }

    DaemonsFixture testKitDaemons() {
        testKitDaemons(gradleVersion)
    }

    DaemonsFixture getDaemonsFixture() {
        testKitDaemons()
    }

    DaemonsFixture testKitDaemons(GradleVersion gradleVersion) {
        def daemonDirName = gradleVersion < CUSTOM_DAEMON_DIR_SUPPORT_VERSION ? "daemon" : TEST_KIT_DAEMON_DIR_NAME
        daemons(testKitDir.file(daemonDirName), gradleVersion)
    }

    DaemonsFixture daemons(File daemonDir, GradleVersion version) {
        DaemonLogsAnalyzer.newAnalyzer(daemonDir, version.version)
    }

    def cleanup() {
        if (requireIsolatedTestKitDir) {
            testKitDaemons().killAll()
        }
    }

    ExecutionResult execResult(BuildResult buildResult) {
        new OutputScrapingExecutionResult(buildResult.output, buildResult.output)
    }

    ExecutionFailure execFailure(BuildResult buildResult) {
        new OutputScrapingExecutionFailure(buildResult.output, buildResult.output)
    }

    @Rule
    RetryRule retryRule = RetryRuleUtil.retryCrossVersionTestOnIssueWithReleasedGradleVersion(this)

    static class Runner extends AbstractMultiTestRunner {

        private static final Map<Class<? extends Annotation>, TestKitFeature> TESTKIT_FEATURES = [
            (InspectsExecutedTasks): TestKitFeature.CAPTURE_BUILD_RESULT_TASKS,
            (InspectsBuildOutput): TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG,
            (InjectsPluginClasspath): TestKitFeature.PLUGIN_CLASSPATH_INJECTION
        ]

        private static final IntegrationTestBuildContext BUILD_CONTEXT = new IntegrationTestBuildContext()
        private static final String COMPATIBILITY_SYSPROP_NAME = 'org.gradle.integtest.testkit.compatibility'
        private static final ReleasedVersionDistributions RELEASED_VERSION_DISTRIBUTIONS = new ReleasedVersionDistributions()

        Runner(Class<?> target) {
            super(target)
        }

        @Override
        protected void createExecutions() {
            determineTestedGradleDistributions().each { testedGradleDistribution ->
                def releasedDist = RELEASED_VERSION_DISTRIBUTIONS.getDistribution(testedGradleDistribution.gradleVersion)
                addExecutions(releasedDist, testedGradleDistribution)
            }
        }

        private Set<TestedGradleDistribution> determineTestedGradleDistributions() {
            if (target.getAnnotation(NonCrossVersion)) {
                return [TestedGradleDistribution.UNDER_DEVELOPMENT] as Set
            }

            String version = System.getProperty(COMPATIBILITY_SYSPROP_NAME, 'current')
            switch (version) {
                case 'all':
                    crossVersion = true
                    return [
                            TestedGradleDistribution.forVersion(getMinCompatibleVersion()),
                            TestedGradleDistribution.mostRecentFinalRelease(),
                            TestedGradleDistribution.UNDER_DEVELOPMENT
                    ] as SortedSet
                case 'current': return [
                    TestedGradleDistribution.UNDER_DEVELOPMENT
                ] as Set
                default:
                    throw new IllegalArgumentException("Invalid value for $COMPATIBILITY_SYSPROP_NAME system property: $version (valid values: 'all', 'current')")
            }
        }

        private void addExecutions(releasedDist, TestedGradleDistribution testedGradleDistribution) {
            if (releasedDist && !releasedDist.worksWith(Jvm.current())) {
                add(new IgnoredGradleRunnerExecution(testedGradleDistribution, 'does not work with current JVM'))
            } else if (releasedDist && !releasedDist.isToolingApiTargetJvmSupported(Jvm.current().javaVersion)) {
                add(new IgnoredGradleRunnerExecution(testedGradleDistribution, 'does not work with current JVM due to an incompatibility with the tooling API'))
            } else if (releasedDist && !releasedDist.worksWith(OperatingSystem.current())) {
                add(new IgnoredGradleRunnerExecution(testedGradleDistribution, 'does not work with current OS'))
            } else {
                if (target.getAnnotation(NoDebug)) {
                    add(new GradleRunnerExecution(testedGradleDistribution, false))
                } else if (target.getAnnotation(Debug)) {
                    add(new GradleRunnerExecution(testedGradleDistribution, true))
                } else {
                    [true, false].each { add(new GradleRunnerExecution(testedGradleDistribution, it)) }
                }
            }
        }

        private GradleVersion getMinCompatibleVersion() {
            List<GradleVersion> testedFeatures = TESTKIT_FEATURES.keySet().findAll {
                target.getAnnotation(it)
            }.collect {
                TESTKIT_FEATURES[it].since
            }

            !testedFeatures.empty ? testedFeatures.min() : MIN_TESTED_VERSION
        }

        @Sortable(includes = ['gradleVersion'])
        private static class TestedGradleDistribution {

            private static
            final TestedGradleDistribution UNDER_DEVELOPMENT = new TestedGradleDistribution(BUILD_CONTEXT.version, GradleProvider.installation(BUILD_CONTEXT.gradleHomeDir)) {
                @Override
                String getDisplayName() {
                    return "current"
                }
            }

            final GradleVersion gradleVersion
            final GradleProvider gradleProvider

            String getDisplayName() {
                return gradleVersion.version
            }

            TestedGradleDistribution(GradleVersion gradleVersion, GradleProvider gradleProvider) {
                this.gradleVersion = gradleVersion
                this.gradleProvider = gradleProvider
            }

            static TestedGradleDistribution forVersion(GradleVersion gradleVersion) {
                new TestedGradleDistribution(gradleVersion, GradleProvider.version(gradleVersion.version))
            }

            static TestedGradleDistribution mostRecentFinalRelease() {
                new TestedGradleDistribution(RELEASED_VERSION_DISTRIBUTIONS.mostRecentFinalRelease.version,
                    GradleProvider.version(RELEASED_VERSION_DISTRIBUTIONS.mostRecentFinalRelease.version.version))
            }

        }

        private static class IgnoredGradleRunnerExecution extends AbstractMultiTestRunner.Execution {

            private final TestedGradleDistribution testedGradleDistribution
            private final String reason

            IgnoredGradleRunnerExecution(TestedGradleDistribution testedGradleDistribution, String reason) {
                this.testedGradleDistribution = testedGradleDistribution
                this.reason = reason
            }

            @Override
            protected String getDisplayName() {
                "$testedGradleDistribution.gradleVersion.version $reason"
            }

            @Override
            protected boolean isTestEnabled(AbstractMultiTestRunner.TestDetails testDetails) {
                false
            }
        }

        private static class GradleRunnerExecution extends AbstractMultiTestRunner.Execution {

            protected final boolean debug
            private final TestedGradleDistribution testedGradleDistribution

            GradleRunnerExecution(TestedGradleDistribution testedGradleDistribution, boolean debug) {
                this.debug = debug
                this.testedGradleDistribution = testedGradleDistribution
            }

            @Override
            protected String getDisplayName() {
                "version = $testedGradleDistribution.displayName, debug = $debug"
            }

            @Override
            protected void before() {
                super.before()
                BaseGradleRunnerIntegrationTest.debug = debug
                BaseGradleRunnerIntegrationTest.gradleVersion = testedGradleDistribution.gradleVersion
                BaseGradleRunnerIntegrationTest.gradleProvider = testedGradleDistribution.gradleProvider
            }

            @Override
            protected boolean isTestEnabled(AbstractMultiTestRunner.TestDetails testDetails) {
                def gradleVersion = testedGradleDistribution.gradleVersion

                if (testDetails.getAnnotation(InjectsPluginClasspath)) {
                    if (gradleVersion < TESTKIT_FEATURES[InjectsPluginClasspath].since) {
                        return false
                    }
                }

                if (testDetails.getAnnotation(InspectsBuildOutput)) {
                    if (debug && gradleVersion < TESTKIT_FEATURES[InspectsBuildOutput].since) {
                        return false
                    }
                }

                if (testDetails.getAnnotation(InspectsExecutedTasks)) {
                    if (gradleVersion < TESTKIT_FEATURES[InspectsExecutedTasks].since) {
                        return false
                    }
                }

                if (testDetails.getAnnotation(NoDebug) && debug) {
                    return false
                }

                if (testDetails.getAnnotation(Debug) && !debug) {
                    return false
                }

                if (testDetails.getAnnotation(CustomDaemonDirectory)) {
                    if (gradleVersion < BaseGradleRunnerIntegrationTest.CUSTOM_DAEMON_DIR_SUPPORT_VERSION) {
                        return false
                    }
                }
                if (testDetails.getAnnotation(WithNoSourceTaskOutcome)) {
                    if (gradleVersion < BaseGradleRunnerIntegrationTest.NO_SOURCE_TASK_OUTCOME_SUPPORT_VERSION) {
                        return false
                    }
                }

                true
            }

        }
    }
}
