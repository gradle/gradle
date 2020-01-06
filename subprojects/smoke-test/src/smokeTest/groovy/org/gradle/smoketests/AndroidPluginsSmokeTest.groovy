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
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.VersionNumber
import spock.lang.Unroll

/**
 * For these tests to run you need to set ANDROID_HOME to your Android SDK directory
 *
 * https://developer.android.com/studio/releases/build-tools.html
 * https://developer.android.com/studio/releases/gradle-plugin.html
 * https://androidstudio.googleblog.com/
 *
 */
class AndroidPluginsSmokeTest extends AbstractSmokeTest {


    public static final String JAVA_COMPILE_DEPRECATION_MESSAGE = "Extending the JavaCompile task has been deprecated. This is scheduled to be removed in Gradle 7.0. Configure the task instead."

    def setup() {
        AndroidHome.assertIsSet()
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "android application plugin #pluginVersion"(String pluginVersion) {
        given:

        def basedir = '.'

        def packageName = 'org.gradle.android.example'
        def activity = 'MyActivity'
        writeActivity(basedir, packageName, activity)

        file("${basedir}/src/main/res/values/strings.xml") << '''<?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">Android Gradle</string>
            </resources>'''.stripIndent()


        file('src/main/AndroidManifest.xml') << """<?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="${packageName}">

                <application android:label="@string/app_name" >
                    <activity
                        android:name=".${activity}"
                        android:label="@string/app_name" >
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>

            </manifest>""".stripIndent()

        buildFile << buildscript(pluginVersion) << """
            apply plugin: 'com.android.application'

           ${jcenterRepository()}
           ${googleRepository()}

            android.defaultConfig.applicationId "org.gradle.android.myapplication"
        """.stripIndent() << androidPluginConfiguration() << activityDependency()

        when:
        def result = runner(
            'androidDependencies',
            'build',
            'connectedAndroidTest',
            '-x', 'lint').build()

        then:
        def pluginBaseVersion = VersionNumber.parse(pluginVersion).baseVersion
        def threeDotSixBaseVersion = VersionNumber.parse("3.6.0").baseVersion
        if (pluginBaseVersion < threeDotSixBaseVersion) {
            assert result.output.contains(JAVA_COMPILE_DEPRECATION_MESSAGE)
        } else {
            assert !result.output.contains(JAVA_COMPILE_DEPRECATION_MESSAGE)
        }
        result.task(':assemble').outcome == TaskOutcome.SUCCESS
        result.task(':compileReleaseJavaWithJavac').outcome == TaskOutcome.SUCCESS

        if (pluginBaseVersion >= threeDotSixBaseVersion) {
            expectNoDeprecationWarnings(result)
        }

        where:
        pluginVersion << TestedVersions.androidGradle
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "android library plugin #pluginVersion"(String pluginVersion) {
        given:

        def app = 'app'
        def appPackage = 'org.gradle.android.example.app'
        def appActivity = 'AppActivity'

        def library = 'library'
        def libPackage = 'org.gradle.android.example.library'
        def libraryActivity = 'LibraryActivity'

        writeActivity(library, libPackage, libraryActivity)
        file("${library}/src/main/AndroidManifest.xml") << """<?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="${libPackage}">
            </manifest>""".stripIndent()

        writeActivity(app, appPackage, appActivity)
        file("${app}/src/main/java/UsesLibraryActivity.java") << """
            public class UsesLibraryActivity {
                public void consume(${libPackage}.${libraryActivity} activity) {
                }
            }
        """
        file("${app}/src/main/AndroidManifest.xml") << """<?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="${appPackage}">

                <application android:label="@string/app_name" >
                    <activity
                        android:name=".${appActivity}"
                        android:label="@string/app_name" >
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <activity
                        android:name="${libPackage}.${libraryActivity}">
                    </activity>
                </application>

            </manifest>""".stripIndent()
        file("${app}/src/main/res/values/strings.xml") << '''<?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">Android Gradle</string>
            </resources>'''.stripIndent()

        file('settings.gradle') << """
            include ':${app}'
            include ':${library}'
        """

        file('build.gradle') << buildscript(pluginVersion) << """
            subprojects {
                ${jcenterRepository()}
                ${googleRepository()}
            }
        """

        def appBuildFile = file("${app}/build.gradle")
        appBuildFile << """
            apply plugin: 'com.android.application'

            android.defaultConfig.applicationId "org.gradle.android.myapplication"
        """
        appBuildFile << androidPluginConfiguration()
        appBuildFile << activityDependency()
        appBuildFile << """
            dependencies {
                compile project(':${library}')
            }
        """

        def libraryBuildFile = file("${library}/build.gradle")
        libraryBuildFile << """
            apply plugin: 'com.android.library'
        """
        libraryBuildFile << androidPluginConfiguration()
        libraryBuildFile << activityDependency()

        when:
        def result = runner('build', '-x', 'lint').build()

        then:
        result.task(':app:assemble').outcome == TaskOutcome.SUCCESS
        result.task(':library:assemble').outcome == TaskOutcome.SUCCESS
        result.task(':app:compileReleaseJavaWithJavac').outcome == TaskOutcome.SUCCESS

        if (pluginVersion == TestedVersions.androidGradle.latest()) {
            expectNoDeprecationWarnings(result)
        }

        when: 'abi change on library'
        writeActivity(library, libPackage, libraryActivity, true)
        result = runner('build', '-x', 'lint').build()

        then: 'dependent sources are recompiled'
        result.task(':library:compileReleaseJavaWithJavac').outcome == TaskOutcome.SUCCESS
        result.task(':app:compileReleaseJavaWithJavac').outcome == TaskOutcome.SUCCESS

        where:
        pluginVersion << TestedVersions.androidGradle
    }

    private static String activityDependency() {
        """
            dependencies {
                compile 'joda-time:joda-time:2.7'
            }
        """
    }

    private static String buildscript(String pluginVersion) {
        """
            buildscript {
                ${jcenterRepository()}
                ${googleRepository()}

                dependencies {
                    classpath 'com.android.tools.build:gradle:${pluginVersion}'
                }
            }

            System.properties['com.android.build.gradle.overrideVersionCheck'] = 'true'
        """.stripIndent()
    }

    private writeActivity(String basedir, String packageName, String className, changed = false) {
        String resourceName = className.toLowerCase()

        file("${basedir}/src/main/java/${packageName.replaceAll('\\.', '/')}/${className}.java").text = """
            package ${packageName};

            import org.joda.time.LocalTime;

            import android.app.Activity;
            import android.os.Bundle;
            import android.widget.TextView;

            public class ${className} extends Activity {

                @Override
                public void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    setContentView(R.layout.${resourceName}_layout);
                }

                @Override
                public void onStart() {
                    super.onStart();
                    LocalTime currentTime = new LocalTime();
                    TextView textView = (TextView) findViewById(R.id.text_view);
                    textView.setText("The current local time is: " + currentTime);
                }

                ${changed ? "public void doStuff() {}" : ""}
            }""".stripIndent()

        file("${basedir}/src/main/res/layout/${resourceName}_layout.xml").text = '''<?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:orientation="vertical"
                android:layout_width="fill_parent"
                android:layout_height="fill_parent"
                >
            <TextView
                android:id="@+id/text_view"
                android:layout_width="fill_parent"
                android:layout_height="wrap_content"
                />
            </LinearLayout>'''.stripIndent()
    }

    def androidPluginConfiguration() {
        """
            android {
                compileSdkVersion 22
                buildToolsVersion "${TestedVersions.androidTools}"

                defaultConfig {
                    minSdkVersion 22
                    targetSdkVersion 26
                    versionCode 1
                    versionName "1.0"
                }
                compileOptions {
                    sourceCompatibility JavaVersion.VERSION_1_7
                    targetCompatibility JavaVersion.VERSION_1_7
                }
                buildTypes {
                    release {
                        minifyEnabled false
                    }
                }
            }
        """.stripIndent()
    }
}
