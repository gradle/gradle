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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.android.AndroidHome
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.internal.VersionNumber

import static org.gradle.api.problems.Severity.ERROR
import static org.junit.Assume.assumeTrue

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
class AndroidPluginsSmokeTest extends AbstractPluginValidatingSmokeTest implements ValidationMessageChecker, RunnerFactory {

    public static final String JAVA_COMPILE_DEPRECATION_MESSAGE = "Extending the JavaCompile task has been deprecated. This is scheduled to be removed in Gradle 7.0. Configure the task instead."

    def setup() {
        AndroidHome.assertIsSet()
    }

    @Override
    SmokeTestGradleRunner runner(String... tasks) {
        def runner = super.runner(tasks)
        // TODO: AGP's ShaderCompile uses Task.project after the configuration barrier to compute inputs
        return runner.withJvmArguments(runner.jvmArguments + [
            // A workaround for this has been added to TaskExecutionAccessCheckers;
            // TODO once we remove it, uncomment the flag below or upgrade AGP
            // "-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true"
        ])
    }

    @UnsupportedWithConfigurationCache
    // See https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-master-dev/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/tasks/SourceSetsTask.java#40
    def "can use sourceSets task with android library and application build (agp=#agpVersion, ide=#ide)"() {
        given:
        // SourceSetsTask has been deprecated in 8.8 and will be removed in AGP 9.0
        assumeTrue(VersionNumber.parse(agpVersion).baseVersion < VersionNumber.parse("8.8.0"))

        and:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        androidLibraryAndApplicationBuild(agpVersion)

        and:
        def runner = agpRunner(agpVersion, 'sourceSets')

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

    def "android library and application APK assembly (agp=#agpVersion, ide=#ide)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def abiChange = androidLibraryAndApplicationBuild(agpVersion)

        and:
        def runner = agpRunner(agpVersion,
            'assembleDebug',
            'testDebugUnitTest',
            'connectedDebugAndroidTest',
            "-Pandroid.injected.invoked.from.ide=$ide"
        )

        when: 'first build'
        SantaTrackerConfigurationCacheWorkaround.beforeBuild(runner.projectDir, IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir)
        def result = runner
            .expectChangingPropertyValueAtExecutionTimeDeprecationWarning("systemProperties")
            .build()

        then:
        result.task(':app:compileDebugJavaWithJavac').outcome == TaskOutcome.SUCCESS
        result.task(':library:assembleDebug').outcome == TaskOutcome.SUCCESS
        result.task(':app:assembleDebug').outcome == TaskOutcome.SUCCESS

        and:
        assert !result.output.contains(JAVA_COMPILE_DEPRECATION_MESSAGE)

        and:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateStored()
        }

        when: 'up-to-date build'
        SantaTrackerConfigurationCacheWorkaround.beforeBuild(runner.projectDir, IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir)
        result = runner.build()

        then:
        result.task(':app:compileDebugJavaWithJavac').outcome == TaskOutcome.UP_TO_DATE
        result.task(':library:assembleDebug').outcome == TaskOutcome.UP_TO_DATE
        result.task(':app:assembleDebug').outcome == TaskOutcome.UP_TO_DATE
        result.task(':app:processDebugAndroidTestManifest').outcome == TaskOutcome.UP_TO_DATE

        and:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateLoaded()
        }

        when: 'abi change on library'
        abiChange.run()
        SantaTrackerConfigurationCacheWorkaround.beforeBuild(runner.projectDir, IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir)
        result = runner
            .expectChangingPropertyValueAtExecutionTimeDeprecationWarning("systemProperties")
            .build()

        then: 'dependent sources are recompiled'
        result.task(':library:compileDebugJavaWithJavac').outcome == TaskOutcome.SUCCESS
        result.task(':app:compileDebugJavaWithJavac').outcome == TaskOutcome.SUCCESS
        result.task(':library:assembleDebug').outcome == TaskOutcome.SUCCESS
        result.task(':app:assembleDebug').outcome == TaskOutcome.SUCCESS

        and:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateLoaded()
        }

        when: 'clean re-build'
        agpRunner(agpVersion, 'clean').build()
        SantaTrackerConfigurationCacheWorkaround.beforeBuild(runner.projectDir, IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir)
        result = runner
            .expectChangingPropertyValueAtExecutionTimeDeprecationWarning("systemProperties")
            .build()

        then:
        result.task(':app:compileDebugJavaWithJavac').outcome == TaskOutcome.SUCCESS
        result.task(':library:assembleDebug').outcome == TaskOutcome.SUCCESS
        result.task(':app:assembleDebug').outcome == TaskOutcome.SUCCESS

        and:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateLoaded()
        }

        where:
        [agpVersion, ide] << [
            TestedVersions.androidGradle.toList(),
            [false, true]
        ].combinations()
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
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            </manifest>""".stripIndent()

        writeActivity(app, appPackage, appActivity)
        file("${app}/src/main/java/UsesLibraryActivity.java") << """
            public class UsesLibraryActivity {
                public void consume(${libPackage}.${libraryActivity} activity) {
                }
            }
        """
        file("${app}/src/main/AndroidManifest.xml") << """<?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">

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
            }
        """

        def appBuildFile = file("${app}/build.gradle")
        appBuildFile << """
            apply plugin: 'com.android.application'

            android.defaultConfig.applicationId "org.gradle.android.myapplication"
        """
        appBuildFile << androidPluginConfiguration(appPackage, agpVersion)
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
        libraryBuildFile << androidPluginConfiguration(libPackage, agpVersion)
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

    def androidPluginConfiguration(String appPackage, String agpVersion) {

        JavaVersion targetJvm = JavaVersion.current()

        VersionNumber agp = VersionNumber.parse(agpVersion)
        if (agp < VersionNumber.parse("8.2.1") && JavaVersion.current() >= JavaVersion.VERSION_21) {
            // Bug in AGP that prevents targeting bytecode version > Java 8.
            // This only occurs when compiling with JDK > 21
            // See https://issuetracker.google.com/issues/294137077
            targetJvm = JavaVersion.VERSION_1_8
        }

        """
            android {
                compileSdkVersion 30
                buildToolsVersion "${TestedVersions.androidTools}"

                namespace "${appPackage}"
                defaultConfig {
                    minSdkVersion 22
                    targetSdkVersion 26
                    versionCode 1
                    versionName "1.0"
                }
                compileOptions {
                    sourceCompatibility JavaVersion.${targetJvm.name()}
                    targetCompatibility JavaVersion.${targetJvm.name()}
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
    protected List<String> getValidationExtraParameters(String version) {
        if (AGP_VERSIONS.isAgpNightly(version)) {
            def init = AGP_VERSIONS.createAgpNightlyRepositoryInitScript()
            return ["-I", init.canonicalPath]
        }
        return super.getValidationExtraParameters(version)
    }

    @Override
    void configureValidation(String testedPluginId, String version) {
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(version)
        if (testedPluginId != 'com.android.reporting') {
            buildFile << """
                android {
                    namespace = "org.gradle.android.example.app"
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
            boolean failsValidation = version.startsWith('4.2.')
            if (failsValidation) {
                def pluginSuffix = testedPluginId.substring('com.android.'.length())
                def failingPlugins = ['com.android.internal.version-check', testedPluginId, 'com.android.internal.' + pluginSuffix]
                passing {
                    it !in failingPlugins
                }
                onPlugins(failingPlugins) {
                    failsWith([
                        (missingAnnotationMessage { type('com.android.build.gradle.internal.TaskManager.ConfigAttrTask').property('consumable').missingInputOrOutput().includeLink() }): ERROR,
                        (missingAnnotationMessage { type('com.android.build.gradle.internal.TaskManager.ConfigAttrTask').property('resolvable').missingInputOrOutput().includeLink() }): ERROR,
                    ])
                }
            } else {
                alwaysPasses()
            }
        }
    }
}
