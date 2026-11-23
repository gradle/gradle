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
import org.gradle.integtests.fixtures.android.AndroidHome
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.internal.VersionNumber

import java.util.zip.ZipFile

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

    /**
     * The configurations of the library whose primary variant has no artifacts, and which the app would select
     * with the {@code CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS} feature preview.
     */
    private static final List<String> LIBRARY_CONFIGURATIONS_WITHOUT_ARTIFACTS = ['debugRuntimeElements', 'debugApiElements']

    private static final String APP_ACTIVITY_CLASS = 'Lorg/gradle/android/example/app/AppActivity;'
    private static final String LIBRARY_ACTIVITY_CLASS = 'Lorg/gradle/android/example/library/LibraryActivity;'

    def setup() {
        AndroidHome.assertIsSet()
    }

    def "android library and application APK assembly (agp=#agpVersion, ide=#ide)"() {
        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def abiChange = androidLibraryAndApplicationBuild(agpVersion)
        def expectSetVisible = VersionNumber.parse(agpVersion).baseVersion < VersionNumber.parse("9.6.0")

        and:
        def runner = agpRunner(agpVersion,
            'assembleDebug',
            'testDebugUnitTest',
            "-Pandroid.injected.invoked.from.ide=$ide"
        )
        when: 'first build'
        def result = runner
            .deprecations(AndroidDeprecations) {
                expectProjectDependencyNotationDeprecationIf(VersionNumber.parse(agpVersion).baseVersion < VersionNumber.parse("9.3.0"))
                expectSetVisibleDeprecationIf(expectSetVisible)
                expectArtifactlessPrimaryVariantDeprecations(':library', LIBRARY_CONFIGURATIONS_WITHOUT_ARTIFACTS)
            }
            .build()

        then:
        result.task(':app:compileDebugJavaWithJavac').outcome == TaskOutcome.SUCCESS
        result.task(':library:assembleDebug').outcome == TaskOutcome.SUCCESS
        result.task(':app:assembleDebug').outcome == TaskOutcome.SUCCESS

        and:
        assert !result.output.contains(JAVA_COMPILE_DEPRECATION_MESSAGE)

        and:
        LIBRARY_ACTIVITY_CLASS in classesInApk(agpVersion)

        and:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateStored()
        }

        when: 'up-to-date build'
        result = runner
            .deprecations(AndroidDeprecations) {
                expectProjectDependencyNotationDeprecationIf(GradleContextualExecuter.isNotConfigCache() && VersionNumber.parse(agpVersion).baseVersion < VersionNumber.parse("9.3.0"))
                expectSetVisibleDeprecationIf(expectSetVisible && GradleContextualExecuter.isNotConfigCache())
                expectArtifactlessPrimaryVariantDeprecationsIf(GradleContextualExecuter.isNotConfigCache(), ':library', LIBRARY_CONFIGURATIONS_WITHOUT_ARTIFACTS)
            }
            .build()

        then:
        result.task(':app:compileDebugJavaWithJavac').outcome == TaskOutcome.UP_TO_DATE
        result.task(':library:assembleDebug').outcome == TaskOutcome.UP_TO_DATE
        result.task(':app:assembleDebug').outcome == TaskOutcome.UP_TO_DATE
        result.task(':app:processDebugManifest').outcome == TaskOutcome.UP_TO_DATE

        and:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateLoaded()
        }

        when: 'abi change on library'
        abiChange.run()
        result = runner
            .deprecations(AndroidDeprecations) {
                expectProjectDependencyNotationDeprecationIf(GradleContextualExecuter.isNotConfigCache() && VersionNumber.parse(agpVersion).baseVersion < VersionNumber.parse("9.3.0"))
                expectSetVisibleDeprecationIf(expectSetVisible && GradleContextualExecuter.isNotConfigCache())
                expectArtifactlessPrimaryVariantDeprecationsIf(GradleContextualExecuter.isNotConfigCache(), ':library', LIBRARY_CONFIGURATIONS_WITHOUT_ARTIFACTS)
            }
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
        agpRunner(agpVersion, 'clean')
            .deprecations(AndroidDeprecations) {
                expectProjectDependencyNotationDeprecationIf(VersionNumber.parse(agpVersion).baseVersion < VersionNumber.parse("9.3.0"))
                expectSetVisibleDeprecationIf(expectSetVisible)
            }
            .build()
        result = runner
            .deprecations(AndroidDeprecations) {
                expectProjectDependencyNotationDeprecationIf(GradleContextualExecuter.isNotConfigCache() && VersionNumber.parse(agpVersion).baseVersion < VersionNumber.parse("9.3.0"))
                expectSetVisibleDeprecationIf(expectSetVisible && GradleContextualExecuter.isNotConfigCache())
                expectArtifactlessPrimaryVariantDeprecationsIf(GradleContextualExecuter.isNotConfigCache(), ':library', LIBRARY_CONFIGURATIONS_WITHOUT_ARTIFACTS)
            }.build()

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

    def "android application APK lacks library classes when artifactless primary variants are considered (agp=#agpVersion)"() {
        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        androidLibraryAndApplicationBuild(agpVersion)
        file('settings.gradle') << 'enableFeaturePreview("CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS")\n'

        when:
        def result = agpRunner(agpVersion, 'assembleDebug')
            .deprecations(AndroidDeprecations) {
                expectProjectDependencyNotationDeprecationIf(VersionNumber.parse(agpVersion).baseVersion < VersionNumber.parse("9.3.0"))
                expectSetVisibleDeprecationIf(VersionNumber.parse(agpVersion).baseVersion < VersionNumber.parse("9.6.0"))
            }
            .build()

        then:
        result.task(':app:assembleDebug').outcome == TaskOutcome.SUCCESS
        LIBRARY_CONFIGURATIONS_WITHOUT_ARTIFACTS.every { configuration ->
            result.output.contains("Selecting artifacts from project ':library' selected variant 'configuration ':library:${configuration}'', which has no artifacts.")
        }

        and:
        // The app selects the library's primary variants, which have no artifacts, so its APK silently lacks the library's classes
        def classes = classesInApk(agpVersion)
        APP_ACTIVITY_CLASS in classes
        !(LIBRARY_ACTIVITY_CLASS in classes)

        where:
        agpVersion << TestedVersions.androidGradle.toList()
    }

    /**
     * Returns the descriptors of the classes defined in the app's debug APK, as listed by the SDK's {@code dexdump}.
     */
    private Set<String> classesInApk(String agpVersion) {
        def dexdump = new File(AndroidHome.get(), "build-tools/${AGP_VERSIONS.getBuildToolsVersionFor(agpVersion)}/${OperatingSystem.current().getExecutableName('dexdump')}")
        def dexDir = file('build/apk-dex').createDir()
        new ZipFile(file('app/build/outputs/apk/debug/app-debug.apk')).withCloseable { apk ->
            apk.entries().findAll { it.name ==~ /classes\d*\.dex/ }.each { entry ->
                new File(dexDir, entry.name).bytes = apk.getInputStream(entry).bytes
            }
        }
        dexDir.listFiles().collectMany { dex ->
            def output = [dexdump.absolutePath, dex.absolutePath].execute().text
            (output =~ /Class descriptor\s*:\s*'([^']+)'/).collect { it[1] }
        } as Set<String>
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
            pluginManagement {
                repositories {
                    ${googleRepository()}
                    ${mavenCentralRepository()}
                    ${gradlePluginRepositoryDefinition()}
                }
            }
            dependencyResolutionManagement {
                ${googleRepository()}
                ${mavenCentralRepository()}
            }
            include ':${app}'
            include ':${library}'
        """

        file('build.gradle') << ""

        def appBuildFile = file("${app}/build.gradle")
        appBuildFile << """
            plugins {
                id 'com.android.application' version '${agpVersion}'
            }

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
            plugins {
                id 'com.android.library' version '${agpVersion}'
            }
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

        """
            android {
                compileSdk = 30
                buildToolsVersion = "${AGP_VERSIONS.getBuildToolsVersionFor(agpVersion)}"

                namespace = "${appPackage}"
                defaultConfig {
                    minSdk = 22
                    targetSdk = 26
                    versionCode = 1
                    versionName = "1.0"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.${targetJvm.name()}
                    targetCompatibility = JavaVersion.${targetJvm.name()}
                }
                buildTypes {
                    release {
                        minifyEnabled = false
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
                    compileSdk = 24
                    buildToolsVersion = '${AGP_VERSIONS.getBuildToolsVersionFor(version)}'
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
                    ${gradlePluginRepositoryDefinition()}
                    ${googleRepositoryDefinition()}
                }
                resolutionStrategy.eachPlugin {
                    if (pluginRequest.id.id.startsWith('com.android')) {
                        useModule('com.android.tools.build:gradle:${version}')
                    }
                }
            }
        """
        validatePlugins {
            alwaysPasses()
        }
    }
}
