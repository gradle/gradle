/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.jvm

import org.gradle.util.GradleVersion

import java.util.regex.Pattern

/**
 * Contains common deprecation warnings that are emitted by Gradle which
 * are related to java version compatibility
 */
class SupportedJavaVersionsExpectations {

    static String getExpectedDaemonDeprecationWarning() {
        getExpectedDaemonDeprecationWarning(GradleVersion.current())
    }

    static String getExpectedDaemonDeprecationWarning(GradleVersion gradleVersion) {
        getExpectedDaemonDeprecationWarning(
            gradleVersion,
            SupportedJavaVersions.FUTURE_MINIMUM_DAEMON_JAVA_VERSION
        )
    }

    static String getExpectedDaemonDeprecationWarning(GradleVersion gradleVersion, int futureMinimumDaemonJavaVersion) {
        int currentMajorGradleVersion = gradleVersion.getMajorVersion()

        "Executing Gradle on JVM versions ${futureMinimumDaemonJavaVersion - 1} and lower has been deprecated. " +
            "This will fail with an error in Gradle ${currentMajorGradleVersion + 1}.0. " +
            "Use JVM ${futureMinimumDaemonJavaVersion} or greater to execute Gradle. " +
            "Projects can continue to use older JVM versions via toolchains. " +
            "Consult the upgrading guide for further information: " +
            "https://docs.gradle.org/${gradleVersion.version}/userguide/upgrading_version_${currentMajorGradleVersion}.html#minimum_daemon_jvm_version"
    }

    static String getExpectedDaemonIncompatibilityErrorMessage(Jvm jdk) {
        getExpectedDaemonIncompatibilityErrorMessage(jdk.javaVersionMajor)
    }

    static String getExpectedDaemonIncompatibilityErrorMessage(int majorVersion) {
        getExpectedDaemonIncompatibilityErrorMessage(majorVersion as String)
    }

    static String getExpectedDaemonIncompatibilityErrorMessage(String majorVersion) {
        getExpectedIncompatibilityErrorMessage(SupportedJavaVersions.MINIMUM_DAEMON_JAVA_VERSION, majorVersion)
    }

    static String getExpectedLauncherIncompatibilityErrorMessage(Jvm jdk) {
        getExpectedIncompatibilityErrorMessage(SupportedJavaVersions.MINIMUM_WRAPPER_JAVA_VERSION, jdk.javaVersionMajor as String)
    }

    static String getExpectedWrapperIncompatibilityErrorMessage(Jvm jdk) {
        getExpectedIncompatibilityErrorMessage(SupportedJavaVersions.MINIMUM_WRAPPER_JAVA_VERSION, jdk.javaVersionMajor as String)
    }

    private static String getExpectedIncompatibilityErrorMessage(int minimumVersion, String majorVersion) {
        "Gradle requires JVM ${minimumVersion} or later to run. Your build is currently configured to use JVM ${majorVersion}."
    }

    static Pattern getErrorPattern(Jvm jdk) {
        getErrorPattern(jdk.javaVersionMajor)
    }

    static Pattern getErrorPattern(int majorVersion) {
        Pattern.compile("Gradle requires JVM \\d+ or later to run. (You are currently using|Your build is currently configured to use) JVM ${majorVersion}.")
    }
}
