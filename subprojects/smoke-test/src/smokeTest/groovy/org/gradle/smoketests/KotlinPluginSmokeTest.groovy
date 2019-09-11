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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.android.AndroidHome
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.Requires
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT

class KotlinPluginSmokeTest extends AbstractSmokeTest {

    @Unroll
    def 'kotlin #version plugin, workers=#workers'() {
        given:
        useSample("kotlin-example")
        replaceVariablesInBuildFile(kotlinVersion: version)

        when:
        def result = build(workers, 'run')

        then:
        result.task(':compileKotlin').outcome == SUCCESS

        where:
        [version, workers] << [TestedVersions.kotlin.versions, [true, false]].combinations()
    }

    @Unroll
    def 'kotlin #kotlinPluginVersion android #androidPluginVersion plugins, workers=#workers'() {
        given:
        AndroidHome.assertIsSet()
        useSample("android-kotlin-example")
        replaceVariablesInBuildFile(
            kotlinVersion: kotlinPluginVersion,
            androidPluginVersion: androidPluginVersion,
            androidBuildToolsVersion: TestedVersions.androidTools)

        when:
        def result = build(workers, 'clean', 'testDebugUnitTestCoverage')

        then:
        result.task(':testDebugUnitTestCoverage').outcome == SUCCESS

        where:
        [kotlinPluginVersion, androidPluginVersion, workers] << [
            TestedVersions.kotlin.versions,
            TestedVersions.androidGradle.versions,
            [true, false]
        ].combinations()
    }

    @Unroll
    @Requires(KOTLIN_SCRIPT)
    def 'kotlin js #version plugin, workers=#workers'() {
        given:
        useSample("kotlin-js-sample")
        withKotlinBuildFile()
        replaceVariablesInBuildFile(kotlinVersion: version)

        when:
        def result = build(workers, 'compileKotlin2Js')

        then:
        result.task(':compileKotlin2Js').outcome == SUCCESS

        where:
        [version, workers] << [TestedVersions.kotlin.versions, [true, false]].combinations()
    }

    private BuildResult build(boolean workers, String... tasks) {
        return runner(tasks + ["--parallel", "-Pkotlin.parallel.tasks.in.project=$workers"] as String[])
            .forwardOutput()
            .build()
    }
}
