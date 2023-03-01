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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber
import spock.lang.Issue

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import static org.junit.Assume.assumeFalse
import static org.junit.Assume.assumeTrue

class KotlinPluginSmokeTest extends AbstractPluginValidatingSmokeTest implements ValidationMessageChecker {

    def 'kotlin jvm (kotlin=#version, workers=#workers)'() {
        given:
        useSample("kotlin-example")
        replaceVariablesInBuildFile(kotlinVersion: version)
        def versionNumber = VersionNumber.parse(version)

        when:
        def result = runner(workers, versionNumber, 'run')
            .deprecations(KotlinDeprecations) {
                expectKotlinWorkerSubmitDeprecation(workers, version)
                expectKotlinArchiveNameDeprecation(version)
                expectAbstractCompileDestinationDirDeprecation(version)
                expectOrgGradleUtilWrapUtilDeprecation(version)
            }.build()

        then:
        result.task(':compileKotlin').outcome == SUCCESS
        assert result.output.contains("Hello world!")

        when:
        result = runner(workers, versionNumber, 'run')
            .deprecations(KotlinDeprecations) {
                if (GradleContextualExecuter.isNotConfigCache()) {
                    expectOrgGradleUtilWrapUtilDeprecation(version)
                }
            }.build()

        then:
        result.task(':compileKotlin').outcome == UP_TO_DATE
        assert result.output.contains("Hello world!")

        where:
        [version, workers] << [
            TestedVersions.kotlin.versions,
            [true, false]
        ].combinations()
    }

    def 'kotlin javascript (kotlin=#version, workers=#workers)'() {

        // kotlinjs has been removed in Kotlin 1.7 in favor of kotlin-mpp
        assumeTrue(VersionNumber.parse(version).baseVersion < VersionNumber.version(1, 7))

        given:
        useSample("kotlin-js-sample")
        withKotlinBuildFile()
        replaceVariablesInBuildFile(kotlinVersion: version)
        def versionNumber = VersionNumber.parse(version)

        when:
        def result = runner(workers, versionNumber, 'compileKotlin2Js')
            .deprecations(KotlinDeprecations) {
                expectKotlinWorkerSubmitDeprecation(workers, version)
                expectKotlin2JsPluginDeprecation(version)
                expectKotlinParallelTasksDeprecation(version)
                expectKotlinCompileDestinationDirPropertyDeprecation(version)
                expectKotlinArchiveNameDeprecation(version)
                expectOrgGradleUtilWrapUtilDeprecation(version)
            }.build()

        then:
        result.task(':compileKotlin2Js').outcome == SUCCESS

        where:
        [version, workers] << [
            TestedVersions.kotlin.versions,
            [true, false]
        ].combinations()
    }

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
        def versionNumber = VersionNumber.parse(kotlinVersion)

        when:
        def result = runner(false, versionNumber, 'compileJava')
            .deprecations(KotlinDeprecations) {
                expectKotlinArchiveNameDeprecation(kotlinVersion)
                expectAbstractCompileDestinationDirDeprecation(kotlinVersion)
                expectOrgGradleUtilWrapUtilDeprecation(kotlinVersion)
            }.build()

        then:
        result.task(':compileJava').outcome == SUCCESS
        result.tasks.collect { it.path } == [':compileGroovy', ':compileKotlin', ':compileJava']

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    def 'kotlin jvm and java-gradle-plugin plugins combined (kotlin=#kotlinVersion)'() {

        assumeFalse(kotlinVersion.startsWith("1.3."))
        assumeFalse(kotlinVersion.startsWith("1.4."))
        assumeFalse(kotlinVersion.startsWith("1.5."))
        assumeFalse(kotlinVersion.startsWith("1.6."))

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
        def versionNumber = VersionNumber.parse(kotlinVersion)

        when:
        def result = runner(false, versionNumber, 'build')
            .deprecations(KotlinDeprecations) {
                expectAbstractCompileDestinationDirDeprecation(kotlinVersion)
                expectOrgGradleUtilWrapUtilDeprecation(kotlinVersion)
            }.build()

        then:
        result.task(':compileKotlin').outcome == SUCCESS

        when:
        result = runner(false, versionNumber, 'build')
            .deprecations(KotlinDeprecations) {
                if (GradleContextualExecuter.isNotConfigCache()) {
                    expectOrgGradleUtilWrapUtilDeprecation(kotlinVersion)
                }
            }.build()

        then:
        result.task(':compileKotlin').outcome == UP_TO_DATE

        where:
        kotlinVersion << TestedVersions.kotlin.versions
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

        when:
        def versionNumber = VersionNumber.parse(kotlinVersion)
        def result = runner(false, versionNumber, ':tasks')
            .expectDeprecationWarning("The TestReport.reportOn(Object...) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Please use the testResults method instead. See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.tasks.testing.TestReport.html#org.gradle.api.tasks.testing.TestReport:testResults for more details.", '')
            .expectDeprecationWarning("The TestReport.destinationDir property has been deprecated. This is scheduled to be removed in Gradle 9.0. Please use the destinationDirectory property instead. See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.tasks.testing.TestReport.html#org.gradle.api.tasks.testing.TestReport:destinationDir for more details.", '')
            .deprecations(KotlinDeprecations) {
                expectOrgGradleUtilWrapUtilDeprecation(kotlinVersion)
            }
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
        def versionNumber = VersionNumber.parse(kotlinVersion)
        def testRunner = runner(false, versionNumber, ':resolve', '--stacktrace')

        if (versionNumber < VersionNumber.parse('1.7.22')) {
            testRunner.expectDeprecationWarning("The AbstractCompile.destinationDir property has been deprecated. This is scheduled to be removed in Gradle 9.0. Please use the destinationDirectory property instead. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#compile_task_wiring", '')
        }

        testRunner.deprecations(KotlinDeprecations) {
            expectOrgGradleUtilWrapUtilDeprecation(kotlinVersion)
        }

        def result = testRunner.build()

        then:
        result.output.contains("other-jvm.jar")

        where:
        // withJava is incompatible pre 1.6.20 since it attempts to set the `archiveName` convention property on the Jar task.
        kotlinVersion << TestedVersions.kotlin.versions.findAll { VersionNumber.parse(it) > VersionNumber.parse("1.6.10") }
    }

    private SmokeTestGradleRunner runner(boolean workers, VersionNumber kotlinVersion, String... tasks) {
        return runnerFor(this, workers, kotlinVersion, tasks)
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
        def androidVersion = TestedVersions.androidGradle.latestStable()
        if (testedPluginId in ['org.jetbrains.kotlin.kapt', 'org.jetbrains.kotlin.plugin.scripting']) {
            return ['org.jetbrains.kotlin.jvm': version]
        }
        if (isAndroidKotlinPlugin(testedPluginId)) {
            AGP_VERSIONS.assumeAgpSupportsCurrentJavaVersionAndKotlinVersion(androidVersion, version)
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
            if (testedPluginId == 'org.jetbrains.kotlin.js') {
                buildFile << """
                    kotlin { js(IR) { browser() } }
                """
            }
            if (testedPluginId == 'org.jetbrains.kotlin.multiplatform') {
                buildFile << """
                    kotlin {
                        jvm()
                        js(IR) { browser() }
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

    static SmokeTestGradleRunner runnerFor(AbstractSmokeTest smokeTest, boolean workers, String... tasks) {
        smokeTest.runner(tasks + ["--parallel", "-Pkotlin.parallel.tasks.in.project=$workers"] as String[])
            .forwardOutput()
    }

    static SmokeTestGradleRunner runnerFor(AbstractSmokeTest smokeTest, boolean workers, VersionNumber kotlinVersion, String... tasks) {
        if (kotlinVersion.getMinor() < 5 && JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_16)) {
            String kotlinOpts = "-Dkotlin.daemon.jvm.options=--add-exports=java.base/sun.nio.ch=ALL-UNNAMED,--add-opens=java.base/java.util=ALL-UNNAMED"
            return runnerFor(smokeTest, workers, tasks + [kotlinOpts] as String[])
        }
        runnerFor(smokeTest, workers, tasks)
    }

    private static boolean isAndroidKotlinPlugin(String pluginId) {
        return pluginId.contains('android')
    }

    static class KotlinDeprecations extends BaseDeprecations implements WithKotlinDeprecations {
        private static final String ARCHIVE_NAME_DEPRECATION = "The AbstractArchiveTask.archiveName property has been deprecated. " +
            "This is scheduled to be removed in Gradle 8.0. Please use the archiveFileName property instead. " +
            "See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.tasks.bundling.AbstractArchiveTask.html#org.gradle.api.tasks.bundling.AbstractArchiveTask:archiveName for more details."

        KotlinDeprecations(SmokeTestGradleRunner runner) {
            super(runner)
        }

        void expectKotlinArchiveNameDeprecation(String kotlinPluginVersion) {
            VersionNumber kotlinVersionNumber = VersionNumber.parse(kotlinPluginVersion)
            runner.expectLegacyDeprecationWarningIf(kotlinVersionNumber.minor == 3, ARCHIVE_NAME_DEPRECATION)
        }

        void expectKotlin2JsPluginDeprecation(String version) {
            VersionNumber versionNumber = VersionNumber.parse(version)
            runner.expectLegacyDeprecationWarningIf(versionNumber >= VersionNumber.parse('1.4.0'),
                "The `kotlin2js` Gradle plugin has been deprecated."
            )
        }

        void expectKotlinParallelTasksDeprecation(String version) {
            VersionNumber versionNumber = VersionNumber.parse(version)
            runner.expectLegacyDeprecationWarningIf(
                versionNumber >= VersionNumber.parse('1.5.20') && versionNumber <= VersionNumber.parse('1.6.10'),
                "Project property 'kotlin.parallel.tasks.in.project' is deprecated."
            )
        }

        void expectAbstractCompileDestinationDirDeprecation(String version) {
            VersionNumber versionNumber = VersionNumber.parse(version)
            runner.expectDeprecationWarningIf(
                versionNumber <= VersionNumber.parse("1.6.21"),
                "The AbstractCompile.destinationDir property has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Please use the destinationDirectory property instead. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#compile_task_wiring",
                ""
            )
        }

        void expectOrgGradleUtilWrapUtilDeprecation(String version) {
            VersionNumber versionNumber = VersionNumber.parse(version)
            runner.expectDeprecationWarningIf(
                versionNumber < VersionNumber.parse("1.7.20"),
                "The org.gradle.util.WrapUtil type has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#org_gradle_util_reports_deprecations",
                ""
            )
        }
    }
}
