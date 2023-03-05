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

package org.gradle.performance.fixture

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.ScenarioContext

import javax.annotation.Nullable

class AndroidTestProject implements TestProject {

    private static final AndroidGradlePluginVersions AGP_VERSIONS = new AndroidGradlePluginVersions()
    private static final KotlinGradlePluginVersions KGP_VERSIONS = new KotlinGradlePluginVersions()
    private static final String AGP_STABLE_TARGET_VERSION = "7.3"
    private static final String AGP_LATEST_TARGET_VERSION = "7.3"
    public static final LARGE_ANDROID_BUILD = new AndroidTestProject(
        templateName: 'largeAndroidBuild'
    )
    public static final LARGE_ANDROID_BUILD_2 = new AndroidTestProject(
        templateName: 'largeAndroidBuild2'
    )

    public static final List<AndroidTestProject> ANDROID_TEST_PROJECTS = [
        LARGE_ANDROID_BUILD,
        LARGE_ANDROID_BUILD_2,
        IncrementalAndroidTestProject.SANTA_TRACKER,
        IncrementalAndroidTestProject.UBER_MOBILE_APP
    ]

    String templateName

    static AndroidTestProject projectFor(String testProject) {
        def foundProject = findProjectFor(testProject)
        if (!foundProject) {
            throw new IllegalArgumentException("Android project ${testProject} not found")
        }
        return foundProject
    }

    @Nullable
    static AndroidTestProject findProjectFor(String testProject) {
        return ANDROID_TEST_PROJECTS.find { it.templateName == testProject }
    }

    @Override
    void configure(CrossVersionPerformanceTestRunner runner) {
    }

    @Override
    void configure(GradleBuildExperimentSpec.GradleBuilder builder) {
    }

    static void useKotlinLatestStableVersion(CrossVersionPerformanceTestRunner runner) {
        runner.args.add("-DkotlinVersion=${KGP_VERSIONS.latestStable}")
    }

    static void useKotlinLatestVersion(CrossVersionPerformanceTestRunner runner) {
        runner.args.add("-DkotlinVersion=${KGP_VERSIONS.latest}")
    }

    static void useAgpLatestStableVersion(CrossVersionPerformanceTestRunner runner) {
        configureForLatestAgpVersionOfMinor(runner, AGP_STABLE_TARGET_VERSION)
    }

    static void useAgpLatestVersion(CrossVersionPerformanceTestRunner runner) {
        configureForLatestAgpVersionOfMinor(runner, AGP_LATEST_TARGET_VERSION)
    }

    private static void configureForLatestAgpVersionOfMinor(CrossVersionPerformanceTestRunner runner, String lowerBound) {

        def agpVersion = AGP_VERSIONS.getLatestOfMinor(lowerBound)
        runner.args.add("-DagpVersion=${agpVersion}")

        def buildJavaHome = AvailableJavaHomes.getJdk(AGP_VERSIONS.getMinimumJavaVersionFor(agpVersion)).javaHome
        runner.addBuildMutator { invocation -> new JavaHomeMutator(invocation, buildJavaHome) }

        def minimumGradle = AGP_VERSIONS.getMinimumGradleBaseVersionFor(agpVersion)
        if (minimumGradle != null) {
            runner.minimumBaseVersion = minimumGradle
        }
    }

    static class JavaHomeMutator implements BuildMutator {
        private final File buildJavaHome
        private final InvocationSettings invocation

        JavaHomeMutator(InvocationSettings invocation, File buildJavaHome) {
            this.invocation = invocation
            this.buildJavaHome = buildJavaHome
        }

        @Override
        void beforeScenario(ScenarioContext context) {
            def gradleProps = new File(invocation.projectDir, "gradle.properties")
            gradleProps << "\norg.gradle.java.home=${buildJavaHome.absolutePath.replace("\\", "/")}\n"
        }
    }

    @Override
    String toString() {
        templateName
    }
}

