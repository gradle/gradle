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
import org.gradle.util.internal.VersionNumber

import static org.junit.Assume.assumeTrue
/**
 * Kotlin Gradle Plugin Versions.
 */
class KotlinGradlePluginVersions {

    // https://search.maven.org/search?q=g:org.jetbrains.kotlin%20AND%20a:kotlin-project&core=gav
    private static final List<String> LATEST_VERSIONS = [
        '1.3.72',
        '1.4.0', '1.4.10', '1.4.21', '1.4.31',
        '1.5.0', '1.5.31',
        '1.6.0', '1.6.10',
    ]

    private static final VersionNumber KOTLIN_GRADLE_PLUGIN_1_5_0_VERSION_NUMBER = VersionNumber.parse('1.5.0')

    List<String> getLatests() {
        return LATEST_VERSIONS
    }

    static void assumeCurrentJavaVersionIsSupportedBy(String kotlinPluginVersion) {
        VersionNumber kotlinPluginVersionNumber = VersionNumber.parse(kotlinPluginVersion)
        JavaVersion maxi = getMaximumJavaVersionFor(kotlinPluginVersionNumber)
        JavaVersion current = JavaVersion.current()
        if (maxi != null) {
            assumeTrue("Kotlin gradle plugin $kotlinPluginVersion maximum supported Java version is $maxi, current is $current", current <= maxi)
        }
    }

    private static JavaVersion getMaximumJavaVersionFor(VersionNumber kotlinPluginVersionNumber) {
        if (kotlinPluginVersionNumber.baseVersion < KOTLIN_GRADLE_PLUGIN_1_5_0_VERSION_NUMBER) {
            return JavaVersion.VERSION_11
        }
        return null
    }
}
