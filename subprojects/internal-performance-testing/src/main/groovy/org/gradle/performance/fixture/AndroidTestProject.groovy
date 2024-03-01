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

import org.gradle.api.JavaVersion
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
        IncrementalAndroidTestProject.NOW_IN_ANDROID,
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

    static String useKotlinLatestStableOrRcVersion(CrossVersionPerformanceTestRunner runner) {
        def version = KGP_VERSIONS.latestStableOrRC
        runner.args.add("-DkotlinVersion=${ version}")
        version
    }

    static String useKotlinLatestVersion(CrossVersionPerformanceTestRunner runner) {
        def version = KGP_VERSIONS.latest
        runner.args.add("-DkotlinVersion=${ version}")
        version
    }

    static String useAgpLatestOfMinorVersion(CrossVersionPerformanceTestRunner runner, String lowerBound) {
        def version = AGP_VERSIONS.getLatestOfMinor(lowerBound)
        configureForAgpVersion(runner, version)
        version
    }

    static String useAgpLatestStableOrRcVersion(CrossVersionPerformanceTestRunner runner) {
        def version = AGP_VERSIONS.latestStableOrRC
        configureForAgpVersion(runner, version)
        version
    }

    static String useAgpLatestVersion(CrossVersionPerformanceTestRunner runner) {
        def version = AGP_VERSIONS.latest
        configureForAgpVersion(runner, version)
        version
    }

    static void useJavaVersion(CrossVersionPerformanceTestRunner runner, JavaVersion javaVersion) {
        def buildJavaHome = AvailableJavaHomes.getJdk(javaVersion).javaHome
        runner.addBuildMutator { invocation -> new JavaVersionMutator(invocation, javaVersion, buildJavaHome) }
    }

    private static void configureForAgpVersion(CrossVersionPerformanceTestRunner runner, String agpVersion) {
        runner.args.add("-DagpVersion=${agpVersion}")

        def javaVersion = AGP_VERSIONS.getMinimumJavaVersionFor(agpVersion)
        def buildJavaHome = AvailableJavaHomes.getJdk(javaVersion).javaHome
        runner.addBuildMutator { invocation -> new JavaVersionMutator(invocation, javaVersion, buildJavaHome) }

        def minimumGradle = AGP_VERSIONS.getMinimumGradleBaseVersionFor(agpVersion)
        if (minimumGradle != null) {
            runner.minimumBaseVersion = minimumGradle
        }
    }

    static class JavaVersionMutator implements BuildMutator {
        private final File buildJavaHome
        private final JavaVersion javaVersion
        private final InvocationSettings invocation

        JavaVersionMutator(InvocationSettings invocation, JavaVersion javaVersion, File buildJavaHome) {
            this.invocation = invocation
            this.javaVersion = javaVersion
            this.buildJavaHome = buildJavaHome
        }

        @Override
        void beforeScenario(ScenarioContext context) {
            def gradleProps = new File(invocation.projectDir, "gradle.properties")
            gradleProps << "\norg.gradle.java.home=${buildJavaHome.absolutePath.replace("\\", "/")}\n"
            gradleProps << "\nsystemProp.javaVersion=${javaVersion.majorVersion}\n"
        }
    }

    @Override
    String toString() {
        templateName
    }
}

