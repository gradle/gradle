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

import org.gradle.performance.categories.PerformanceExperiment
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.util.GFileUtils
import org.junit.experimental.categories.Category
import spock.lang.Unroll

class RealLifeAndroidBuildPerformanceTest extends AbstractAndroidPerformanceTest {

    @Unroll
    def "#tasks on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = tasks.split(' ')
        runner.gradleOpts = ["-Xms$memory", "-Xmx$memory"]
        runner.args = parallel ? ['-Dorg.gradle.parallel=true'] : []
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        runner.minimumVersion = "5.1.1"
        runner.targetVersions = ["5.5-20190515115345+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject         | memory | parallel | warmUpRuns | runs | tasks
        'k9AndroidBuild'           | '1g' | false | null | null | 'help'
        'k9AndroidBuild'           | '1g' | false | null | null | 'assembleDebug'
//        'k9AndroidBuild'    | '1g'   | false    | null       | null | 'clean k9mail:assembleDebug'
        'largeAndroidBuild'        | '5g' | true  | null | null | 'help'
        'largeAndroidBuild'        | '5g' | true  | null | null | 'assembleDebug'
        'largeAndroidBuild'        | '5g' | true  | 2    | 8    | 'clean phthalic:assembleDebug'
        'santaTrackerAndroidBuild' | '1g' | true  | null | null | 'assembleDebug'
    }

    @Category(PerformanceExperiment)
    @Unroll
    def "clean #tasks on #testProject with clean transforms cache"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = tasks.split(' ')
        runner.gradleOpts = ["-Xms$memory", "-Xmx$memory"]
        runner.args = ['-Dorg.gradle.parallel=true']
        runner.warmUpRuns = warmUpRuns
        runner.cleanTasks = ["clean"]
        runner.runs = runs
        runner.minimumVersion = "5.4"
        runner.targetVersions = ["5.5-20190524010357+0000"]
        runner.addBuildExperimentListener(cleanTransformsCacheBeforeInvocation())

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                | memory | warmUpRuns | runs | tasks
        'largeAndroidBuild'        | '5g'   | 2          | 8    | 'phthalic:assembleDebug'
        'largeAndroidBuild'        | '5g'   | 2          | 8    | 'assembleDebug'
        'santaTrackerAndroidBuild' | '1g'   | null       | null | 'assembleDebug'
    }

    private static BuildExperimentListenerAdapter cleanTransformsCacheBeforeInvocation() {
        new BuildExperimentListenerAdapter() {
            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                def transformsCaches = new File(invocationInfo.gradleUserHome, "caches").listFiles(new FilenameFilter() {
                    @Override
                    boolean accept(File dir, String name) {
                        return name.startsWith("transforms-")
                    }
                })
                if (transformsCaches != null) {
                    for (transformsCache in transformsCaches) {
                        GFileUtils.deleteDirectory(transformsCache)
                    }
                }
            }
        }
    }
}
