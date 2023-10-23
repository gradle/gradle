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

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.util.internal.VersionNumber

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import static org.junit.Assume.assumeFalse
import static org.junit.Assume.assumeTrue

/**
 * Smoke test for the Kotlin plugins.
 *
 * Kotlin Multiplatform tests should be added to {@link KotlinMultiplatformPluginSmokeTest}.
 */
class KotlinPluginSmokeTest extends AbstractKotlinPluginSmokeTest {

    def 'kotlin jvm (kotlin=#version, workers=#parallelTasksInProject)'() {
        given:
        useSample("kotlin-example")
        replaceVariablesInBuildFile(kotlinVersion: version)
        def versionNumber = VersionNumber.parse(version)

        when:
        def result = runner(parallelTasksInProject, versionNumber, 'run')
            .deprecations(KotlinDeprecations) {
                expectKotlinParallelTasksDeprecation(versionNumber, parallelTasksInProject)
                expectKotlinArchiveNameDeprecation(versionNumber)
                expectAbstractCompileDestinationDirDeprecation(versionNumber)
                expectOrgGradleUtilWrapUtilDeprecation(versionNumber) {
                    cause = "plugin 'kotlin'"
                }
                expectProjectConventionDeprecation(versionNumber) {
                    causes = [
                        "plugin 'kotlin'",
                        "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
                    ]
                }
                expectConventionTypeDeprecation(versionNumber) {
                    causes = [
                        "plugin 'kotlin'",
                        "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
                    ]
                }
                expectJavaPluginConventionDeprecation(versionNumber) {
                    causes = [
                        "plugin 'kotlin'",
                        "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
                    ]
                }
                if (GradleContextualExecuter.isConfigCache()) {
                    expectProjectConventionDeprecation(versionNumber)
                    expectConventionTypeDeprecation(versionNumber)
                    expectBasePluginConventionDeprecation(versionNumber)
                    expectKotlinBasePluginExtensionArchivesBaseNameDeprecation(versionNumber)
                    expectForUseAtConfigurationTimeDeprecation(versionNumber)
                }
            }.build()

        then:
        result.task(':compileKotlin').outcome == SUCCESS
        assert result.output.contains("Hello world!")

        when:
        result = runner(parallelTasksInProject, versionNumber, 'run')
            .deprecations(KotlinDeprecations) {
                if (GradleContextualExecuter.isNotConfigCache()) {
                    expectOrgGradleUtilWrapUtilDeprecation(versionNumber) {
                        cause = "plugin 'kotlin'"
                    }
                    expectProjectConventionDeprecation(versionNumber) {
                        causes = [
                            "plugin 'kotlin'",
                            "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
                        ]
                    }
                    expectConventionTypeDeprecation(versionNumber) {
                        causes = [
                            "plugin 'kotlin'",
                            "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
                        ]
                    }
                    expectJavaPluginConventionDeprecation(versionNumber) {
                        causes = [
                            "plugin 'kotlin'",
                            "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
                        ]
                    }
                }
            }.build()

        then:
        result.task(':compileKotlin').outcome == UP_TO_DATE
        assert result.output.contains("Hello world!")

        where:
        [version, parallelTasksInProject] << [
            TestedVersions.kotlin.versions,
            ParallelTasksInProject.values()
        ].combinations()
    }

    def 'kotlin javascript (kotlin=#version, workers=#parallelTasksInProject)'() {

        // kotlinjs has been removed in Kotlin 1.7 in favor of kotlin-mpp
        assumeTrue(VersionNumber.parse(version).baseVersion < VersionNumber.version(1, 7))

        given:
        useSample("kotlin-js-sample")
        withKotlinBuildFile()
        replaceVariablesInBuildFile(kotlinVersion: version)
        def versionNumber = VersionNumber.parse(version)

        when:
        def result = runner(parallelTasksInProject, versionNumber, 'compileKotlin2Js')
            .deprecations(KotlinDeprecations) {
                expectKotlinParallelTasksDeprecation(versionNumber, parallelTasksInProject)
                expectKotlin2JsPluginDeprecation(versionNumber)
                expectKotlinCompileDestinationDirPropertyDeprecation(versionNumber)
                maybeExpectKotlinCompileDestinationDirPropertyDeprecation(versionNumber) {
                    cause = "plugin 'kotlin2js'"
                }
                expectKotlinArchiveNameDeprecation(versionNumber)
                expectOrgGradleUtilWrapUtilDeprecation(versionNumber) {
                    cause = "plugin 'kotlin2js'"
                }
                expectProjectConventionDeprecation(versionNumber) {
                    causes = [null, "plugin 'kotlin2js'"]
                }
                expectConventionTypeDeprecation(versionNumber) {
                    causes = [null, "plugin 'kotlin2js'"]
                }
                expectJavaPluginConventionDeprecation(versionNumber) {
                    causes = [null, "plugin 'kotlin2js'"]
                }
                expectBasePluginConventionDeprecation(versionNumber) {
                    causes = [null, "plugin 'kotlin2js'"]
                }
                expectKotlinBasePluginExtensionArchivesBaseNameDeprecation(versionNumber) {
                    causes = [null, "plugin 'kotlin2js'"]
                }
                if (GradleContextualExecuter.isConfigCache()) {
                    expectForUseAtConfigurationTimeDeprecation(versionNumber)
                }
            }.build()

        then:
        result.task(':compileKotlin2Js').outcome == SUCCESS

        where:
        [version, parallelTasksInProject] << [
            TestedVersions.kotlin.versions,
            ParallelTasksInProject.values()
        ].combinations()
    }

    def 'kotlin jvm and groovy plugins combined (kotlin=#kotlinVersion)'() {

        def versionNumber = VersionNumber.parse(kotlinVersion)
        def kotlinCompileClasspathPropertyName = versionNumber >= VersionNumber.parse("1.7.0") ? 'libraries' : 'classpath'

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
                ${kotlinCompileClasspathPropertyName}.from(files(sourceSets.main.groovy.classesDirectory))
            }

            dependencies {
                implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion"
                implementation localGroovy()
            }
        """
        file("src/main/groovy/Groovy.groovy") << "class Groovy { }"
        file("src/main/kotlin/Kotlin.kt") << "class Kotlin { val groovy = Groovy() }"
        file("src/main/java/Java.java") << "class Java { private Kotlin kotlin = new Kotlin(); }" // dependency to compileJava->compileKotlin is added by Kotlin plugin
        def parallelTasksInProject = ParallelTasksInProject.FALSE

        when:
        def result = runner(parallelTasksInProject, versionNumber, 'compileJava')
            .deprecations(KotlinDeprecations) {
                expectKotlinParallelTasksDeprecation(versionNumber, parallelTasksInProject)
                expectKotlinArchiveNameDeprecation(versionNumber)
                expectAbstractCompileDestinationDirDeprecation(versionNumber)
                expectOrgGradleUtilWrapUtilDeprecation(versionNumber) {
                    cause = "plugin 'org.jetbrains.kotlin.jvm'"
                }
                expectProjectConventionDeprecation(versionNumber) {
                    causes = [
                        "plugin 'org.jetbrains.kotlin.jvm'",
                        "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
                    ]
                }
                expectConventionTypeDeprecation(versionNumber) {
                    causes = [
                        "plugin 'org.jetbrains.kotlin.jvm'",
                        "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
                    ]
                }
                expectJavaPluginConventionDeprecation(versionNumber) {
                    causes = [
                        "plugin 'org.jetbrains.kotlin.jvm'",
                        "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
                    ]
                }
                if (GradleContextualExecuter.isConfigCache()) {
                    expectBasePluginConventionDeprecation(versionNumber)
                    expectKotlinBasePluginExtensionArchivesBaseNameDeprecation(versionNumber)
                    expectForUseAtConfigurationTimeDeprecation(versionNumber)
                }
            }.build()

        then:
        result.task(':compileJava').outcome == SUCCESS
        if (VersionNumber.parse(kotlinVersion).baseVersion >= VersionNumber.parse("1.9.20")) {
            result.tasks.collect { it.path } == [':checkKotlinGradlePluginConfigurationErrors', ':compileGroovy', ':compileKotlin', ':compileJava']
        } else {
            result.tasks.collect { it.path } == [':compileGroovy', ':compileKotlin', ':compileJava']
        }

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    def 'kotlin jvm and java-gradle-plugin plugins combined (kotlin=#kotlinVersion)'() {

        assumeFalse(kotlinVersion.startsWith("1.6."))
        assumeFalse(kotlinVersion.startsWith("1.7."))

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
        def result = runner(ParallelTasksInProject.FALSE, versionNumber, 'build')
            .deprecations(KotlinDeprecations) {
                expectAbstractCompileDestinationDirDeprecation(versionNumber)
                expectOrgGradleUtilWrapUtilDeprecation(versionNumber)
                expectProjectConventionDeprecation(versionNumber) {
                    causes = [
                        "plugin 'kotlin'",
                        "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
                    ]
                }
                expectConventionTypeDeprecation(versionNumber) {
                    causes = [
                        "plugin 'kotlin'",
                        "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
                    ]
                }
                expectJavaPluginConventionDeprecation(versionNumber) {
                    causes = [
                        "plugin 'kotlin'",
                        "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
                    ]
                }
                if (GradleContextualExecuter.isConfigCache()) {
                    expectForUseAtConfigurationTimeDeprecation(versionNumber)
                }
            }.build()

        then:
        result.task(':compileKotlin').outcome == SUCCESS

        when:
        result = runner(ParallelTasksInProject.FALSE, versionNumber, 'build')
            .deprecations(KotlinDeprecations) {
                if (GradleContextualExecuter.isNotConfigCache()) {
                    expectOrgGradleUtilWrapUtilDeprecation(versionNumber)
                    expectProjectConventionDeprecation(versionNumber)
                    expectConventionTypeDeprecation(versionNumber)
                    expectJavaPluginConventionDeprecation(versionNumber)
                }
            }.build()

        then:
        result.task(':compileKotlin').outcome == UP_TO_DATE

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'org.jetbrains.kotlin.jvm': TestedVersions.kotlin,
            'org.jetbrains.kotlin.js': TestedVersions.kotlin,
            'org.jetbrains.kotlin.android': TestedVersions.kotlin,
            'org.jetbrains.kotlin.android.extensions': TestedVersions.kotlin,
            'org.jetbrains.kotlin.kapt': TestedVersions.kotlin,
            'org.jetbrains.kotlin.plugin.scripting': TestedVersions.kotlin,
            'org.jetbrains.kotlin.native.cocoapods': TestedVersions.kotlin,
        ]
    }

    @Override
    Map<String, String> getExtraPluginsRequiredForValidation(String testedPluginId, String version) {
        def androidVersion = AGP_VERSIONS.latestStable
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
}
