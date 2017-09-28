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

import org.gradle.smoketests.fixtures.AndroidConfiguration
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.Requires
import spock.lang.Ignore

import static org.gradle.smoketests.fixtures.AndroidSmokeTestFixture.*

/**
 * For these tests to run you need to set ANDROID_HOME to your Android SDK directory
 */
class AndroidPluginsSmokeTest extends AbstractSmokeTest {

    def setup() {
        assertAndroidHomeSet()
    }

    def "android application plugin"() {
        given:
        setupAndroidApplication()

        when:
        def result = runner('androidDependencies', 'build', '-x', 'lint').build()

        then:
        result.task(':assemble').outcome == TaskOutcome.SUCCESS
        result.task(':compileReleaseJavaWithJavac').outcome == TaskOutcome.SUCCESS
    }

    def "android library plugin"() {
        given:
        setupAndroidLibrary()

        when:
        def result = runner('build', '-x', 'lint').build()

        then:
        result.task(':app:assemble').outcome == TaskOutcome.SUCCESS
        result.task(':library:assemble').outcome == TaskOutcome.SUCCESS
        result.task(':app:compileReleaseJavaWithJavac').outcome == TaskOutcome.SUCCESS
    }

    def "android unit tests"() {
        given:
        setupAndroidApplication()
        file('src/main/java/EmailValidator.java') << emailValidatorJavaSourceFile()
        file('src/test/java/EmailValidatorTest.java') << emailValidatorUnitTest()
        buildFile << unitTestSetup()

        when:
        def result = runner('test').build()

        then:
        result.task(':test').outcome == TaskOutcome.SUCCESS
    }

    // TODO: Requires automated set up and tear down of headless emulator
    @Ignore
    @Requires(adhoc = { !System.getenv().containsKey('CI') })
    def "android instrumented tests"() {
        given:
        setupAndroidApplication(AndroidConfiguration.BETA)
        file('src/androidTest/java/SampleTest.java') << instrumentedTest()
        buildFile << instrumentedTestSetup()

        when:
        def result = runner('connectedAndroidTest').forwardOutput().build()

        then:
        result.task(':connectedAndroidTest').outcome == TaskOutcome.SUCCESS
    }

    private writeActivity(String basedir, String packageName, String className) {
        String resourceName = className.toLowerCase()

        file("${basedir}/src/main/java/${packageName.replaceAll('\\.', '/')}/HelloActivity.java") << """
            package ${packageName};

            import org.joda.time.LocalTime;

            import android.app.Activity;
            import android.os.Bundle;
            import android.widget.TextView;

            public class HelloActivity extends Activity {

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

            }""".stripIndent()

        file("${basedir}/src/main/res/layout/${resourceName}_layout.xml") << '''<?xml version="1.0" encoding="utf-8"?>
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

    private void setupAndroidApplication(AndroidConfiguration androidConfiguration = AndroidConfiguration.STABLE) {
        def basedir='.'
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

        buildFile << buildscript(androidConfiguration.pluginVersion) << """
            apply plugin: 'com.android.application'

            ${googleRepository()}
            ${jcenterRepository()}

            android.defaultConfig.applicationId "org.gradle.android.myapplication"
        """.stripIndent() << androidPluginConfiguration(androidConfiguration) << activityDependency()
    }

    private void setupAndroidLibrary(AndroidConfiguration androidConfiguration = AndroidConfiguration.STABLE) {
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

        file('build.gradle') << buildscript(androidConfiguration.pluginVersion) << """
            subprojects {
                ${jcenterRepository()}
            }
        """

        file("${app}/build.gradle") << """
            apply plugin: 'com.android.application'

            android.defaultConfig.applicationId "org.gradle.android.myapplication"

        """.stripIndent() << androidPluginConfiguration(androidConfiguration) << activityDependency() <<
            """
            dependencies {
                compile project(':${library}')
            }
        """.stripIndent()

        file("${library}/build.gradle") << """
            apply plugin: 'com.android.library'
            """.stripIndent() << androidPluginConfiguration(androidConfiguration) << activityDependency()
    }
}
