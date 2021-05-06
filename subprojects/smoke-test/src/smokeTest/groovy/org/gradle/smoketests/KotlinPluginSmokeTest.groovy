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

import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class KotlinPluginSmokeTest extends AbstractPluginValidatingSmokeTest implements ValidationMessageChecker {

    static final String NO_CONFIGURATION_CACHE_ITERATION_MATCHER = ".*kotlin=1\\.3\\.[2-6].*"

    // TODO:configuration-cache remove once fixed upstream
    @Override
    protected int maxConfigurationCacheProblems() {
        return 200
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = NO_CONFIGURATION_CACHE_ITERATION_MATCHER)
    def 'kotlin jvm (kotlin=#version, workers=#workers)'() {
        given:
        useSample("kotlin-example")
        replaceVariablesInBuildFile(kotlinVersion: version)

        when:
        def result = build(workers, 'run')

        then:
        result.task(':compileKotlin').outcome == SUCCESS
        assert result.output.contains("Hello world!")

        if (version == TestedVersions.kotlin.latest()) {
            expectNoDeprecationWarnings(result)
        }

        when:
        result = build(workers, 'run')

        then:
        result.task(':compileKotlin').outcome == UP_TO_DATE
        assert result.output.contains("Hello world!")

        where:
        [version, workers] << [
            TestedVersions.kotlin.versions,
            [true, false]
        ].combinations()
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = NO_CONFIGURATION_CACHE_ITERATION_MATCHER)
    def 'kotlin javascript (kotlin=#version, workers=#workers)'() {
        given:
        useSample("kotlin-js-sample")
        withKotlinBuildFile()
        replaceVariablesInBuildFile(kotlinVersion: version)

        when:
        def result = build(workers, 'compileKotlin2Js')

        then:
        result.task(':compileKotlin2Js').outcome == SUCCESS

        where:
        [version, workers] << [
            TestedVersions.kotlin.versions,
            [true, false]
        ].combinations()
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = NO_CONFIGURATION_CACHE_ITERATION_MATCHER)
    def 'kotlin jvm and groovy plugins combined (kotlin=#kotlinVersion)'() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'org.jetbrains.kotlin.jvm' version '$kotlinVersion'
            }

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
        file("src/main/kotlin/Kotlin.kt") << "class Kotlin { val groovy = Groovy() }"
        file("src/main/java/Java.java") << "class Java { private Kotlin kotlin = new Kotlin(); }" // dependency to compileJava->compileKotlin is added by Kotlin plugin

        when:
        def result = build(false, 'compileJava')

        then:
        result.task(':compileJava').outcome == SUCCESS
        result.tasks.collect { it.path } == [':compileGroovy', ':compileKotlin', ':compileJava']

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = NO_CONFIGURATION_CACHE_ITERATION_MATCHER)
    def 'kotlin jvm and java-gradle-plugin plugins combined (kotlin=#kotlinVersion)'() {
        given:
        buildFile << """
            plugins {
                id 'java-gradle-plugin'
                id 'org.jetbrains.kotlin.jvm' version '$kotlinVersion'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion"
            }
        """
        file("src/main/kotlin/Kotlin.kt") << "class Kotlin { }"

        when:
        def result = build(false, 'build')

        then:
        result.task(':compileKotlin').outcome == SUCCESS

        when:
        result = build(false, 'build')

        then:
        result.task(':compileKotlin').outcome == UP_TO_DATE

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

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'org.jetbrains.kotlin.jvm': TestedVersions.kotlin,
            'org.jetbrains.kotlin.js': TestedVersions.kotlin,
            'org.jetbrains.kotlin.multiplatform': TestedVersions.kotlin,
            'org.jetbrains.kotlin.android': TestedVersions.kotlin,
            'org.jetbrains.kotlin.android.extensions': TestedVersions.kotlin,
            'org.jetbrains.kotlin.kapt': TestedVersions.kotlin,
            'org.jetbrains.kotlin.plugin.scripting': TestedVersions.kotlin,
            'org.jetbrains.kotlin.native.cocoapods': TestedVersions.kotlin,
        ]
    }

    @Override
    Map<String, String> getExtraPluginsRequiredForValidation(String testedPluginId, String version) {
        def androidVersion = TestedVersions.androidGradle.latest()
        if (testedPluginId == 'org.jetbrains.kotlin.kapt') {
            return ['org.jetbrains.kotlin.jvm': version]
        }
        if (isAndroidKotlinPlugin(testedPluginId)) {
            AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(androidVersion)
            def extraPlugins = ['com.android.application': androidVersion]
            if (testedPluginId == 'org.jetbrains.kotlin.android.extensions') {
                extraPlugins.put('org.jetbrains.kotlin.android', version)
            }
            return extraPlugins
        }
        return [:]
    }

    @Override
    void configureValidation(String testedPluginId, String version) {
        validatePlugins {
            if (isAndroidKotlinPlugin(testedPluginId)) {
                buildFile << """
                    android {
                        compileSdkVersion 24
                        buildToolsVersion '${TestedVersions.androidTools}'
                    }
                """
            }
            alwaysPasses()
            if (testedPluginId == 'org.jetbrains.kotlin.js' && version != '1.3.72') {
                buildFile << """
                    kotlin { js { browser() } }
                """
            }
            if (testedPluginId == 'org.jetbrains.kotlin.multiplatform') {
                buildFile << """
                    kotlin {
                        jvm()
                        js { browser() }
                    }
                """
            }
            settingsFile << """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        google()
                    }
                }
            """
        }
    }

    private static boolean isAndroidKotlinPlugin(String pluginId) {
        return pluginId.contains('android')
    }
}
