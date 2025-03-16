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

import static org.junit.Assume.assumeTrue

/**
 * Kotlin Gradle Plugin Versions.
 *
 * See UpdateKotlinVersions.
 */
class KotlinGradlePluginVersions {

    static final List<String> LANGUAGE_VERSIONS = [
        "1.4",
        "1.5",
        "1.6",
        "1.7",
        "1.8",
        "1.9",
        "2.0",
    ]

    private static final LATEST_STABLE_OR_RC_MINIMUM_LANGUAGE_VERSION = VersionNumber.parse("1.6")

    private final Factory<Properties> propertiesFactory
    private Properties properties

    KotlinGradlePluginVersions() {
        this(new ClasspathVersionSource("kotlin-versions.properties", KotlinGradlePluginVersions.classLoader))
    }

    private KotlinGradlePluginVersions(Factory<Properties> propertiesFactory) {
        this.propertiesFactory = propertiesFactory
    }

    private Properties loadedProperties() {
        if (properties == null) {
            properties = propertiesFactory.create()
        }
        return properties
    }


    List<String> getLatests() {
        def versionList = loadedProperties().getProperty("latests")
        return (versionList == null || versionList.empty) ? [] : versionList.split(",")
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
            !lowerCaseVersion.contains('-m') && !(lowerCaseVersion.contains('-beta'))
        }
    }

    String getLatestStableOrRC() {
        return latestsStableOrRC.last()
    }

    /**
     * Determines if the provided version is older than the most recent tested stable.
     */
    boolean isOld(String kgpVersion) {
        isOld(VersionNumber.parse(kgpVersion))
    }

    boolean isOld(VersionNumber kgpVersion) {
        kgpVersion < VersionNumber.parse(latestStable)
    }

    List<String> languageVersionsSupportedByLatestStableOrRc() {
        return LANGUAGE_VERSIONS.findAll { VersionNumber.parse(it) >= LATEST_STABLE_OR_RC_MINIMUM_LANGUAGE_VERSION }
    }

    static final VersionNumber KOTLIN_1_6_21 = VersionNumber.parse('1.6.21')
    static final VersionNumber KOTLIN_1_8_0 = VersionNumber.parse('1.8.0')
    static final VersionNumber KOTLIN_1_9_0 = VersionNumber.parse('1.9.0')
    static final VersionNumber KOTLIN_1_9_20 = VersionNumber.parse('1.9.20')
    static final VersionNumber KOTLIN_2_0_0 = VersionNumber.parse('2.0.0')
    static final VersionNumber KOTLIN_2_0_20 = VersionNumber.parse('2.0.20')
    static final VersionNumber KOTLIN_2_1_20 = VersionNumber.parse('2.1.20')

    static void assumeCurrentJavaVersionIsSupportedBy(String kotlinVersion) {
        assumeCurrentJavaVersionIsSupportedBy(VersionNumber.parse(kotlinVersion))
    }

    static void assumeCurrentJavaVersionIsSupportedBy(VersionNumber kotlinVersionNumber) {
        JavaVersion current = JavaVersion.current()
        JavaVersion mini = getMinimumJavaVersionFor(kotlinVersionNumber)
        assumeTrue("KGP $kotlinVersionNumber minimum supported Java version is $mini, current is $current", current >= mini)
        JavaVersion maxi = getMaximumJavaVersionFor(kotlinVersionNumber)
        if (maxi != null) {
            assumeTrue("KGP $kotlinVersionNumber maximum supported Java version is $maxi, current is $current", current <= maxi)
        }
    }

    static boolean hasConfigurationCacheWarnings(VersionNumber kotlinVersion) {
        // CacheableTasksKt.isBuildCacheEnabledForKotlin(CacheableTasks.kt:22) is the culprit: https://github.com/JetBrains/kotlin/blob/v1.6.21/libraries/tools/kotlin-gradle-plugin/src/main/kotlin/org/jetbrains/kotlin/gradle/tasks/CacheableTasks.kt#L22
        return (KOTLIN_1_6_21 <= kotlinVersion && kotlinVersion < KOTLIN_1_8_0)
    }

    static JavaVersion getMinimumJavaVersionFor(VersionNumber kotlinVersion) {
        return JavaVersion.VERSION_1_8
    }

    private static JavaVersion getMaximumJavaVersionFor(VersionNumber kotlinVersion) {
        if (kotlinVersion.baseVersion < KOTLIN_1_8_0) {
            return JavaVersion.VERSION_18
        }
        if (kotlinVersion.baseVersion < KOTLIN_1_9_0) {
            return JavaVersion.VERSION_19
        }
        if (kotlinVersion.baseVersion < KOTLIN_1_9_20) {
            return JavaVersion.VERSION_20
        }
        // No baseVersion since the betas don't support Java 22
        if (kotlinVersion < KOTLIN_2_0_0) {
            return JavaVersion.VERSION_21
        }
        return null
    }
}
