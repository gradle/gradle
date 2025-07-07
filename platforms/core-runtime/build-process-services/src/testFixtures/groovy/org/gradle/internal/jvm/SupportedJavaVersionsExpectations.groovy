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
            "This will fail with an error in Gradle ${currentMajorGradleVersion + 1}. " +
            "Use JVM ${futureMinimumDaemonJavaVersion} or greater to execute Gradle. " +
            "Projects can continue to use older JVM versions via toolchains. " +
            "Consult the upgrading guide for further information: " +
            "https://docs.gradle.org/${gradleVersion.version}/userguide/upgrading_version_${currentMajorGradleVersion}.html#minimum_daemon_jvm_version"
    }

    /**
     * The error message emitted when the client is started, but the JVM version
     * requested for the daemon cannot execute the daemon.
     */
    static String getMisconfiguredDaemonJavaVersionErrorMessage(int majorVersion) {
        "Gradle requires JVM ${SupportedJavaVersions.MINIMUM_DAEMON_JAVA_VERSION} or later to run. " +
            "Your build is currently configured to use JVM ${majorVersion}."
    }

    /**
     * To be used when the Gradle client is bypassed, therefore not checking
     * compatibility, and daemon classes may be loaded directly on an incompatible JVM.
     */
    static String getIncompatibleDaemonJvmVersionErrorMessage(String processType, int majorVersion) {
        getIncompatibleProcessJvmVersionErrorMessage(
            processType,
            SupportedJavaVersions.MINIMUM_DAEMON_JAVA_VERSION,
            majorVersion
        )
    }

    private static String getIncompatibleProcessJvmVersionErrorMessage(
        String processType,
        int minVersion,
        int currentVersion
    ) {
        "${processType} requires JVM ${minVersion} or later to run. " +
            "You are currently using JVM ${currentVersion}."
    }

}
