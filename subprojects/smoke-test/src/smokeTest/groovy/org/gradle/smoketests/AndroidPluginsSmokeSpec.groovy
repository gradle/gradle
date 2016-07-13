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

import org.gradle.testkit.runner.TaskOutcome

/**
 * For these tests to run you need to set ANDROID_HOME to your Android SDK directory
 */
class AndroidPluginsSmokeSpec extends AbstractSmokeSpec {

    def "android application plugin"() {
        given:

        file('src/main/res/values/strings.xml') << '''<?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">Android Gradle</string>
            </resources>'''.stripIndent()

        file('src/main/java/org/hello/HelloActivity.java') << '''
            package org.hello;

            import org.joda.time.LocalTime;

            import android.app.Activity;
            import android.os.Bundle;
            import android.widget.TextView;

            public class HelloActivity extends Activity {

                @Override
                public void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    setContentView(R.layout.hello_layout);
                }

                @Override
                public void onStart() {
                    super.onStart();
                    LocalTime currentTime = new LocalTime();
                    TextView textView = (TextView) findViewById(R.id.text_view);
                    textView.setText("The current local time is: " + currentTime);
                }

            }'''.stripIndent()

        file('src/main/res/layout/hello_layout.xml') << '''<?xml version="1.0" encoding="utf-8"?>
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

        file('src/main/AndroidManifest.xml') << '''<?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="org.hello">

                <application android:label="@string/app_name" >
                    <activity
                        android:name=".HelloActivity"
                        android:label="@string/app_name" >
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>

            </manifest>'''.stripIndent()

        buildFile << """
            buildscript {
                repositories {
                    jcenter()
                }


                dependencies {
                    classpath 'com.android.tools.build:gradle:2.2.0-alpha4'
                }
            }

            System.properties['com.android.build.gradle.overrideVersionCheck'] = 'true'

            apply plugin: 'com.android.application'

            android {
                compileSdkVersion 22
                buildToolsVersion "23.0.2"

                defaultConfig {
                    applicationId "org.gradle.android.myapplication"
                    minSdkVersion 22
                    targetSdkVersion 23
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

            repositories {
                jcenter()
            }

            dependencies {
                compile 'joda-time:joda-time:2.7'
            }
            """.stripIndent()

        when:
        def result = runner('build', '-x', 'lint').build()

        then:
        result.task(':assemble').outcome == TaskOutcome.SUCCESS
        result.task(':compileReleaseJavaWithJavac').outcome == TaskOutcome.SUCCESS
    }
}
