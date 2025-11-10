/*
 * Copyright 2022 the original author or authors.
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

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber

@SelfType(BaseDeprecations)
trait WithAndroidDeprecations {

    void expectMultiStringNotationDeprecationIf(String agpVersion, boolean condition) {
        if (condition) {
            expectMultiStringNotationDeprecation(agpVersion)
        }
    }

    void expectMultiStringNotationDeprecation(String agpVersion) {
        if (VersionNumber.parse(agpVersion).baseVersion >= AndroidGradlePluginVersions.AGP_9_0) {
            return
        }
        String lintVersion = agpVersion.replaceAll("^8.", "31.")
        String aapt2Version = AndroidGradlePluginVersions.aapt2Version(agpVersion)
        String platform
        if (OperatingSystem.current().isWindows()) {
            platform = "windows"
        } else if (OperatingSystem.current().isLinux()) {
            platform = "linux"
        } else if (OperatingSystem.current().isMacOsX()) {
            platform = "osx"
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: ${OperatingSystem.current().name}")
        }

        if (lintVersion != null && aapt2Version != null) { // We don't test deprecations on older AGP versions.
            // See https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:build-system/gradle-core/src/main/java/com/android/build/gradle/internal/lint/AndroidLintInputs.kt;l=2849?q=AndroidLintInputs
            runner.expectLegacyDeprecationWarning(
                "Declaring dependencies using multi-string notation has been deprecated. This will fail with an error in Gradle 10. Please use single-string notation instead: \"com.android.tools.lint:lint-gradle:$lintVersion\". Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#dependency_multi_string_notation"
            )
            // See https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:build-system/gradle-core/src/main/java/com/android/build/gradle/internal/res/Aapt2FromMaven.kt;l=138?q=Aapt2FromMaven
            runner.expectLegacyDeprecationWarning(
                "Declaring dependencies using multi-string notation has been deprecated. This will fail with an error in Gradle 10. Please use single-string notation instead: \"com.android.tools.build:aapt2:$aapt2Version:$platform\". Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#dependency_multi_string_notation"
            )
        }
    }

}
