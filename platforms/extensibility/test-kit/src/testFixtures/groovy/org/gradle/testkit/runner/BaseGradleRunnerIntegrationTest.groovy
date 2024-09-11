/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.compatibility.MultiVersionTestCategory
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.fixtures.extensions.AbstractMultiTestInterceptor
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.logging.LoggingConfigurationBuildOptions
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.scopes.DefaultGradleUserHomeScopeServiceRegistry
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.fixtures.CustomDaemonDirectory
import org.gradle.testkit.runner.fixtures.CustomEnvironmentVariables
import org.gradle.testkit.runner.fixtures.Debug
import org.gradle.testkit.runner.fixtures.HideEnvVariableValuesInDaemonLog
import org.gradle.testkit.runner.fixtures.InjectsPluginClasspath
import org.gradle.testkit.runner.fixtures.InspectsBuildOutput
import org.gradle.testkit.runner.fixtures.InspectsExecutedTasks
import org.gradle.testkit.runner.fixtures.InspectsGroupedOutput
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.testkit.runner.fixtures.WithNoSourceTaskOutcome
import org.gradle.testkit.runner.internal.GradleProvider
import org.gradle.testkit.runner.internal.feature.TestKitFeature
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.gradle.wrapper.GradleUserHomeLookup
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.spockframework.runtime.extension.IMethodInvocation
import spock.lang.Retry

import javax.annotation.Nullable
import java.lang.annotation.Annotation

import static org.gradle.integtests.fixtures.RetryConditions.onIssueWithReleasedGradleVersion
import static org.gradle.testkit.runner.internal.ToolingApiGradleExecutor.TEST_KIT_DAEMON_DIR_NAME
import static spock.lang.Retry.Mode.SETUP_FEATURE_CLEANUP

@MultiVersionTestCategory
@GradleRunnerTest
@Retry(condition = { onIssueWithReleasedGradleVersion(instance, failure) }, mode = SETUP_FEATURE_CLEANUP, count = 2)
abstract class BaseGradleRunnerIntegrationTest extends AbstractIntegrationSpec {

    public static final GradleVersion MIN_TESTED_VERSION = TestKitFeature.RUN_BUILDS.since
    public static final GradleVersion CUSTOM_DAEMON_DIR_SUPPORT_VERSION = GradleVersion.version("2.2")
    public static final GradleVersion NO_SOURCE_TASK_OUTCOME_SUPPORT_VERSION = GradleVersion.version("3.4")
    public static final GradleVersion ENVIRONMENT_VARIABLES_SUPPORT_VERSION = GradleVersion.version("3.5")
    public static final GradleVersion INSPECTS_GROUPED_OUTPUT_SUPPORT_VERSION = GradleVersion.version("5.0")
    public static final GradleVersion HIDE_ENV_VARIABLE_VALUE_IN_DAEMON_LOG_VERSION = GradleVersion.version("6.7")

    // Context set by multi run infrastructure
    public static GradleVersion gradleVersion
    public static GradleProvider gradleProvider
    public static boolean debug
    public static boolean crossVersion

    @Rule
    SetSystemProperties setSystemProperties = new SetSystemProperties(
        (NativeServices.NATIVE_DIR_OVERRIDE): buildContext.nativeServicesDir.absolutePath,
        (GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY): buildContext.gradleUserHomeDir.absolutePath,
        (LoggingConfigurationBuildOptions.WarningsOption.GRADLE_PROPERTY): WarningMode.All.name()
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
        def changesUserHome = arguments.contains("-g")
        if (changesUserHome && !debug) {
            // A separate daemon be started operating on the changed user home - lets isolate it so that we kill it in the end
            requireIsolatedTestKitDir = true
        }
        boolean closeServices = (debug && requireIsolatedTestKitDir) || changesUserHome
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

    static boolean isCompatibleVersion(String minCompatibleVersion) {
        gradleVersion.baseVersion >= GradleVersion.version(minCompatibleVersion)
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

    def setup() {
        settingsFile.createFile()
    }

    def cleanup() {
        if (requireIsolatedTestKitDir) {
            testKitDaemons().killAll()
        }
    }

    ExecutionResult execResult(BuildResult buildResult) {
        OutputScrapingExecutionResult.from(buildResult.output, buildResult.output)
    }

    ExecutionFailure execFailure(BuildResult buildResult) {
        OutputScrapingExecutionFailure.from(buildResult.output, buildResult.output)
    }

    private static final String LOWEST_MAJOR_GRADLE_VERSION
    static {
        def releasedGradleVersions = new ReleasedVersionDistributions()
        def probeVersions = ["4.10.3", "5.6.4", "6.9.4", "7.6.4", "8.8"]
        String compatibleVersion = probeVersions.find {version ->
            releasedGradleVersions.getDistribution(version)?.worksWith(Jvm.current())
        }
        LOWEST_MAJOR_GRADLE_VERSION = compatibleVersion
    }

    static String findLowestMajorGradleVersion() {
        LOWEST_MAJOR_GRADLE_VERSION
    }

    static String getLowestMajorGradleVersion() {
        def gradleVersion = LOWEST_MAJOR_GRADLE_VERSION
        if (gradleVersion == null) {
            throw new AssumptionViolatedException("No version of Gradle supports Java ${Jvm.current()}")
        }
        return gradleVersion
    }

    static class Interceptor extends AbstractMultiTestInterceptor {

        private static final Map<Class<? extends Annotation>, GradleVersion> MINIMUM_VERSIONS_BY_ANNOTATIONS = [
            (InspectsExecutedTasks): TestKitFeature.CAPTURE_BUILD_RESULT_TASKS.since,
            (InspectsBuildOutput): TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG.since,
            (InjectsPluginClasspath): TestKitFeature.PLUGIN_CLASSPATH_INJECTION.since,
            (CustomDaemonDirectory): CUSTOM_DAEMON_DIR_SUPPORT_VERSION,
            (WithNoSourceTaskOutcome): NO_SOURCE_TASK_OUTCOME_SUPPORT_VERSION,
            (CustomEnvironmentVariables): ENVIRONMENT_VARIABLES_SUPPORT_VERSION,
            (InspectsGroupedOutput): INSPECTS_GROUPED_OUTPUT_SUPPORT_VERSION,
            (HideEnvVariableValuesInDaemonLog): HIDE_ENV_VARIABLE_VALUE_IN_DAEMON_LOG_VERSION
        ]

        private static final IntegrationTestBuildContext BUILD_CONTEXT = new IntegrationTestBuildContext()
        private static final String COMPATIBILITY_SYSPROP_NAME = 'org.gradle.integtest.testkit.compatibility'
        private static final ReleasedVersionDistributions RELEASED_VERSION_DISTRIBUTIONS = new ReleasedVersionDistributions()

        Interceptor(Class<?> target) {
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
                return [underDevelopmentDistribution()] as Set
            }

            String version = System.getProperty(COMPATIBILITY_SYSPROP_NAME, 'current')
            switch (version) {
                case 'all':
                    crossVersion = true
                    return (getMinCompatibleVersions().collect { TestedGradleDistribution.forVersion(it) } +
                        TestedGradleDistribution.mostRecentFinalRelease() +
                        underDevelopmentDistribution()) as SortedSet
                case 'current': return [
                    underDevelopmentDistribution()
                ] as Set
                default:
                    throw new IllegalArgumentException("Invalid value for $COMPATIBILITY_SYSPROP_NAME system property: $version (valid values: 'all', 'current')")
            }
        }

        private static TestedGradleDistribution underDevelopmentDistribution() {
            if (GradleContextualExecuter.embedded) {
                TestedGradleDistribution.EMBEDDED_UNDER_DEVELOPMENT
            } else {
                TestedGradleDistribution.UNDER_DEVELOPMENT
            }

        }

        private void addExecutions(@Nullable GradleDistribution releasedDist, TestedGradleDistribution testedGradleDistribution) {
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

        private Set<GradleVersion> getMinCompatibleVersions() {

            GradleVersion minSpecVersion = MINIMUM_VERSIONS_BY_ANNOTATIONS.keySet()
                .findAll { annotation -> target.getAnnotation(annotation) }
                .collect { MINIMUM_VERSIONS_BY_ANNOTATIONS[it] }
                .max()
                ?: MIN_TESTED_VERSION

            Set<GradleVersion> minFeatureVersions = target.getDeclaredMethods().collect { feature ->
                MINIMUM_VERSIONS_BY_ANNOTATIONS.keySet()
                    .findAll { annotation -> feature.getAnnotation(annotation) }
                    .collect { MINIMUM_VERSIONS_BY_ANNOTATIONS[it] }
                    .max()
                    ?: MIN_TESTED_VERSION
            }

            return ((minFeatureVersions + minSpecVersion) as SortedSet).tailSet(MIN_TESTED_VERSION)
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

            private static
            final TestedGradleDistribution EMBEDDED_UNDER_DEVELOPMENT = new TestedGradleDistribution(BUILD_CONTEXT.version, GradleProvider.embedded()) {
                @Override
                String getDisplayName() {
                    return "current embedded"
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
                new TestedGradleDistribution(RELEASED_VERSION_DISTRIBUTIONS.mostRecentRelease.version,
                    GradleProvider.version(RELEASED_VERSION_DISTRIBUTIONS.mostRecentRelease.version.version))
            }

        }

        private static class IgnoredGradleRunnerExecution extends AbstractMultiTestInterceptor.Execution {

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
            String toString() {
                return getDisplayName()
            }

            @Override
            boolean isTestEnabled(AbstractMultiTestInterceptor.TestDetails testDetails) {
                false
            }
        }

        private static class GradleRunnerExecution extends AbstractMultiTestInterceptor.Execution {

            private final boolean debug
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
            String toString() {
                return getDisplayName()
            }

            @Override
            protected void before(IMethodInvocation invocation) {
                super.before(invocation)
                BaseGradleRunnerIntegrationTest.debug = debug
                gradleVersion = testedGradleDistribution.gradleVersion
                gradleProvider = testedGradleDistribution.gradleProvider
            }

            @Override
            boolean isTestEnabled(AbstractMultiTestInterceptor.TestDetails testDetails) {
                def gradleVersion = testedGradleDistribution.gradleVersion

                if (testDetails.getAnnotation(InjectsPluginClasspath) && gradleVersion < MINIMUM_VERSIONS_BY_ANNOTATIONS[InjectsPluginClasspath]) {
                    return false
                }

                if (testDetails.getAnnotation(InspectsBuildOutput) && debug && gradleVersion < MINIMUM_VERSIONS_BY_ANNOTATIONS[InspectsBuildOutput]) {
                    return false
                }

                if (testDetails.getAnnotation(InspectsExecutedTasks) && gradleVersion < MINIMUM_VERSIONS_BY_ANNOTATIONS[InspectsExecutedTasks]) {
                    return false
                }

                if (testDetails.getAnnotation(NoDebug) && debug) {
                    return false
                }

                if (testDetails.getAnnotation(Debug) && !debug) {
                    return false
                }

                if (testDetails.getAnnotation(CustomDaemonDirectory) && gradleVersion < CUSTOM_DAEMON_DIR_SUPPORT_VERSION) {
                    return false
                }
                if (testDetails.getAnnotation(WithNoSourceTaskOutcome) && gradleVersion < NO_SOURCE_TASK_OUTCOME_SUPPORT_VERSION) {
                    return false
                }
                if (testDetails.getAnnotation(CustomEnvironmentVariables) && gradleVersion < ENVIRONMENT_VARIABLES_SUPPORT_VERSION) {
                    return false
                }
                if (testDetails.getAnnotation(InspectsGroupedOutput) && gradleVersion < INSPECTS_GROUPED_OUTPUT_SUPPORT_VERSION) {
                    return false
                }
                if (testDetails.getAnnotation(HideEnvVariableValuesInDaemonLog) && gradleVersion < HIDE_ENV_VARIABLE_VALUE_IN_DAEMON_LOG_VERSION) {
                    return false
                }

                true
            }

        }
    }
}
