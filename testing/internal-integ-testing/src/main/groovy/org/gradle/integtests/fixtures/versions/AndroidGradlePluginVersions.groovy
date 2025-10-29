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

    public static final VersionNumber AGP_9_0 = VersionNumber.parse('9.0.0')

    private static Factory<Properties> propertiesFactory
    private static Properties properties
    private static Map<String, String> aapt2Versions = null

    AndroidGradlePluginVersions() {
        this(new ClasspathVersionSource("agp-versions.properties", AndroidGradlePluginVersions.classLoader))
    }

    private AndroidGradlePluginVersions(Factory<Properties> propertiesFactory) {
        this.propertiesFactory = propertiesFactory
    }

    static List<String> getLatests() {
        return getVersionList("latests")
    }

    static String getLatest() {
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

    @Nullable
    static String aapt2Version(String agpVersion) {
        if (aapt2Versions == null) {
            aapt2Versions = getVersionList("aapt2Versions")
                .collectEntries { String version ->
                    int index = version.lastIndexOf('-')
                    def agpVer = version.substring(0, index)
                    [(agpVer): version]
                }
        }
        String version = aapt2Versions.get(agpVersion)
        // latest dev has the same build number as the latest alpha
        if (version == null && agpVersion.contains("-dev")) {
            version = aapt2Versions.get(getLatest()).replaceAll(/-alpha\d+/, "-dev")
        }
        return version
    }

    private static String buildToolsVersion() {
        return loadedProperties().getProperty("buildToolsVersion")
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

    private static List<String> getVersionList(String name) {
        def versionList = loadedProperties().getProperty(name)
        return (versionList == null || versionList.empty) ? [] : versionList.split(",")
    }

    private static Properties loadedProperties() {
        if (properties == null) {
            properties = propertiesFactory.create()
        }
        return properties
    }

    @Nullable
    String getMinimumGradleBaseVersionFor(String agpVersion) {
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
        VersionNumber version = VersionNumber.parse(agpVersion).baseVersion

        if (version < AGP_9_0) {
            return "35.0.0"
        }

        return buildToolsVersion()
    }

    static JavaVersion getMinimumJavaVersionFor(VersionNumber agpVersion) {
        return JavaVersion.VERSION_17
    }
}
