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
import org.gradle.testdistribution.LocalOnly
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.internal.VersionNumber
import spock.lang.Issue

@LocalOnly(because = "Needs Android environment")
class AndroidGradleRecipesKotlinSmokeTest extends AbstractSmokeTest implements RunnerFactory {

    @Issue('https://github.com/gradle/gradle/issues/23014')
    def "android gradle recipes: custom BuildConfig field in Kotlin (agp=#agpVersion, provider=#providerType)"() {
        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        file('settings.gradle.kts') << '''
            include(":app")
            rootProject.name = "customBuildConfigField"
        '''

        file('build.gradle.kts') << """
            buildscript {
                $repositoriesBlock
                dependencies {
                    classpath("com.android.tools.build:gradle:$agpVersion")
                    classpath(kotlin("gradle-plugin", version = "$kotlinVersionNumber"))
                }
            }
            allprojects {
                $repositoriesBlock
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
                compileSdk = 29
                buildToolsVersion("${TestedVersions.androidTools}")
                buildFeatures { buildConfig = true }
                kotlinOptions { jvmTarget = "1.8" }
            }

            androidComponents {
                onVariants {
                    it.buildConfigFields.put("MyCustomField",
                        customFieldValueProvider
                            .${provider['mapBegin']}
                            BuildConfigField("String", "\\"{${provider['get']}}\\"", "My custom field")
                            ${provider['mapEnd']}
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
        def runner = mixedRunner(false, agpVersion, kotlinVersionNumber, taskName)

        when: 'running the build for the 1st time'
        beforeAndroidBuild(runner)
        def result = runner.build()

        then:
        result.task(":app:$taskName").outcome == TaskOutcome.SUCCESS

        and:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateStored()
        }

        when: 'running the build for the 2nd time'
        result = runner.build()

        then:
        result.task(":app:$taskName").outcome == TaskOutcome.UP_TO_DATE

        and:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateLoaded()
        }

        where:
        [agpVersion, provider] << [
            AGP_VERSIONS.latestsStableOrRC,
            [
                [type: 'flatMap{map{}}',
                 mapBegin: 'flatMap { it.fieldValueOutputFile.map {', mapEnd: '} }',
                 get: 'it.asFile.readText(Charsets.UTF_8)'],
                [type: 'flatMap{map{}.map{}}',
                 mapBegin: 'flatMap { it.fieldValueOutputFile.map { it.asFile.readText(Charsets.UTF_8) }.map {', mapEnd: '} }',
                 get: 'it'],
                [type: 'flatMap{}.map{}',
                 mapBegin: 'flatMap { it.fieldValueOutputFile }.map {', mapEnd: '}',
                 get: 'it.asFile.readText(Charsets.UTF_8)'],
                [type: 'flatMap{}.map{}.map{}',
                 mapBegin: 'flatMap { it.fieldValueOutputFile }.map { it.asFile.readText(Charsets.UTF_8) }.map {', mapEnd: '}',
                 get: 'it']
            ]
        ].combinations()
        providerType = provider['type']
        kotlinVersionNumber = VersionNumber.parse('1.8.0')
        taskName = 'compileDebugKotlin'
    }

    private getRepositoriesBlock() {
        """
            repositories {
                ${googleRepository(GradleDsl.KOTLIN)}
                ${mavenCentralRepository(GradleDsl.KOTLIN)}
            }
        """
    }

    private beforeAndroidBuild(SmokeTestGradleRunner runner) {
        SantaTrackerConfigurationCacheWorkaround.beforeBuild(
            runner.projectDir,
            IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir
        )
    }
}
