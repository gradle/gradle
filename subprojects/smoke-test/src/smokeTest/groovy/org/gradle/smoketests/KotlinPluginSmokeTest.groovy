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

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.android.AndroidHome
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.Requires
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT

class KotlinPluginSmokeTest extends AbstractSmokeTest {

    @Unroll
    @ToBeFixedForInstantExecution
    def 'kotlin #version plugin, workers=#workers'() {
        given:
        useSample("kotlin-example")
        replaceVariablesInBuildFile(kotlinVersion: version)

        when:
        def result = build(workers, 'run')

        then:
        result.task(':compileKotlin').outcome == SUCCESS

        if (version == TestedVersions.kotlin.latest()) {
            expectNoDeprecationWarnings(result)
        }

        where:
        [version, workers] << [
            TestedVersions.kotlin.versions,
            [true, false]
        ].combinations()
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def 'kotlin #kotlinPluginVersion android #androidPluginVersion plugins, workers=#workers'() {
        given:
        AndroidHome.assertIsSet()
        useSample(sampleName)

        def buildFileName = sampleName.endsWith("kotlin-dsl")
            ? "build.gradle.kts"
            : "build.gradle"
        [buildFileName, "app/$buildFileName"].each { sampleBuildFileName ->
            replaceVariablesInFile(
                file(sampleBuildFileName),
                kotlinVersion: kotlinPluginVersion,
                androidPluginVersion: androidPluginVersion,
                androidBuildToolsVersion: TestedVersions.androidTools)
        }

        when:
        def result = build(workers, 'clean', ':app:testDebugUnitTestCoverage')

        then:
        result.task(':app:testDebugUnitTestCoverage').outcome == SUCCESS

        if (kotlinPluginVersion == TestedVersions.kotlin.latest()
            && androidPluginVersion == TestedVersions.androidGradle.latest()) {
            expectNoDeprecationWarnings(result)
        }

        where:
// To run a specific combination, set the values here, uncomment the following four lines
//  and comment out the lines coming after
//        kotlinPluginVersion = '1.3.61'
//        androidPluginVersion = '3.5.1'
//        workers = false
//        sampleName = 'android-kotlin-example-kotlin-dsl'

        [kotlinPluginVersion, androidPluginVersion, workers, sampleName] << [
            TestedVersions.kotlin.versions,
            TestedVersions.androidGradle.versions,
            [true, false],
            ["android-kotlin-example", "android-kotlin-example-kotlin-dsl"]
        ].combinations()
    }

    @Unroll
    @Requires(KOTLIN_SCRIPT)
    @ToBeFixedForInstantExecution
    def 'kotlin js #version plugin, workers=#workers'() {
        given:
        useSample("kotlin-js-sample")
        withKotlinBuildFile()
        replaceVariablesInBuildFile(kotlinVersion: version)

        when:
        def result = build(workers, 'compileKotlin2Js')

        then:
        result.task(':compileKotlin2Js').outcome == SUCCESS

        if (version == TestedVersions.kotlin.latest()) {
            expectDeprecationWarnings(result,
                "The compile configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 7.0. Please use the implementation configuration instead."
            )
        }

        where:
        [version, workers] << [
            TestedVersions.kotlin.versions,
            [true, false]
        ].combinations()
    }

    private BuildResult build(boolean workers, String... tasks) {
        return runner(tasks + ["--parallel", "-Pkotlin.parallel.tasks.in.project=$workers"] as String[])
            .forwardOutput()
            .build()
    }
}
