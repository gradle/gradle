/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures.versions

import org.gradle.api.JavaVersion
import org.gradle.internal.Factory
import org.gradle.util.internal.VersionNumber

import javax.annotation.Nullable

import static org.junit.Assume.assumeTrue


/**
 * Android Gradle Plugin Versions.
 *
 * See UpdateAgpVersions.
 */
class AndroidGradlePluginVersions {

    // TODO: This property appears to have been removed.
    // We probably don't need to set this anymore. See:
    // https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:build-system/gradle-core/src/main/java/com/android/build/gradle/options/ReplacedOption.kt;l=54-59
    public static final String OVERRIDE_VERSION_CHECK = '-Dcom.android.build.gradle.overrideVersionCheck=true'

    private static final VersionNumber AGP_8_0 = VersionNumber.parse('8.0.0')
    private static final VersionNumber AGP_7_0 = VersionNumber.parse('7.0.0')
    private static final VersionNumber AGP_7_3 = VersionNumber.parse('7.3.0')
    private static final VersionNumber KOTLIN_1_6_20 = VersionNumber.parse('1.6.20')

    private final Factory<Properties> propertiesFactory
    private Properties properties

    AndroidGradlePluginVersions() {
        this(new ClasspathVersionSource("agp-versions.properties", AndroidGradlePluginVersions.classLoader))
    }

    private AndroidGradlePluginVersions(Factory<Properties> propertiesFactory) {
        this.propertiesFactory = propertiesFactory
    }

    List<String> getLatests() {
        return getVersionList("latests")
    }

    String getLatest() {
        return latests.last()
    }

    List<String> getLatestsStable() {
        return latests
            .collect { VersionNumber.parse(it) }
            .findAll { it.baseVersion == it }
            .collect { it.toString() }
    }

    String getLatestStable() {
        return latestsStable.last()
    }

    List<String> getLatestsStableOrRC() {
        return latests.findAll {
            def lowerCaseVersion = it.toLowerCase(Locale.US)
            !lowerCaseVersion.contains('-alpha') && !(lowerCaseVersion.contains('-beta'))
        }
    }

    String getLatestStableOrRC() {
        return latestsStableOrRC.last()
    }

    List<String> getNightlies() {
        return getVersionList("nightlyVersion")
    }

    List<String> getLatestsPlusNightly() {
        return [latests, nightlies].flatten() as List<String>
    }

    boolean isAgpNightly(String agpVersion) {
        return agpVersion in nightlies
    }

    /**
     * Determines if the provided version is older than the most recent tested stable.
     */
    boolean isOld(String agpVersion) {
        VersionNumber.parse(agpVersion) < VersionNumber.parse(latestStable)
    }

    File createAgpNightlyRepositoryInitScript() {
        File mirrors = File.createTempFile("mirrors", ".gradle")
        mirrors.deleteOnExit()
        mirrors << agpNightlyRepositoryInitScript
        return mirrors
    }

    private String getAgpNightlyRepositoryInitScript() {
        return """
            beforeSettings { settings ->
                settings.pluginManagement.repositories {
                    $agpNightlyRepositoryDeclaration
                    gradlePluginPortal()
                }
            }
            allprojects {
                buildscript {
                    repositories {
                        $agpNightlyRepositoryDeclaration
                    }
                }
                repositories {
                    $agpNightlyRepositoryDeclaration
                }
            }
        """
    }

    private String getAgpNightlyRepositoryDeclaration() {
        def nightlyBuildId = getVersionList("nightlyBuildId").first()
        return """
            maven {
                name = 'agp-nightly-build-$nightlyBuildId'
                url = 'https://androidx.dev/studio/builds/$nightlyBuildId/artifacts/artifacts/repository/'
            }
        """
    }

    private List<String> getVersionList(String name) {
        def versionList = loadedProperties().getProperty(name)
        return (versionList == null || versionList.empty) ? [] : versionList.split(",")
    }

    private Properties loadedProperties() {
        if (properties == null) {
            properties = propertiesFactory.create()
        }
        return properties
    }

    @Nullable
    String getMinimumGradleBaseVersionFor(String agpVersion) {
        if (VersionNumber.parse(agpVersion) >= AGP_7_3) {
            return '7.4'
        }
        return null
    }

    static void assumeCurrentJavaVersionIsSupportedBy(String agpVersion) {
        VersionNumber agpVersionNumber = VersionNumber.parse(agpVersion)
        JavaVersion current = JavaVersion.current()
        JavaVersion mini = getMinimumJavaVersionFor(agpVersionNumber)
        assumeTrue("AGP $agpVersion minimum supported Java version is $mini, current is $current", current >= mini)
    }

    static JavaVersion getMinimumJavaVersionFor(String agpVersion) {
        return getMinimumJavaVersionFor(VersionNumber.parse(agpVersion))
    }

    static String getBuildToolsVersionFor(String agpVersion) {
        VersionNumber version = VersionNumber.parse(agpVersion)

        if (version < VersionNumber.parse("8.1")) {
            return "30.0.3"
        } else if (version < VersionNumber.parse("8.2")) {
            return "33.0.1"
        } else if (version < VersionNumber.parse("8.8")) {
            return "34.0.0"
        }

        return "35.0.0"
    }

    static JavaVersion getMinimumJavaVersionFor(VersionNumber agpVersion) {
        return JavaVersion.VERSION_17
    }

    static void assumeAgpSupportsCurrentJavaVersionAndKotlinVersion(String agpVersion, String kotlinVersion) {
        assumeCurrentJavaVersionIsSupportedBy(agpVersion)
        assumeAgpSupportsKotlinVersion(agpVersion, kotlinVersion)
    }

    private static void assumeAgpSupportsKotlinVersion(String agpVersion, String kotlinVersion) {
        VersionNumber agpVersionNumber = VersionNumber.parse(agpVersion)
        VersionNumber kotlinVersionNumber = VersionNumber.parse(kotlinVersion)
        def minimalSupportedKotlinVersion = getMinimumSupportedKotlinVersionFor(agpVersionNumber)
        if (minimalSupportedKotlinVersion != null) {
            assumeTrue("AGP $agpVersion minimal supported Kotlin version is $minimalSupportedKotlinVersion, current is $kotlinVersion", kotlinVersionNumber >= minimalSupportedKotlinVersion)
        }
    }

    private static VersionNumber getMinimumSupportedKotlinVersionFor(VersionNumber agpVersion) {
        return agpVersion.baseVersion < AGP_7_3
            ? null
            : KOTLIN_1_6_20
    }
}
