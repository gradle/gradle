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
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

/**
 * Smoke test for the Kotlin plugins.
 *
 * Kotlin Multiplatform tests should be added to {@link KotlinMultiplatformPluginSmokeTest}.
 */
class KotlinPluginSmokeTest extends AbstractKotlinPluginSmokeTest {

    VersionNumber kotlinPluginVersion

    void setupForKotlinVersion(String version) {
        KotlinGradlePluginVersions.assumeCurrentJavaVersionIsSupportedBy(version)
        kotlinPluginVersion = VersionNumber.parse(version)
    }

    def 'kotlin jvm (kotlin=#version, workers=#parallelTasksInProject)'() {
        given:
        setupForKotlinVersion(version)
        useSample("kotlin-example")
        replaceVariablesInBuildFile(kotlinVersion: version)
        when:
        def result = kgpRunner(parallelTasksInProject, kotlinPluginVersion, 'run').build()

        then:
        result.task(':compileKotlin').outcome == SUCCESS
        assert result.output.contains("Hello world!")

        when:
        result = kgpRunner(parallelTasksInProject, kotlinPluginVersion, 'run').build()

        then:
        result.task(':compileKotlin').outcome == UP_TO_DATE
        assert result.output.contains("Hello world!")

        where:
        [version, parallelTasksInProject] << [
            TestedVersions.kotlin.versions,
            [true, false]
        ].combinations()
    }

    def 'kotlin jvm and test suites (kotlin=#version)'() {

        setupForKotlinVersion(version)

        given:
        buildFile << """
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '$version'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useKotlinTest()
            }

            testing.suites.create("integTest", JvmTestSuite) {
                targets.named("integTest") {
                    testTask.configure {
                        useJUnitPlatform()
                    }
                }
                dependencies {
                    implementation("org.junit.platform:junit-platform-launcher")
                    // Version must be empty here to test for emitted deprecation
                    implementation("org.jetbrains.kotlin:kotlin-test-junit5")
                }
            }

            $SKIP_METADATA_VERSION_CHECK
        """

        ["test", "integTest"].each {
            file("src/$it/kotlin/MyTest.kt") << """
                class MyTest {
                    @kotlin.test.Test
                    fun testSum() {
                        assert(2 + 2 == 4)
                    }
                }
            """
        }

        when:
        def result = kgpRunner(false, kotlinPluginVersion, 'test', 'integTest')
            .deprecations(KotlinDeprecations) {
                runner.expectLegacyDeprecationWarningIf(
                    kotlinPluginVersion.baseVersion == KotlinGradlePluginVersions.KOTLIN_2_0_0,
                    "Mutating dependency org.jetbrains.kotlin:kotlin-test-junit5: after it has been finalized has been deprecated. This will fail with an error in Gradle 10.0. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#dependency_mutate_dependency_collector_after_finalize"
                )
            }.build()

        then:
        result.task(':test').outcome == SUCCESS
        result.task(':integTest').outcome == SUCCESS

        where:
        version << TestedVersions.kotlin.versions
    }

    def 'kotlin jvm and groovy plugins combined (kotlin=#kotlinVersion)'() {

        setupForKotlinVersion(kotlinVersion)

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
                libraries.from(files(sourceSets.main.groovy.classesDirectory))
            }

            dependencies {
                implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion"
                implementation localGroovy()
            }

            $SKIP_METADATA_VERSION_CHECK
        """
        file("src/main/groovy/Groovy.groovy") << "class Groovy { }"
        file("src/main/kotlin/Kotlin.kt") << "class Kotlin { val groovy = Groovy() }"
        file("src/main/java/Java.java") << "class Java { private Kotlin kotlin = new Kotlin(); }" // dependency to compileJava->compileKotlin is added by Kotlin plugin

        when:
        def result = kgpRunner(false, kotlinPluginVersion, 'compileJava').build()

        then:
        result.task(':compileJava').outcome == SUCCESS

        // With config cache enabled, for some reason, the `checkKotlinGradlePluginConfigurationErrors`
        // task may appear out of order in the task list
        def tasks = result.tasks.collect { it.path }
        if (GradleContextualExecuter.isConfigCache()) {
            assert tasks.contains(":checkKotlinGradlePluginConfigurationErrors")
            assert tasks.findAll { it != ":checkKotlinGradlePluginConfigurationErrors" } == [':compileGroovy', ':compileKotlin', ':compileJava']
        } else {
            assert tasks == [':checkKotlinGradlePluginConfigurationErrors', ':compileGroovy', ':compileKotlin', ':compileJava']
        }

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    def 'kotlin jvm and java-gradle-plugin plugins combined (kotlin=#kotlinVersion)'() {

        setupForKotlinVersion(kotlinVersion)

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

            $SKIP_METADATA_VERSION_CHECK
        """
        file("src/main/kotlin/Kotlin.kt") << "class Kotlin { }"
        when:
        def result = kgpRunner(false, kotlinPluginVersion, 'build').build()

        then:
        result.task(':compileKotlin').outcome == SUCCESS

        when:
        result = kgpRunner(false, kotlinPluginVersion, 'build').build()

        then:
        result.task(':compileKotlin').outcome == UP_TO_DATE

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    private static final String SKIP_METADATA_VERSION_CHECK =
        """
        tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
            kotlinOptions {
                freeCompilerArgs += "-Xskip-metadata-version-check"
            }
        }
        """

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'org.jetbrains.kotlin.jvm': TestedVersions.kotlin,
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
