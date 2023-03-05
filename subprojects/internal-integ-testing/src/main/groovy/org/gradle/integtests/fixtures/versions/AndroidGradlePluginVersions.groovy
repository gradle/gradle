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
 * If you need to iterate locally on changes to AGP sources:
 * - hardcode latest nightly to `4.0.0-dev`
 * - change the repository url in `AGP_NIGHTLY_REPOSITORY_DECLARATION` to `file:///path/to/agp-src/out/repo`
 * - run `./gradlew :publishAndroidGradleLocal` in `/path/to/agp-src/tools`
 */
class AndroidGradlePluginVersions {

    public static final String OVERRIDE_VERSION_CHECK = '-Dcom.android.build.gradle.overrideVersionCheck=true'

    private static final String AGP_NIGHTLY_REPOSITORY_DECLARATION = '''
        maven {
            name = 'agp-nightlies'
            url = 'https://repo.gradle.org/gradle/ext-snapshots-local/'
        }
    '''

    private static final String AGP_NIGHTLY_REPOSITORY_INIT_SCRIPT = """
        allprojects {
            buildscript {
                repositories {
                    $AGP_NIGHTLY_REPOSITORY_DECLARATION
                }
            }
            repositories {
                $AGP_NIGHTLY_REPOSITORY_DECLARATION
            }
        }
    """

    private static final VersionNumber AGP_8_0 = VersionNumber.parse('8.0.0')
    private static final VersionNumber AGP_7_0 = VersionNumber.parse('7.0.0')
    private static final VersionNumber AGP_7_3 = VersionNumber.parse('7.3.0')
    private static final VersionNumber KOTLIN_1_6_20 = VersionNumber.parse('1.6.20')

    static boolean isAgpNightly(String agpVersion) {
        return agpVersion.contains("-") && agpVersion.substring(agpVersion.indexOf("-") + 1).matches("^[0-9].*")
    }

    static File createAgpNightlyRepositoryInitScript() {
        File mirrors = File.createTempFile("mirrors", ".gradle")
        mirrors.deleteOnExit()
        mirrors << AGP_NIGHTLY_REPOSITORY_INIT_SCRIPT
        return mirrors
    }

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
        return getVersionList("nightly")
    }

    List<String> getLatestsPlusNightly() {
        return [latests, nightlies].flatten() as List<String>
    }

    List<String> getLatestsFromMinor(String lowerBound) {
        assert lowerBound.matches("^[0-9]+\\.[0-9]+\$")
        def withBound = (latests + lowerBound).sort()
        return withBound.subList(withBound.indexOf(lowerBound) + 1, withBound.size())
    }

    String getLatestOfMinor(String lowerBound) {
        return getLatestsFromMinor(lowerBound).first()
    }

    List<String> getLatestsFromMinorPlusNightly(String lowerBound) {
        return [getLatestsFromMinor(lowerBound), nightlies].flatten() as List<String>
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
        JavaVersion maxi = getMaximumJavaVersionFor(agpVersionNumber)
        if (maxi != null) {
            assumeTrue("AGP $agpVersion maximum supported Java version is $maxi, current is $current", current <= maxi)
        }
    }

    static JavaVersion getMinimumJavaVersionFor(String agpVersion) {
        return getMinimumJavaVersionFor(VersionNumber.parse(agpVersion))
    }

    static JavaVersion getMinimumJavaVersionFor(VersionNumber agpVersion) {
        if (agpVersion.baseVersion < AGP_7_0) {
            return JavaVersion.VERSION_1_8
        }
        if (agpVersion.baseVersion < AGP_8_0) {
            return JavaVersion.VERSION_11
        }
        return JavaVersion.VERSION_17
    }

    private static JavaVersion getMaximumJavaVersionFor(VersionNumber agpVersion) {
        // This is mainly to prevent running all AGP tests on too many java versions and reduce CI time
        if (agpVersion.baseVersion < AGP_7_0) {
            return JavaVersion.VERSION_11
        }
        return null
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
