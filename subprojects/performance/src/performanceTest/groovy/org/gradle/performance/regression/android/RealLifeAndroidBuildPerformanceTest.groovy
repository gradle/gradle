/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.performance.AbstractCrossVersionGradleProfilerPerformanceTest
import org.gradle.performance.categories.SlowPerformanceRegressionTest
import org.gradle.performance.fixture.GradleProfilerCrossVersionPerformanceTestRunner
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.mutations.AbstractCleanupMutator
import org.gradle.profiler.mutations.ApplyAbiChangeToJavaSourceFileMutator
import org.gradle.profiler.mutations.ApplyNonAbiChangeToJavaSourceFileMutator
import org.gradle.profiler.mutations.ClearArtifactTransformCacheMutator
import org.junit.experimental.categories.Category
import spock.lang.Unroll

class RealLifeAndroidBuildPerformanceTest extends AbstractCrossVersionGradleProfilerPerformanceTest {
    private static final SANTA_TRACKER = new AndroidTestProject(
        templateName: 'santaTrackerAndroidBuild',
        memory: '1g',
    )
    private static final LARGE_ANDROID_BUILD = new AndroidTestProject(
        templateName: 'largeAndroidBuild',
        memory: '5g',
    )
    private static final K9_ANDROID = new AndroidTestProject(
        templateName: 'k9AndroidBuild',
        memory: '1g',
    )
    private static final String SANTA_TRACKER_ASSEMBLE_DEBUG = ':santa-tracker:assembleDebug'
    private static final String SANTA_TRACKER_JAVA_FILE_TO_CHANGE = 'snowballrun/src/main/java/com/google/android/apps/santatracker/doodles/snowballrun/BackgroundActor.java'

    def setup() {
        runner.args = ['-Dcom.android.build.gradle.overrideVersionCheck=true']
    }

    @Unroll
    def "#tasks on #testProject"() {
        given:
        testProject.configure(runner)
        runner.tasksToRun = tasks.split(' ')
        runner.args = parallel ? ['-Dorg.gradle.parallel=true'] : []
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        runner.minimumVersion = "5.1.1"
        runner.targetVersions = ["6.0-20190823180744+0000"]
        if (testProject == SANTA_TRACKER) {
            runner.targetVersions = ["5.6"]
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject         | parallel | warmUpRuns | runs | tasks
        K9_ANDROID          | false    | null       | null | 'help'
        K9_ANDROID          | false    | null       | null | 'assembleDebug'
//        K9_ANDROID    | false    | null       | null | 'clean k9mail:assembleDebug'
        LARGE_ANDROID_BUILD | true     | null       | null | 'help'
        LARGE_ANDROID_BUILD | true     | null       | null | 'assembleDebug'
        LARGE_ANDROID_BUILD | true     | 2          | 8    | 'clean phthalic:assembleDebug'
        SANTA_TRACKER       | true     | null       | null | 'assembleDebug'
    }

    @Category(SlowPerformanceRegressionTest)
    @Unroll
    def "clean #tasks on #testProject with clean transforms cache"() {
        given:
        testProject.configure(runner)
        runner.tasksToRun = tasks.split(' ')
        runner.args = ['-Dorg.gradle.parallel=true']
        runner.warmUpRuns = warmUpRuns
        runner.cleanTasks = ["clean"]
        runner.runs = runs
        runner.minimumVersion = "5.4"
        runner.targetVersions = ["5.7-20190807220120+0000"]
        runner.addBuildMutator { invocationSettings ->
            new ClearArtifactTransformCacheMutator(invocationSettings.getGradleUserHome(), AbstractCleanupMutator.CleanupSchedule.BUILD)
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject         | warmUpRuns | runs | tasks
        LARGE_ANDROID_BUILD | 2          | 8    | 'phthalic:assembleDebug'
        LARGE_ANDROID_BUILD | 2          | 8    | 'assembleDebug'
        SANTA_TRACKER       | null       | null | 'assembleDebug'
    }

    @Unroll
    def "abi change on #testProject"() {
        given:
        testProject.configure(runner)
        runner.tasksToRun = [SANTA_TRACKER_ASSEMBLE_DEBUG]
        runner.args = ['-Dorg.gradle.parallel=true']
        runner.minimumVersion = "5.4"
        runner.targetVersions = ["6.0-20190823180744+0000"]
        runner.addBuildMutator { invocationSettings ->
            new ApplyAbiChangeToJavaSourceFileMutator(getSantaTrackerFileToChange(invocationSettings))
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject << [SANTA_TRACKER]
    }

    @Unroll
    def "non-abi change on #testProject"() {
        given:
        testProject.configure(runner)
        runner.tasksToRun = [SANTA_TRACKER_ASSEMBLE_DEBUG]
        runner.args = ['-Dorg.gradle.parallel=true']
        runner.minimumVersion = "5.4"
        runner.targetVersions = ["6.0-20190823180744+0000"]
        runner.addBuildMutator { invocationSettings ->
            new ApplyNonAbiChangeToJavaSourceFileMutator(getSantaTrackerFileToChange(invocationSettings))
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject << [SANTA_TRACKER]
    }

    private static File getSantaTrackerFileToChange(InvocationSettings invocationSettings) {
        new File(invocationSettings.getProjectDir(), SANTA_TRACKER_JAVA_FILE_TO_CHANGE)
    }

    static class AndroidTestProject {
        String templateName
        String memory

        void configure(GradleProfilerCrossVersionPerformanceTestRunner runner) {
            runner.testProject = templateName
            runner.gradleOpts = ["-Xms$memory", "-Xmx$memory"]
        }

        @Override
        String toString() {
            templateName
        }
    }
}
