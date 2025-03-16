/*
 * Copyright 2021 the original author or authors.
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


import org.gradle.internal.reflect.validation.ValidationMessageChecker

/**
 * For these tests to run you need to set ANDROID_SDK_ROOT to your Android SDK directory
 *
 * https://developer.android.com/studio/releases/build-tools.html
 * https://developer.android.com/studio/releases/gradle-plugin.html
 * https://androidstudio.googleblog.com/
 *
 * To run your tests against all AGP versions from agp-versions.properties, use higher version of java by setting -PtestJavaVersion=<version>
 * See {@link org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions#assumeCurrentJavaVersionIsSupportedBy() assumeCurrentJavaVersionIsSupportedBy} for more details
 */
class AndroidCommunityPluginsSmokeTest extends AbstractPluginValidatingSmokeTest implements ValidationMessageChecker {

    private static final String ANDROID_PLUGIN_VERSION_FOR_TESTS = AGP_VERSIONS.latestStable

    private static final String GOOGLE_SERVICES_PLUGIN_ID = 'com.google.gms.google-services'
    private static final String CRASHLYTICS_PLUGIN_ID = 'com.google.firebase.crashlytics'
    private static final String FIREBASE_PERF_PLUGIN_ID = 'com.google.firebase.firebase-perf'
    private static final String BUGSNAG_PLUGIN_ID = 'com.bugsnag.android.gradle'
    private static final String FLADLE_PLUGIN_ID = 'com.osacky.fladle'
    private static final String TRIPLET_PLAY_PLUGIN_ID = 'com.github.triplet.play'
    private static final String SAFEARGS_PLUGIN_ID = 'androidx.navigation.safeargs'
    private static final String DAGGER_HILT_ANDROID_PLUGIN_ID = 'dagger.hilt.android.plugin'
    private static final String SENTRY_PLUGIN_ID = 'io.sentry.android.gradle'

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            // https://mvnrepository.com/artifact/com.google.gms/google-services?repo=google
            (GOOGLE_SERVICES_PLUGIN_ID): Versions.of('4.3.15'),
            // https://mvnrepository.com/artifact/com.google.firebase.crashlytics/com.google.firebase.crashlytics.gradle.plugin
            (CRASHLYTICS_PLUGIN_ID): Versions.of('2.9.7'),
            // https://mvnrepository.com/artifact/com.google.firebase/perf-plugin
            (FIREBASE_PERF_PLUGIN_ID): Versions.of('1.4.2'),
            // https://plugins.gradle.org/plugin/com.bugsnag.android.gradle
            (BUGSNAG_PLUGIN_ID): Versions.of('8.0.1'),
            // https://plugins.gradle.org/plugin/com.osacky.fladle
            (FLADLE_PLUGIN_ID): Versions.of('0.17.4'),
            // https://plugins.gradle.org/plugin/com.github.triplet.play
            (TRIPLET_PLAY_PLUGIN_ID): Versions.of('3.8.4'),
            // https://mvnrepository.com/artifact/androidx.navigation.safeargs/androidx.navigation.safeargs.gradle.plugin
            (SAFEARGS_PLUGIN_ID): Versions.of('2.6.0'),
            // https://mvnrepository.com/artifact/com.google.dagger/hilt-android-gradle-plugin
            (DAGGER_HILT_ANDROID_PLUGIN_ID): Versions.of('2.47'),
            // https://mvnrepository.com/artifact/io.sentry.android.gradle/io.sentry.android.gradle.gradle.plugin
            (SENTRY_PLUGIN_ID): Versions.of('3.11.1'),
        ]
    }

    @Override
    void configureValidation(String testedPluginId, String version) {
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(ANDROID_PLUGIN_VERSION_FOR_TESTS)
        configureAndroidProject(testedPluginId, version)

        validatePlugins {
            passing {
                true
            }
        }
    }

    private void configureAndroidProject(String testedPluginId, String version) {
        settingsFile << """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    google()
                }
                resolutionStrategy.eachPlugin {
                    if (pluginRequest.id.id.startsWith('com.android')) {
                        useModule("com.android.tools.build:gradle:\${pluginRequest.version}")
                    }
                    if (pluginRequest.id.id == '${GOOGLE_SERVICES_PLUGIN_ID}') {
                        useModule("com.google.gms:google-services:\${pluginRequest.version}")
                    }
                    if (pluginRequest.id.id == '${CRASHLYTICS_PLUGIN_ID}') {
                        useModule("com.google.firebase:firebase-crashlytics-gradle:\${pluginRequest.version}")
                    }
                    if (pluginRequest.id.id == '${FIREBASE_PERF_PLUGIN_ID}') {
                        useModule("com.google.firebase:perf-plugin:\${pluginRequest.version}")
                    }
                    if (pluginRequest.id.id == '${SAFEARGS_PLUGIN_ID}') {
                        useModule("androidx.navigation:navigation-safe-args-gradle-plugin:\${pluginRequest.version}")
                    }
                    if (pluginRequest.id.id == '${DAGGER_HILT_ANDROID_PLUGIN_ID}') {
                        useModule("com.google.dagger:hilt-android-gradle-plugin:\${pluginRequest.version}")
                    }
                    if (pluginRequest.id.id == '${SENTRY_PLUGIN_ID}') {
                        useModule("io.sentry:sentry-android-gradle-plugin:\${pluginRequest.version}")
                    }
                    if (pluginRequest.id.id == '${BUGSNAG_PLUGIN_ID}') {
                        useModule("com.bugsnag:bugsnag-android-gradle-plugin:\${pluginRequest.version}")
                    }
                }
            }
        """
        buildFile << """
                android {
                    namespace = "org.gradle.smoke.test"
                    compileSdk = 24
                    buildToolsVersion = '${TestedVersions.androidTools}'
                    defaultConfig {
                        versionCode = 1
                    }
                }
        """
        file("gradle.properties") << """
            android.useAndroidX=true
            android.enableJetifier=true
        """.stripIndent()
        file("src/main/AndroidManifest.xml") << """<?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="org.gradle.android.example.app">

                <application android:label="@string/app_name" >
                    <activity
                        android:name=".AppActivity"
                        android:label="@string/app_name" >
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <activity
                        android:name="org.gradle.android.example.app.AppActivity">
                    </activity>
                </application>

            </manifest>""".stripIndent()

        switch (testedPluginId) {
            case DAGGER_HILT_ANDROID_PLUGIN_ID:
                buildFile << """
                    dependencies {
                        implementation "com.google.dagger:hilt-android:$version"
                        implementation "com.google.dagger:hilt-compiler:$version"
                    }
                """
                break
            case TRIPLET_PLAY_PLUGIN_ID:
                buildFile << """
                    play {
                        serviceAccountCredentials = file("your-key.json")
                        updatePriority = 2
                    }
                """
                break
        }
    }

    @Override
    Map<String, String> getExtraPluginsRequiredForValidation(String testedPluginId, String version) {
        if (testedPluginId == DAGGER_HILT_ANDROID_PLUGIN_ID) {
            return [
                'com.android.application': ANDROID_PLUGIN_VERSION_FOR_TESTS,
                'org.jetbrains.kotlin.android': KOTLIN_VERSIONS.latestStable
            ]
        }
        return ['com.android.application': ANDROID_PLUGIN_VERSION_FOR_TESTS]
    }
}
