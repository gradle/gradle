/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.performance.regression.android

import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.performance.fixture.CrossVersionPerformanceTestRunner
import org.gradle.performance.fixture.GradleBuildExperimentSpec
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.mutations.ApplyAbiChangeToSourceFileMutator
import org.gradle.profiler.mutations.ApplyNonAbiChangeToSourceFileMutator

class AndroidTestProject {

    public static final LARGE_ANDROID_BUILD = new AndroidTestProject(
        templateName: 'largeAndroidBuild',
        memory: '5g',
    )

    public static final K9_ANDROID = new AndroidTestProject(
        templateName: 'k9AndroidBuild',
        memory: '1g',
    )

    public static final List<AndroidTestProject> ANDROID_TEST_PROJECTS = [
        LARGE_ANDROID_BUILD,
        K9_ANDROID,
        IncrementalAndroidTestProject.SANTA_TRACKER_JAVA,
        IncrementalAndroidTestProject.SANTA_TRACKER_KOTLIN
    ]

    String templateName
    String memory

    static getAndroidTestProject(String testProject) {
        def foundProject = ANDROID_TEST_PROJECTS.find { it.templateName == testProject }
        if (!foundProject) {
            throw new IllegalArgumentException("Android project ${testProject} not found")
        }
        return foundProject
    }

    void configure(CrossVersionPerformanceTestRunner runner) {
        runner.testProject = templateName
        runner.gradleOpts = ["-Xms$memory", "-Xmx$memory"]
    }

    void configure(GradleBuildExperimentSpec.GradleBuilder builder) {
        builder.projectName(templateName)
        builder.invocation {
            gradleOpts("-Xms$memory", "-Xmx$memory")
        }
    }

    @Override
    String toString() {
        templateName
    }
}

class IncrementalAndroidTestProject extends AndroidTestProject {

    private static final AndroidGradlePluginVersions AGP_VERSIONS = new AndroidGradlePluginVersions()
    private static final String ENABLE_AGP_IDE_MODE_ARG = "-Pandroid.injected.invoked.from.ide=true"

    static final SANTA_TRACKER_KOTLIN = new IncrementalAndroidTestProject(
        templateName: 'santaTrackerAndroidBuild',
        memory: '1g',
        pathToChange: 'common/src/main/java/com/google/android/apps/santatracker/AudioPlayer.kt',
        taskToRunForChange: ':santa-tracker:assembleDebug'
    )

    static final SANTA_TRACKER_JAVA = new IncrementalAndroidTestProject(
        templateName: 'santaTrackerAndroidJavaBuild',
        memory: '1g',
        pathToChange: 'village/src/main/java/com/google/android/apps/santatracker/village/SnowFlake.java',
        taskToRunForChange: ':santa-tracker:assembleDebug'
    )

    String pathToChange
    String taskToRunForChange

    @Override
    void configure(CrossVersionPerformanceTestRunner runner) {
        super.configure(runner)
        runner.args.add(ENABLE_AGP_IDE_MODE_ARG)
    }

    @Override
    void configure(GradleBuildExperimentSpec.GradleBuilder builder) {
        super.configure(builder)
        builder.invocation {
            args.add(ENABLE_AGP_IDE_MODE_ARG)
        }
    }

    void configureForLatestAgpVersionOfMinor(CrossVersionPerformanceTestRunner runner, String lowerBound) {
        runner.args.add("-DagpVersion=${AGP_VERSIONS.getLatestOfMinor(lowerBound)}")
    }

    void configureForLatestAgpVersionOfMinor(GradleBuildExperimentSpec.GradleBuilder builder, String lowerBound) {
        builder.invocation.args("-DagpVersion=${AGP_VERSIONS.getLatestOfMinor(lowerBound)}")
    }

    void configureForAbiChange(CrossVersionPerformanceTestRunner runner) {
        configure(runner)
        runner.tasksToRun = [taskToRunForChange]
        runner.addBuildMutator { invocationSettings ->
            new ApplyAbiChangeToSourceFileMutator(getFileToChange(invocationSettings))
        }
    }

    void configureForAbiChange(GradleBuildExperimentSpec.GradleBuilder builder) {
        configure(builder)
        builder.invocation {
            tasksToRun(taskToRunForChange)
        }
        builder.addBuildMutator { invocationSettings ->
            new ApplyAbiChangeToSourceFileMutator(getFileToChange(invocationSettings))
        }
    }

    void configureForNonAbiChange(CrossVersionPerformanceTestRunner runner) {
        configure(runner)
        runner.tasksToRun = [taskToRunForChange]
        runner.addBuildMutator { invocationSettings ->
            new ApplyNonAbiChangeToSourceFileMutator(getFileToChange(invocationSettings))
        }
    }

    void configureForNonAbiChange(GradleBuildExperimentSpec.GradleBuilder builder) {
        configure(builder)
        builder.invocation {
            tasksToRun(taskToRunForChange)
        }
        builder.addBuildMutator { invocationSettings ->
            new ApplyNonAbiChangeToSourceFileMutator(getFileToChange(invocationSettings))
        }
    }

    File getFileToChange(InvocationSettings invocationSettings) {
        new File(invocationSettings.getProjectDir(), pathToChange)
    }
}
