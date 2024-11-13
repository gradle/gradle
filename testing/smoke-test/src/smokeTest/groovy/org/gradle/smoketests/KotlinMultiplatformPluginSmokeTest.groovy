/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber
import spock.lang.Issue

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Smoke test for the Kotlin Multiplatform plugin.
 */
class KotlinMultiplatformPluginSmokeTest extends AbstractKotlinPluginSmokeTest {
    def 'test kotlin multiplatform with js project (kotlin=#kotlinVersion)'() {
        given:
        withKotlinBuildFile()
        useSample("kotlin-multiplatform-js-jvm-example")

        def kotlinVersionNumber = VersionNumber.parse(kotlinVersion)
        replaceVariablesInBuildFile(kotlinVersion: kotlinVersion)
        replaceCssSupportBlocksInBuildFile(kotlinVersionNumber)

        when:
        def result = kgpRunner(false, kotlinVersionNumber, ':tasks').build()

        then:
        result.task(':tasks').outcome == SUCCESS

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    def 'can run tests with kotlin multiplatform with js project (kotlin=#kotlinVersion)'() {
        given:
        withKotlinBuildFile()
        useSample("kotlin-multiplatform-js-jvm-example")

        def kotlinVersionNumber = VersionNumber.parse(kotlinVersion)
        replaceVariablesInBuildFile(kotlinVersion: kotlinVersion)
        replaceCssSupportBlocksInBuildFile(kotlinVersionNumber)

        when:
        def result = kgpRunner(false, kotlinVersionNumber, ':allTests', '-s')
            .expectDeprecationWarningIf(
                kotlinVersionNumber >= VersionNumber.parse('1.9.22') && kotlinVersionNumber.baseVersion < KotlinGradlePluginVersions.KOTLIN_2_0_20,
                "Internal API BuildOperationExecutor.getCurrentOperation() has been deprecated. This is scheduled to be removed in Gradle 9.0.",
                "https://youtrack.jetbrains.com/issue/KT-67110"
            )
            .expectDeprecationWarningIf(
                GradleContextualExecuter.notConfigCache,
                "Invocation of Task.project at execution time has been deprecated. This will fail with an error in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#task_project",
                "https://youtrack.jetbrains.com/issue/KT-58374"
            )
            .build()

        then:
        result.task(':allTests').outcome == SUCCESS

        where:
        kotlinVersion << TestedVersions.kotlin.versions.findAll {
            def version = VersionNumber.parse(it)
            // versions prior to 1.7.0 use the removed 'void org.gradle.api.reporting.DirectoryReport.setEnabled(boolean)' method
            // 1.7.22 does not define implementation of the 'abstract void failure(java.lang.Object, org.gradle.api.tasks.testing.TestFailure)' of interface org.gradle.api.internal.tasks.testing.TestResultProcessor.
            // versions prior to 2.0.0 don't support java 21
            version > VersionNumber.parse('1.7.22') &&
                (JavaVersion.current() < JavaVersion.VERSION_21 || version >= VersionNumber.parse('2.0.0-Beta1'))
        }
    }

    /**
     * This tests that the usage of deprecated methods in {@code org.gradle.api.tasks.testing.TestReport} task
     * is okay, and ensures the methods are not removed until the versions of the kotlin plugin that uses them
     * is no longer tested.
     *
     * See usage here: https://cs.android.com/android-studio/kotlin/+/master:libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/testing/internal/KotlinTestReport.kt;l=136?q=KotlinTestReport.kt:136&ss=android-studio
     */
    @Issue("https://github.com/gradle/gradle/issues/22246")
    def 'ensure kotlin multiplatform allTests aggregation task can be created (kotlin=#kotlinVersion)'() {
        given:
        buildFile << """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform' version '$kotlinVersion'
            }

            ${mavenCentralRepository()}

            kotlin {
                jvm()
            }
        """
        def kotlinVersionNumber = VersionNumber.parse(kotlinVersion)

        when:
        def result = kgpRunner(false, kotlinVersionNumber, ':tasks').build()

        then:
        result.task(':tasks').outcome == SUCCESS
        result.output.contains('allTests - Runs the tests for all targets and create aggregated report')

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    @Issue("https://github.com/gradle/gradle/issues/22952")
    def "kotlin project can consume kotlin multiplatform java project"() {
        given:
        buildFile << """
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '$kotlinVersion'
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation project(":other")
            }

            task resolve {
                def files = configurations.compileClasspath
                doLast {
                    println("Files: " + files.files)
                }
            }
        """

        settingsFile << "include 'other'"
        file("other/build.gradle") << """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
            }

            ${mavenCentralRepository()}

            kotlin {
                jvm {
                    withJava()
                }
            }
        """

        when:
        def kotlinVersionNumber = VersionNumber.parse(kotlinVersion)
        def testRunner = kgpRunner(false, kotlinVersionNumber, ':resolve', '--stacktrace')
        def result = testRunner.build()

        then:
        result.output.contains("other-jvm.jar")

        where:
        // withJava is incompatible pre 1.6.20 since it attempts to set the `archiveName` convention property on the Jar task.
        kotlinVersion << TestedVersions.kotlin.versions.findAll { VersionNumber.parse(it) > VersionNumber.parse("1.6.10") }
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
                'org.jetbrains.kotlin.multiplatform': TestedVersions.kotlin
        ]
    }

    private void replaceCssSupportBlocksInBuildFile(VersionNumber kotlinVersionNumber) {
        Map<String, String> replacementMap = [:]
        if (kotlinVersionNumber >= VersionNumber.parse('1.8.0')) {
            replacementMap['enableCssSupportNew'] = """
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
            """
            replacementMap['enableCssSupportOld'] = ''
        } else {
            replacementMap['enableCssSupportOld'] = """
                    webpackConfig.cssSupport.enabled = true
            """
            replacementMap['enableCssSupportNew'] = ''
        }

        replaceVariablesInBuildFile(replacementMap)
    }
}
