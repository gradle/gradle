/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.integtests.fixtures.android.AndroidHome
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.scripts.DefaultScriptFileResolver
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber

import java.util.jar.JarOutputStream

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

abstract class AndroidSantaTrackerSmokeTest extends AbstractAndroidSantaTrackerSmokeTest {
}

class AndroidSantaTrackerDeprecationSmokeTest extends AndroidSantaTrackerSmokeTest {
    def "check deprecation warnings produced by building Santa Tracker (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir)

        when:
        def result = runnerForLocation(checkoutDir, agpVersion, "assembleDebug")
            .maybeExpectLegacyDeprecationWarningIf(VersionNumber.parse(agpVersion) >= VersionNumber.parse("8.8.0"), "Retrieving attribute with a null key. This behavior has been deprecated. This will fail with an error in Gradle 10.0. Don't request attributes from attribute containers using null keys. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#null-attribute-lookup")
            .build()

        then:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateStored()
        }

        where:
        agpVersion << TestedVersions.androidGradle.versions
    }
}

class AndroidSantaTrackerIncrementalCompilationSmokeTest extends AndroidSantaTrackerSmokeTest {
    def "incremental Java compilation works for Santa Tracker (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir)

        and:
        def pathToClass = "com/google/android/apps/santatracker/tracker/ui/BottomSheetBehavior"
        def fileToChange = checkoutDir.file("tracker/src/main/java/${pathToClass}.java")
        def compiledClassFile = VersionNumber.parse(agpVersion).baseVersion >= VersionNumber.parse('8.3.0')
            ? checkoutDir.file("tracker/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes/${pathToClass}.class")
            : checkoutDir.file("tracker/build/intermediates/javac/debug/classes/${pathToClass}.class")

        when:
        SantaTrackerConfigurationCacheWorkaround.beforeBuild(checkoutDir, homeDir)
        def result = buildLocation(checkoutDir, agpVersion)
        def md5Before = compiledClassFile.md5Hash

        then:
        result.task(":tracker:compileDebugJavaWithJavac").outcome == SUCCESS
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateStored()
        }

        when:
        fileToChange.replace("computeCurrentVelocity(1000", "computeCurrentVelocity(2000")
        SantaTrackerConfigurationCacheWorkaround.beforeBuild(checkoutDir, homeDir)
        result = buildCachedLocation(checkoutDir, agpVersion)

        def md5After = compiledClassFile.md5Hash

        then:
        result.task(":tracker:compileDebugJavaWithJavac").outcome == SUCCESS
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateLoaded()
        }
        md5After != md5Before

        where:
        agpVersion << TestedVersions.androidGradle.versions
    }
}

class AndroidSantaTrackerLintSmokeTest extends AndroidSantaTrackerSmokeTest {
    def "can lint Santa-Tracker (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir)

        when:
        def runner = runnerForLocation(
            checkoutDir, agpVersion,
            "common:lintDebug", "playgames:lintDebug", "doodles-lib:lintDebug"
        )
        SantaTrackerConfigurationCacheWorkaround.beforeBuild(checkoutDir, homeDir)
        // Use --continue so that a deterministic set of tasks runs when some tasks fail
        runner.withArguments(runner.arguments + "--continue")
        def result = runner
            .maybeExpectLegacyDeprecationWarningIf(VersionNumber.parse(agpVersion) >= VersionNumber.parse("8.8.0"), "Retrieving attribute with a null key. This behavior has been deprecated. This will fail with an error in Gradle 10.0. Don't request attributes from attribute containers using null keys. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#null-attribute-lookup")
            .buildAndFail()

        then:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateStored()
        }
        result.output.contains("Lint found errors in the project; aborting build.")

        when:
        runner = runnerForLocation(
            checkoutDir, agpVersion,
            "common:lintDebug", "playgames:lintDebug", "doodles-lib:lintDebug"
        )
        SantaTrackerConfigurationCacheWorkaround.beforeBuild(checkoutDir, homeDir)
        result = runner.withArguments(runner.arguments + "--continue")
            .maybeExpectLegacyDeprecationWarningIf(VersionNumber.parse(agpVersion) >= VersionNumber.parse("8.8.0"), "Retrieving attribute with a null key. This behavior has been deprecated. This will fail with an error in Gradle 10.0. Don't request attributes from attribute containers using null keys. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#null-attribute-lookup")
            .buildAndFail()

        then:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateLoaded()
        }
        result.output.contains("Lint found errors in the project; aborting build.")

        where:
        agpVersion << TestedVersions.androidGradle.versions
    }
}

class SantaTrackerConfigurationCacheWorkaround {
    static void beforeBuild(File checkoutDir, File gradleHome) {
        // Workaround for Android Gradle plugin checking for the presence of these directories at configuration time,
        // which invalidates configuration cache if their presence changes. Create these directories before the first build.
        // See: https://android.googlesource.com/platform/tools/base/+/studio-master-dev/build-system/gradle-core/src/main/java/com/android/build/gradle/tasks/ShaderCompile.java#120
        // TODO: remove this once AGP stops checking for the existence of these directories at configuration time
        checkoutDir.listFiles().findAll { isGradleProjectDir(it) }.each {
            new File(it, "build/intermediates/merged_shaders/debug/out").mkdirs()
            new File(it, "build/intermediates/merged_shaders/debugUnitTest/out").mkdirs()
            new File(it, "build/intermediates/merged_shaders/debugAndroidTest/out").mkdirs()
            new File(it, "build/intermediates/merged_shaders/release/out").mkdirs()
            new File(it, "build/intermediates/merged_shaders/releaseAndroidTest/out").mkdirs()
        }
        File androidAnalyticsSetting = new File(System.getProperty("user.home"), ".android/analytics.settings")
        if (!androidAnalyticsSetting.exists()) {
            androidAnalyticsSetting.parentFile.mkdirs()
            androidAnalyticsSetting.createNewFile()
        }
        File androidCacheDir = new File(System.getProperty("user.home"), ".android/cache")
        if (!androidCacheDir.exists()) {
            androidCacheDir.mkdirs()
        }
        File androidLock = new File(gradleHome, "android.lock")
        if (!androidLock.exists()) {
            androidLock.parentFile.mkdirs()
            androidLock.createNewFile()
        }
        def androidFakeDependency = new File(gradleHome, "android/FakeDependency.jar")
        if (!androidFakeDependency.exists()) {
            androidFakeDependency.parentFile.mkdirs()
            new JarOutputStream(new FileOutputStream(androidFakeDependency)).close()
        }
        File androidSdkRoot = new File(AndroidHome.get())
        File androidSdkPackageXml = new File(androidSdkRoot, "platform-tools/package.xml")
        if (!androidSdkPackageXml.exists()) {
            androidSdkPackageXml.parentFile.mkdirs()
            androidSdkPackageXml.createNewFile()
        }
    }

    private static boolean isGradleProjectDir(File candidate) {
        candidate.isDirectory() && hasGradleScript(candidate)
    }

    private static boolean hasGradleScript(File dir) {
        !new DefaultScriptFileResolver().findScriptsIn(dir).isEmpty()
    }
}
