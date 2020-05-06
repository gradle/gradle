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


import spock.lang.Unroll

import static org.gradle.performance.regression.android.AndroidTestProject.K9_ANDROID
import static org.gradle.performance.regression.android.AndroidTestProject.LARGE_ANDROID_BUILD
import static org.gradle.performance.regression.android.IncrementalAndroidTestProject.SANTA_TRACKER_KOTLIN

class RealLifeAndroidBuildPerformanceTest extends AbstractRealLifeAndroidBuildPerformanceTest {
    @Unroll
    def "#tasks on #testProject"() {
        given:
        testProject.configure(runner)
        runner.tasksToRun = tasks.split(' ')
        if (parallel) {
            runner.args.add('-Dorg.gradle.parallel=true')
        }
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        applyEnterprisePlugin()

        and:
        if (testProject == SANTA_TRACKER_KOTLIN) {
            (testProject as IncrementalAndroidTestProject).configureForLatestAgpVersionOfMinor(runner, SANTA_AGP_TARGET_VERSION)
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject          | parallel | warmUpRuns | runs | tasks
        K9_ANDROID           | false    | null       | null | 'help'
        K9_ANDROID           | false    | null       | null | 'assembleDebug'
//        K9_ANDROID    | false    | null       | null | 'clean k9mail:assembleDebug'
        LARGE_ANDROID_BUILD  | true     | null       | null | 'help'
        LARGE_ANDROID_BUILD  | true     | null       | null | 'assembleDebug'
        LARGE_ANDROID_BUILD  | true     | 2          | 8    | 'clean phthalic:assembleDebug'
        SANTA_TRACKER_KOTLIN | true     | null       | null | 'assembleDebug'
    }

    @Unroll
    def "abi change on #testProject"() {
        given:
        testProject.configureForAbiChange(runner)
        testProject.configureForLatestAgpVersionOfMinor(runner, SANTA_AGP_TARGET_VERSION)
        runner.args.add('-Dorg.gradle.parallel=true')
        applyEnterprisePlugin()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject << [SANTA_TRACKER_KOTLIN]
    }

    @Unroll
    def "non-abi change on #testProject"() {
        given:
        testProject.configureForNonAbiChange(runner)
        testProject.configureForLatestAgpVersionOfMinor(runner, SANTA_AGP_TARGET_VERSION)
        runner.args.add('-Dorg.gradle.parallel=true')
        applyEnterprisePlugin()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject << [SANTA_TRACKER_KOTLIN]
    }
}
