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

package org.gradle.testkit.runner.fixtures

import groovy.transform.Sortable
import groovy.transform.TupleConstructor
import org.gradle.integtests.fixtures.AbstractMultiTestRunner
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.testkit.runner.internal.dist.GradleDistribution
import org.gradle.testkit.runner.internal.dist.InstalledGradleDistribution
import org.gradle.testkit.runner.internal.dist.VersionBasedGradleDistribution
import org.gradle.util.GradleVersion
import org.gradle.wrapper.GradleUserHomeLookup

import java.lang.annotation.Annotation

import static org.gradle.testkit.runner.fixtures.FeatureCompatibility.*

/**
 * Verifies GradleRunner functionality against "supported" Gradle versions.
 *
 * Compatibility testing is performed against a subset of Gradle versions to limit the (growing) number of required test executions:
 *
 * - The minimum Gradle version a feature is compatible with (if no specific feature is provided, then the minimum Gradle version is used that supports TestKit)
 * - The most recent released Gradle version
 * - The Gradle version under development
 */
class GradleRunnerCompatibilityIntegTestRunner extends AbstractMultiTestRunner {
    /**
     * Read by tests to configure themselves to determine the Gradle distribution used for test execution.
     */
    public static GradleDistribution distribution

    /**
     * Read by tests to configure themselves for debug or not.
     */
    public static boolean debug

    /**
     * TestKit features annotations read by tests to determine the minimum compatible Gradle version.
     */
    private static final List<? extends Annotation> TESTKIT_FEATURES = [PluginClasspathInjection]

    private static final IntegrationTestBuildContext BUILD_CONTEXT = new IntegrationTestBuildContext()
    private static final ReleasedVersionDistributions RELEASED_VERSION_DISTRIBUTIONS = new ReleasedVersionDistributions()

    GradleRunnerCompatibilityIntegTestRunner(Class<?> target) {
        super(target)
    }

    @Override
    protected void createExecutions() {
        determineTestGradleRuntimes().each { gradleRuntime ->
            [true, false].each { debug ->
                add(new GradleRunnerExecution(gradleRuntime, debug))
            }
        }
    }

    private Set<TestedGradleRuntime> determineTestGradleRuntimes() {
        [TestedGradleRuntime.forVersion(getMinCompatibleVersion()),
         TestedGradleRuntime.mostRecentFinalRelease(),
         TestedGradleRuntime.underDevelopment()] as SortedSet
    }

    private GradleVersion getMinCompatibleVersion() {
        List<GradleVersion> testedFeatures = TESTKIT_FEATURES.findAll { target.getAnnotation(it) }.collect { getMinSupportedVersion(it) }
        (!testedFeatures.empty && isFeatureVersionValid(testedFeatures.min())) ? testedFeatures.min() : TESTKIT_MIN_SUPPORTED_VERSION
    }

    private boolean isFeatureVersionValid(GradleVersion featureVersion) {
        isValidVersion(featureVersion, TESTKIT_MIN_SUPPORTED_VERSION)
    }

    @TupleConstructor
    @Sortable(includes = ['gradleVersion'])
    private static class TestedGradleRuntime {
        final GradleVersion gradleVersion
        final GradleDistribution gradleDistribution

        static TestedGradleRuntime forVersion(GradleVersion gradleVersion) {
            new TestedGradleRuntime(gradleVersion, new VersionBasedGradleDistribution(gradleVersion.version))
        }

        static TestedGradleRuntime mostRecentFinalRelease() {
            new TestedGradleRuntime(RELEASED_VERSION_DISTRIBUTIONS.mostRecentFinalRelease.version,
                                    new VersionBasedGradleDistribution(RELEASED_VERSION_DISTRIBUTIONS.mostRecentFinalRelease.version.version))
        }

        static TestedGradleRuntime underDevelopment() {
            new TestedGradleRuntime(BUILD_CONTEXT.version, new InstalledGradleDistribution(BUILD_CONTEXT.gradleHomeDir))
        }
    }

    private static class GradleRunnerExecution extends AbstractMultiTestRunner.Execution {

        private final TestedGradleRuntime testedGradleRuntime
        private final boolean debug
        private String gradleUserHomeSetting

        GradleRunnerExecution(TestedGradleRuntime testedGradleRuntime, boolean debug) {
            this.testedGradleRuntime = testedGradleRuntime
            this.debug = debug
        }

        @Override
        protected String getDisplayName() {
            "version = $testedGradleRuntime.gradleVersion.version, debug = $debug"
        }

        @Override
        protected void before() {
            GradleRunnerCompatibilityIntegTestRunner.distribution = testedGradleRuntime.gradleDistribution
            GradleRunnerCompatibilityIntegTestRunner.debug = debug
            gradleUserHomeSetting = System.setProperty(GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY, BUILD_CONTEXT.gradleUserHomeDir.absolutePath)
        }

        @Override
        protected void after() {
            if (gradleUserHomeSetting) {
                System.setProperty(GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY, gradleUserHomeSetting)
            }
        }

        @Override
        protected boolean isTestEnabled(AbstractMultiTestRunner.TestDetails testDetails) {
            if (isDebugModeAndBuildOutputCaptured(testDetails)) {
                return false
            }

            !debug || !testDetails.getAnnotation(NoDebug)
        }

        private boolean isDebugModeAndBuildOutputCaptured(AbstractMultiTestRunner.TestDetails testDetails) {
            def captureBuildOutputInDebug = testDetails.getAnnotation(CaptureBuildOutputInDebug)
            debug && captureBuildOutputInDebug && !isSupported(CaptureBuildOutputInDebug, testedGradleRuntime.gradleVersion)
        }
    }
}
