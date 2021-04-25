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

import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.android.AndroidHome
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber

import static org.gradle.internal.reflect.validation.Severity.ERROR

/**
 * For these tests to run you need to set ANDROID_SDK_ROOT to your Android SDK directory
 *
 * https://developer.android.com/studio/releases/build-tools.html
 * https://developer.android.com/studio/releases/gradle-plugin.html
 * https://androidstudio.googleblog.com/
 *
 */
class AndroidPluginsSmokeTest extends AbstractPluginValidatingSmokeTest implements ValidationMessageChecker {

    public static final String JAVA_COMPILE_DEPRECATION_MESSAGE = "Extending the JavaCompile task has been deprecated. This is scheduled to be removed in Gradle 7.0. Configure the task instead."

    def setup() {
        AndroidHome.assertIsSet()
    }

    @UnsupportedWithConfigurationCache
    def "can use sourceSets task with android library and application build (agp=#agpVersion, ide=#ide)"() {
        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        androidLibraryAndApplicationBuild(agpVersion)

        and:
        def runner = useAgpVersion(agpVersion, runner('sourceSets'))

        when:
        def result = runner.build()

        then:
        result.task(':app:sourceSets').outcome == TaskOutcome.SUCCESS
        result.task(':library:sourceSets').outcome == TaskOutcome.SUCCESS

        where:
        [agpVersion, ide] << [
            TestedVersions.androidGradle.toList(),
            [false, true]
        ].combinations()
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [AGP_4_0_ITERATION_MATCHER, AGP_4_1_ITERATION_MATCHER])
    def "android library and application APK assembly (agp=#agpVersion, ide=#ide)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def abiChange = androidLibraryAndApplicationBuild(agpVersion)

        and:
        def runner = useAgpVersion(agpVersion, runner(
            'assembleDebug',
            'testDebugUnitTest',
            'connectedDebugAndroidTest',
            "-Pandroid.injected.invoked.from.ide=$ide"
        ))

        when: 'first build'
        def result = runner.build()

        then:
        result.task(':app:compileDebugJavaWithJavac').outcome == TaskOutcome.SUCCESS
        result.task(':library:assembleDebug').outcome == TaskOutcome.SUCCESS
        result.task(':app:assembleDebug').outcome == TaskOutcome.SUCCESS

        and:
        def agpBaseVersion = baseVersionNumberOf(agpVersion)
        def threeDotSixBaseVersion = baseVersionNumberOf("3.6.0")
        if (agpBaseVersion < threeDotSixBaseVersion) {
            assert result.output.contains(JAVA_COMPILE_DEPRECATION_MESSAGE)
        } else {
            assert !result.output.contains(JAVA_COMPILE_DEPRECATION_MESSAGE)
            if (agpBaseVersion == baseVersionNumberOf("3.6.4")) {
                expectDeprecationWarnings(
                        result,
                        "Internal API constructor DefaultDomainObjectSet(Class<T>) has been deprecated. " +
                                "This is scheduled to be removed in Gradle 8.0. Please use ObjectFactory.domainObjectSet(Class<T>) instead. " +
                                "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/custom_gradle_types.html#domainobjectset for more details.",
                        "The WorkerExecutor.submit() method has been deprecated. " +
                                "This is scheduled to be removed in Gradle 8.0. Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. " +
                                "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details."
                )
            } else if (agpVersion.startsWith('4.0.2')) {
                expectDeprecationWarnings(
                        result,
                        "The WorkerExecutor.submit() method has been deprecated. " +
                                "This is scheduled to be removed in Gradle 8.0. Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. " +
                                "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details."
                )
            } else if (agpVersion.startsWith('4.1')) {
                expectDeprecationWarnings(result, "The WorkerExecutor.submit() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 8.0. Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. " +
                    "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details.")
            } else {
                expectNoDeprecationWarnings(result)
            }
        }

        and:
        assertConfigurationCacheStateStored()

        when: 'up-to-date build'
        result = runner.build()

        then:
        result.task(':app:compileDebugJavaWithJavac').outcome == TaskOutcome.UP_TO_DATE
        result.task(':library:assembleDebug').outcome == TaskOutcome.UP_TO_DATE
        // In AGP 3.4 and 3.5 some of the dependencies of `:app:assembleDebug` are invalid and are thus forced to re-execute every time
        result.task(':app:assembleDebug').outcome == (VersionNumber.parse(agpVersion) < VersionNumber.parse("3.6.0")
            ? TaskOutcome.SUCCESS
            : TaskOutcome.UP_TO_DATE)
        result.task(':app:processDebugAndroidTestManifest').outcome == TaskOutcome.UP_TO_DATE

        and:
        assertConfigurationCacheStateLoaded()

        when: 'abi change on library'
        abiChange.run()
        result = runner.build()

        then: 'dependent sources are recompiled'
        result.task(':library:compileDebugJavaWithJavac').outcome == TaskOutcome.SUCCESS
        result.task(':app:compileDebugJavaWithJavac').outcome == TaskOutcome.SUCCESS
        result.task(':library:assembleDebug').outcome == TaskOutcome.SUCCESS
        result.task(':app:assembleDebug').outcome == TaskOutcome.SUCCESS

        and:
        assertConfigurationCacheStateLoaded()

        when: 'clean re-build'
        useAgpVersion(agpVersion, this.runner('clean')).build()
        result = runner.build()

        then:
        result.task(':app:compileDebugJavaWithJavac').outcome == TaskOutcome.SUCCESS
        result.task(':library:assembleDebug').outcome == TaskOutcome.SUCCESS
        result.task(':app:assembleDebug').outcome == TaskOutcome.SUCCESS

        and:
        assertConfigurationCacheStateLoaded()

        where:
        [agpVersion, ide] << [
            TestedVersions.androidGradle.toList(),
            [false, true]
        ].combinations()
    }

    private VersionNumber baseVersionNumberOf(String versionString) {
        VersionNumber.parse(versionString).baseVersion
    }

    /**
     * @return ABI change runnable
     */
    private Runnable androidLibraryAndApplicationBuild(String agpVersion) {

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
        file("${app}/src/test/java/ExampleTest.java") << '''
            import org.junit.Test;
            public class ExampleTest {
                @Test public void test() {}
            }
        '''.stripIndent()

        file('settings.gradle') << """
            include ':${app}'
            include ':${library}'
        """

        file('build.gradle') << buildscript(agpVersion) << """
            subprojects {
                ${googleRepository()}
                ${mavenCentralRepository()}

        /*
        jcenter() requried for AGP versions 4.* because:
        > Could not resolve all files for configuration ':android-kotlin-library:lintClassPath'.
           > Could not find org.jetbrains.trove4j:trove4j:20160824.
             Searched in the following locations:
               - https://dl.google.com/dl/android/maven2/org/jetbrains/trove4j/trove4j/20160824/trove4j-20160824.pom
               - https://repo.maven.apache.org/maven2/org/jetbrains/trove4j/trove4j/20160824/trove4j-20160824.pom
             Required by:
                 project :android-kotlin-library > com.android.tools.lint:lint-gradle:27.0.2 > com.android.tools:sdk-common:27.0.2
                 project :android-kotlin-library > com.android.tools.lint:lint-gradle:27.0.2 > com.android.tools.external.com-intellij:intellij-core:27.0.2

         Newer versions of this library are available in Maven Central: https://search.maven.org/artifact/org.jetbrains.intellij.deps/trove4j
         */
                ${jcenterRepository()}
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
                implementation project(':${library}')
                testImplementation 'junit:junit:4.12'
                androidTestImplementation project(":${library}")
            }
        """

        def libraryBuildFile = file("${library}/build.gradle")
        libraryBuildFile << """
            apply plugin: 'com.android.library'
        """
        libraryBuildFile << androidPluginConfiguration()
        libraryBuildFile << activityDependency()

        return {
            writeActivity(library, libPackage, libraryActivity, true)
        }
    }

    private static String activityDependency() {
        """
            dependencies {
                implementation 'joda-time:joda-time:2.7'
            }
        """
    }

    private static String buildscript(String pluginVersion) {
        """
            buildscript {
                ${mavenCentralRepository()}
                ${googleRepository()}
        /*
        jcenter() requried for AGP versions 4.* because:
        > Could not resolve all files for configuration ':android-kotlin-library:lintClassPath'.
           > Could not find org.jetbrains.trove4j:trove4j:20160824.
             Searched in the following locations:
               - https://dl.google.com/dl/android/maven2/org/jetbrains/trove4j/trove4j/20160824/trove4j-20160824.pom
               - https://repo.maven.apache.org/maven2/org/jetbrains/trove4j/trove4j/20160824/trove4j-20160824.pom
             Required by:
                 project :android-kotlin-library > com.android.tools.lint:lint-gradle:27.0.2 > com.android.tools:sdk-common:27.0.2
                 project :android-kotlin-library > com.android.tools.lint:lint-gradle:27.0.2 > com.android.tools.external.com-intellij:intellij-core:27.0.2

         Newer versions of this library are available in Maven Central: https://search.maven.org/artifact/org.jetbrains.intellij.deps/trove4j
         */
                ${jcenterRepository()}

                dependencies {
                    classpath 'com.android.tools.build:gradle:${pluginVersion}'
                }
            }
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

    @Override
    Map<String, Versions> getPluginsToValidate() {
        return [
            'com.android.application': TestedVersions.androidGradle,
            'com.android.library': TestedVersions.androidGradle,
            'com.android.test': TestedVersions.androidGradle,
            'com.android.reporting': TestedVersions.androidGradle,
            'com.android.dynamic-feature': TestedVersions.androidGradle,
        ]
    }

    @Override
    void configureValidation(String testedPluginId, String version) {
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(version)
        if (testedPluginId != 'com.android.reporting') {
            buildFile << """
                android {
                    compileSdkVersion 24
                    buildToolsVersion '${TestedVersions.androidTools}'
                }
            """
        }
        if (testedPluginId == 'com.android.test') {
            buildFile << """
                android {
                    targetProjectPath ':'
                }
            """
        }
        settingsFile << """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    google()
                }
                resolutionStrategy.eachPlugin {
                    if (pluginRequest.id.id.startsWith('com.android')) {
                        useModule('com.android.tools.build:gradle:${version}')
                    }
                }
            }
        """
        validatePlugins {
            boolean failsValidation = version.startsWith('4.2.0')
            if (failsValidation) {
                def pluginSuffix = testedPluginId.substring('com.android.'.length())
                def failingPlugins = ['com.android.internal.version-check', testedPluginId, 'com.android.internal.' + pluginSuffix]
                passing {
                    it !in failingPlugins
                }
                onPlugins(failingPlugins) {
                    failsWith([
                        (missingAnnotationMessage { type('TaskManager.ConfigAttrTask').property('consumable').missingInputOrOutput().includeLink() }): ERROR,
                        (missingAnnotationMessage { type('TaskManager.ConfigAttrTask').property('resolvable').missingInputOrOutput().includeLink() }): ERROR,
                    ])
                }
            } else {
                alwaysPasses()
            }
        }
    }
}
