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
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

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
    def '#sampleName kotlin #kotlinPluginVersion android #androidPluginVersion plugins, workers=#workers'() {
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
        def result = useAgpVersion(androidPluginVersion, runner(workers, 'clean', ':app:testDebugUnitTestCoverage')).build()

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
                "The compile configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 7.0. " +
                    "Please use the implementation configuration instead. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations"
            )
        }

        where:
        [version, workers] << [
            TestedVersions.kotlin.versions,
            [true, false]
        ].combinations()
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def 'kotlin #kotlinVersion and groovy plugins combined'() {
        given:
        buildFile << """
            buildscript {
                ext.kotlin_version = '$kotlinVersion'
                repositories { mavenCentral() }
                dependencies {
                    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
                }
            }
            apply plugin: 'kotlin'
            apply plugin: 'groovy'

            repositories {
                mavenCentral()
            }

            tasks.named('compileGroovy') {
                classpath = sourceSets.main.compileClasspath
            }
            tasks.named('compileKotlin') {
                classpath += files(sourceSets.main.groovy.classesDirectory)
            }

            dependencies {
                implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion"
                implementation localGroovy()
            }
        """
        file("src/main/groovy/Groovy.groovy") << "class Groovy { }"
        file("src/main/kotlin/Kotlin.kt")     << "class Kotlin { val groovy = Groovy() }"
        file("src/main/java/Java.java")       << "class Java { private Kotlin kotlin = new Kotlin(); }" // dependency to compileJava->compileKotlin is added by Kotlin plugin

        when:
        def result = build(false, 'compileJava')

        then:
        result.task(':compileJava').outcome == SUCCESS
        result.tasks.collect { it.path } == [':compileGroovy', ':compileKotlin', ':compileJava']

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    private BuildResult build(boolean workers, String... tasks) {
        return runner(workers, *tasks).build()
    }

    private GradleRunner runner(boolean workers, String... tasks) {
        return runner(tasks + ["--parallel", "-Pkotlin.parallel.tasks.in.project=$workers"] as String[])
            .forwardOutput()
    }
}
