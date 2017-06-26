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

package org.gradle.launcher.daemon

import org.gradle.api.internal.cache.HeapProportionalCacheSizer
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.soak.categories.SoakTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.experimental.categories.Category

@Category(SoakTest)
@Requires(TestPrecondition.NOT_WINDOWS)
class AndroidDexingSoakTest extends DaemonIntegrationSpec {
    public static final ANDROID_PLUGIN_VERSION = '2.3.1'
    public static final ANDROID_BUILD_TOOLS_VERSION = '25.0.0'

    int buildCount
    int maxRatio

    def "dexing remains performant with cache-reserved space"() {
        given:
        simpleAndroidApp()
        buildFile << """
            configurations.all {
                exclude group: 'com.android.support'
            }

            dependencies {
                compile "joda-time:joda-time:2.2"
            }

            import com.google.common.cache.CacheBuilder
            import com.google.common.cache.Cache
            import org.gradle.api.internal.cache.HeapProportionalCacheSizer

            // create a heap-proportional cache that we can fill up over multiple builds
            class State {
                static Cache<Object, Object> cache = CacheBuilder.newBuilder().maximumSize(new HeapProportionalCacheSizer().scaleCacheSize(5000000)).recordStats().build()
            }

            task gobbleCache {
                doLast {
                    1000000.times { count ->
                        State.cache.get(UUID.randomUUID()) { new Integer(count) }
                    }
                    println "stats: " + State.cache.stats().toString()
                }
            }

            tasks.withType(JavaCompile) { dependsOn gobbleCache }

            $captureTaskRunTimes
        """

        when:
        buildCount = 10
        maxRatio = 3
        generateSourceClasses(100, 300)

        then:
        dexRemainsPerformant()
    }

    def dexRemainsPerformant() {
        def runTimes = []
        buildCount.times { count ->
            file("inputs").deleteDir()

            executer.withStackTraceChecksDisabled()
            3.times { executer.expectDeprecationWarning() }
            executer.withBuildJvmOpts("-Xmx2560m", "-D${HeapProportionalCacheSizer.CACHE_RESERVED_SYSTEM_PROPERTY}=1536")
            args('-x', 'lint')
            succeeds('clean', 'transformClassesWithDexForRelease')
            result.assertTaskNotSkipped(':transformClassesWithDexForRelease')
            String runTime = file("build/runTimes/transformClassesWithDexForRelease").text
            runTimes.add Integer.valueOf(runTime)
        }

        println "dex run times: " + runTimes.collect { String.format("%.2fs", it/1000) }
        def minTime = runTimes.min()
        def lastTime = runTimes.last()

        assert lastTime/minTime < maxRatio : "The last dex run time (${lastTime}) is more than ${maxRatio} times the minimum (${minTime})"

        return true
    }

    void generateSourceClasses(int numSourceClasses, int numMethodsPerClass) {
        numSourceClasses.times { classNum ->
            def sourceFile = file("src/main/java/org/test/Class${classNum}.java")
            sourceFile << """
                package org.test;

                class Class${classNum} {
                    ${generateMethods(numMethodsPerClass)}
                }
            """
        }
    }

    String generateMethods(int numMethods) {
        StringBuilder sb = new StringBuilder()
        numMethods.times { methodNum ->
            sb.append("void doSomething${methodNum}() {\n")
            7.times {
                sb.append("System.out.println(\"doing something\");\n")
            }
            sb.append("}")
        }
        return sb.toString()
    }

    void simpleAndroidApp() {
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
                    classpath 'com.android.tools.build:gradle:$ANDROID_PLUGIN_VERSION'
                }
            }

            System.properties['com.android.build.gradle.overrideVersionCheck'] = 'true'

            apply plugin: 'com.android.application'

            android {
                compileSdkVersion 22
                buildToolsVersion "$ANDROID_BUILD_TOOLS_VERSION"

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

                dexOptions.preDexLibraries=false
            }

            repositories {
                jcenter()
            }
        """
    }

    String getCaptureTaskRunTimes() {
        return """
            tasks.all { task ->
                def startTime
                def endTime

                doFirst {
                    startTime = System.currentTimeMillis()
                }
                doLast {
                    endTime = System.currentTimeMillis()
                    file("\${buildDir}/runTimes").mkdirs()
                    file("\${buildDir}/runTimes/\${task.name}").text = "\${endTime - startTime}"
                }
            }
        """
    }
}
