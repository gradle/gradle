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
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.internal.VersionNumber
import spock.lang.Issue

class AndroidGradleRecipesKotlinSmokeTest extends AbstractSmokeTest {

    @Issue('https://github.com/gradle/gradle/issues/23014')
    def "android gradle recipes: custom BuildConfig field in Kotlin (agp=#agpVersion)"() {
        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        file('settings.gradle.kts') << '''
            include(":app")
            rootProject.name = "customBuildConfigField"
        '''

        file('build.gradle.kts') << """
            buildscript {
                repositories {
                    ${googleRepository(GradleDsl.KOTLIN)}
                    ${mavenCentralRepository(GradleDsl.KOTLIN)}
                }
                dependencies {
                    classpath("com.android.tools.build:gradle:$agpVersion")
                    classpath(kotlin("gradle-plugin", version = "$kotlinVersionNumber"))
                }
            }
            allprojects {
                repositories {
                    ${googleRepository(GradleDsl.KOTLIN)}
                    ${mavenCentralRepository(GradleDsl.KOTLIN)}
                }
            }
        """

        file('app/build.gradle.kts') << """
            import com.android.build.api.artifact.*
            import com.android.build.api.variant.*

            plugins {
                id("com.android.application")
                kotlin("android")
            }

            abstract class CustomFieldValueProvider : DefaultTask() {

                @get:OutputFile
                abstract val fieldValueOutputFile: RegularFileProperty

                @TaskAction fun writeFieldValue() {
                    fieldValueOutputFile.get().asFile.writeText("42")
                }
            }

            val customFieldValueProvider = tasks.register<CustomFieldValueProvider>("customFieldValueProvider") {
                fieldValueOutputFile.set(
                    layout.buildDirectory.file("intermediates/customFieldValueProvider/output")
                )
                outputs.upToDateWhen { false }
            }

            android {
                namespace = "org.gradle.smoketests.androidrecipes"
                compileSdkVersion(29)
                buildToolsVersion("${TestedVersions.androidTools}")
                buildFeatures { buildConfig = true }
            }

            androidComponents {
                onVariants {
                    it.buildConfigFields.put("MyCustomField", customFieldValueProvider
                        .flatMap { task -> task.fieldValueOutputFile }
                        .map { file ->
                            BuildConfigField(
                                "String",
                                "\\"{file.asFile.readText(Charsets.UTF_8)}\\"",
                                "My custom field"
                            )
                        }
                    )
                }
            }
        """

        file('app/src/main/kotlin/org/gradle/smoketests/androidrecipes/MainActivity.kt') << '''
            package org.gradle.smoketests.androidrecipes

            import android.app.Activity
            import android.os.Bundle
            import android.widget.TextView

            class MainActivity : Activity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(
                        TextView(this).apply {
                            setText("It's ${BuildConfig.MyCustomField}")
                        }
                    )
                }
            }
        '''

        file('app/src/main/AndroidManifest.xml') << '''<?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:label="Minimal">
                    <activity android:name="MainActivity">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>'''.stripIndent()

        and:
        def runner = useAgpVersion(agpVersion, runner('assembleDebug'))

        when: 'running the build for the 1st time'
        SantaTrackerConfigurationCacheWorkaround.beforeBuild(runner.projectDir, IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir)
        def result = runnerWithDeprecations(runner, agpVersion, kotlinVersionNumber).build()

        then:
        result.task(':app:assembleDebug').outcome == TaskOutcome.SUCCESS

        and:
        assertConfigurationCacheStateStored()

        when: 'running the build for the 2nd time'
        SantaTrackerConfigurationCacheWorkaround.beforeBuild(runner.projectDir, IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir)
        result = (
            GradleContextualExecuter.isConfigCache()
                ? runner
                : runnerWithDeprecations(runner, agpVersion, kotlinVersionNumber)
        ).build()

        then:
        result.task(':app:assembleDebug').outcome == TaskOutcome.UP_TO_DATE

        and:
        assertConfigurationCacheStateLoaded()

        where:
        agpVersion << TestedVersions.androidGradle
        kotlinVersionNumber = VersionNumber.parse('1.7.0')
    }

    private SmokeTestGradleRunner runnerWithDeprecations(
        SmokeTestGradleRunner runner,
        String agpVersion,
        VersionNumber kotlinVersionNumber
    ) {
        runner.deprecations(KotlinAndroidDeprecations) {
            expectOrgGradleUtilWrapUtilDeprecation(kotlinVersionNumber)
            maybeExpectOrgGradleUtilGUtilDeprecation(agpVersion)
            expectForUseAtConfigurationTimeDeprecation(kotlinVersionNumber)
            expectAndroidWorkerExecutionSubmitDeprecationWarning(agpVersion)
            expectProjectConventionDeprecationWarning(agpVersion)
            maybeExpectConventionTypeDeprecation(kotlinVersionNumber)
            expectAndroidConventionTypeDeprecationWarning(agpVersion)
            expectBasePluginConventionDeprecation(agpVersion)
        }
    }
}
