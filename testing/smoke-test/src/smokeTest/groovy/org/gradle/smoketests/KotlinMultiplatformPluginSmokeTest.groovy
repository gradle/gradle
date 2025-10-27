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

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.test.fixtures.Flaky
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
        replaceCssSupportBlocksInBuildFile()

        when:
        def result = kgpRunner(kotlinVersionNumber, ':tasks')
            .expectLegacyDeprecationWarningIf(
                kotlinVersionNumber.baseVersion < KotlinGradlePluginVersions.KOTLIN_2_1_21,
                "Declaring an 'is-' property with a Boolean type has been deprecated. Starting with Gradle 9.0, this property will be ignored by Gradle. The combination of method name and return type is not consistent with Java Bean property rules and will become unsupported in future versions of Groovy. Add a method named 'getMpp' with the same behavior and mark the old one with @Deprecated, or change the type of 'org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget.isMpp' (and the setter) to 'boolean'. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#groovy_boolean_properties",
            )
            .expectDeprecationWarningIf(
                kotlinVersionNumber.baseVersion > KotlinGradlePluginVersions.KOTLIN_2_1_20,
                "The archives configuration has been deprecated for artifact declaration. This will fail with an error in Gradle 10. Add artifacts as direct task dependencies of the 'assemble' task instead of declaring them in the archives configuration. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#sec:archives-configuration",
                "https://youtrack.jetbrains.com/issue/KT-78620"
            )
            .build()

        then:
        result.task(':tasks').outcome == SUCCESS

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    @Flaky(because = "https://github.com/gradle/gradle-private/issues/4643")
    def 'can run tests with kotlin multiplatform with js project (kotlin=#kotlinVersion)'() {
        given:
        withKotlinBuildFile()
        useSample("kotlin-multiplatform-js-jvm-example")

        def kotlinVersionNumber = VersionNumber.parse(kotlinVersion)
        replaceVariablesInBuildFile(kotlinVersion: kotlinVersion)
        replaceCssSupportBlocksInBuildFile()

        when:
        def result = kgpRunner(kotlinVersionNumber, ':allTests', '-s')
            .expectLegacyDeprecationWarningIf(
                kotlinVersionNumber.baseVersion < KotlinGradlePluginVersions.KOTLIN_2_1_21,
                "Declaring an 'is-' property with a Boolean type has been deprecated. Starting with Gradle 9.0, this property will be ignored by Gradle. The combination of method name and return type is not consistent with Java Bean property rules and will become unsupported in future versions of Groovy. Add a method named 'getMpp' with the same behavior and mark the old one with @Deprecated, or change the type of 'org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget.isMpp' (and the setter) to 'boolean'. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#groovy_boolean_properties",
            )
            .expectDeprecationWarningIf(
                kotlinVersionNumber.baseVersion < KotlinGradlePluginVersions.KOTLIN_2_0_20,
                "Internal API BuildOperationExecutor.getCurrentOperation() has been deprecated. This is scheduled to be removed in Gradle 9.0.",
                "https://youtrack.jetbrains.com/issue/KT-67110"
            )
            .expectLegacyDeprecationWarningIf(
                GradleContextualExecuter.notConfigCache && kotlinVersionNumber.baseVersion < KotlinGradlePluginVersions.KOTLIN_2_1_20,
                "Invocation of Task.project at execution time has been deprecated. This will fail with an error in Gradle 10. This API is incompatible with the configuration cache, which will become the only mode supported by Gradle in a future release. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#task_project"
            )
            .expectDeprecationWarningIf(
                kotlinVersionNumber.baseVersion > KotlinGradlePluginVersions.KOTLIN_2_1_20,
                "The archives configuration has been deprecated for artifact declaration. This will fail with an error in Gradle 10. Add artifacts as direct task dependencies of the 'assemble' task instead of declaring them in the archives configuration. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#sec:archives-configuration",
                "https://youtrack.jetbrains.com/issue/KT-78620"
            )
            .build()

        then:
        result.task(':allTests').outcome == SUCCESS

        where:
        kotlinVersion << TestedVersions.kotlin.versions.findAll {
            // versions prior to 2.0.20 use deprecated APIs removed in Gradle 9.0
            VersionNumber.parse(it) >= KotlinGradlePluginVersions.KOTLIN_2_0_20
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
        def result = kgpRunner(kotlinVersionNumber, ':tasks')
            .expectDeprecationWarningIf(
                kotlinVersionNumber.baseVersion > KotlinGradlePluginVersions.KOTLIN_2_1_20,
                "The archives configuration has been deprecated for artifact declaration. This will fail with an error in Gradle 10. Add artifacts as direct task dependencies of the 'assemble' task instead of declaring them in the archives configuration. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#sec:archives-configuration",
                "https://youtrack.jetbrains.com/issue/KT-78620"
            )
            .build()

        then:
        result.task(':tasks').outcome == SUCCESS
        result.output.contains('allTests - Runs the tests for all targets and create aggregated report')

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    @Issue("https://github.com/gradle/gradle/issues/22952")
    def "kotlin project can consume kotlin multiplatform java project"() {
        given:
        def kotlinVersionNumber = VersionNumber.parse(kotlinVersion)

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
                    // Kotlin 2.1.20: Kotlin multiplatform plugin always configures Java sources compilation and 'withJava()' configuration is deprecated.
                    ${if (kotlinVersionNumber.baseVersion < VersionNumber.parse("2.1.20")) { "withJava()" } else { "" }}
                }
            }
        """

        when:
        def testRunner = kgpRunner(kotlinVersionNumber, ':resolve', '--stacktrace')
            .expectDeprecationWarningIf(
                kotlinVersionNumber.baseVersion > KotlinGradlePluginVersions.KOTLIN_2_1_20,
                "The archives configuration has been deprecated for artifact declaration. This will fail with an error in Gradle 10. Add artifacts as direct task dependencies of the 'assemble' task instead of declaring them in the archives configuration. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#sec:archives-configuration",
                "https://youtrack.jetbrains.com/issue/KT-78620"
            )
        def result = testRunner.build()

        then:
        result.output.contains("other-jvm.jar")

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'org.jetbrains.kotlin.multiplatform': TestedVersions.kotlin
        ]
    }

    private void replaceCssSupportBlocksInBuildFile() {
        Map<String, String> replacementMap = [:]
        replacementMap['enableCssSupportNew'] = """
        commonWebpackConfig {
            cssSupport {
                enabled.set(true)
            }
        }
        """
        replacementMap['enableCssSupportOld'] = ''

        replaceVariablesInBuildFile(replacementMap)
    }
}
