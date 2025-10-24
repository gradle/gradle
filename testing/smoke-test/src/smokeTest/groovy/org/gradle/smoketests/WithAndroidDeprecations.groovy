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
import groovy.transform.TupleConstructor
import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber

import java.util.function.Consumer

@SelfType(BaseDeprecations)
trait WithAndroidDeprecations {

    private static final VersionNumber AGP_8_11 = VersionNumber.parse("8.11")

    @TupleConstructor
    private static class IsPropertyInfo {
        String name
        String existing
        String replacement
        String location
    }

    private static final List<IsPropertyInfo> IS_PROPERTIES = [
        new IsPropertyInfo("crunchPngs", "isCrunchPngs", "getCrunchPngs", "com.android.build.gradle.internal.dsl.BuildType\$AgpDecorated"),
        new IsPropertyInfo("useProguard", "isUseProguard", "getUseProguard", "com.android.build.gradle.internal.dsl.BuildType"),
        new IsPropertyInfo("wearAppUnbundled", "isWearAppUnbundled", "getWearAppUnbundled", "com.android.build.api.variant.impl.ApplicationVariantImpl"),
    ]

    private void expectIsPropertyDeprecationWarningsUsing(Consumer<String> deprecationFunction) {
        for (def prop : IS_PROPERTIES) {
            deprecationFunction.accept(
                "Declaring '${prop.name}' as a property using an 'is-' method with a Boolean type on ${prop.location} has been deprecated. " +
                    "Starting with Gradle 10, this property will no longer be treated like a property. " +
                    "The combination of method name and return type is not consistent with Java Bean property rules. " +
                    "Add a method named '${prop.replacement}' with the same behavior and mark the old one with @Deprecated, or change the type of '${prop.location}.${prop.existing}' (and the setter) to 'boolean'. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#groovy_boolean_properties")
        }
    }

    void expectIsPropertyDeprecationWarnings(String agpVersion) {
        expectIsPropertyDeprecationWarningsUsing { message ->
            runner.expectLegacyDeprecationWarningIf(
                VersionNumber.parse(agpVersion).baseVersion < AGP_8_11,
                message
            )
        }
    }

    void maybeExpectIsPropertyDeprecationWarnings(String agpVersion) {
        expectIsPropertyDeprecationWarningsUsing { message ->
            runner.maybeExpectLegacyDeprecationWarningIf(
                VersionNumber.parse(agpVersion).baseVersion < AGP_8_11,
                message
            )
        }
    }

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
