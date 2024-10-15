/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.jvm;

import org.gradle.util.GradleVersion;

/**
 * An exception thrown when using an invalid JVM for running Gradle.
 */
public class UnsupportedJavaRuntimeException extends RuntimeException {

    public UnsupportedJavaRuntimeException(String message) {
        super(message);
    }

    /**
     * Assert the current JVM is capable of running the daemon for current Gradle version.
     */
    public static void assertUsingSupportedDaemonVersion() throws UnsupportedJavaRuntimeException {
        assertIsSupportedDaemonJvmVersion(Jvm.current().getJavaVersionMajor(), "You are currently using Java %d.");
    }

    /**
     * Assert the given JVM version is capable of running the daemon for the current Gradle version.
     */
    public static void assertIsSupportedDaemonJvmVersion(int majorVersion) {
        assertIsSupportedDaemonJvmVersion(majorVersion, "Your build is currently configured to use Java %d.");
    }

    private static void assertIsSupportedDaemonJvmVersion(int majorVersion, String message) {
        if (majorVersion < SupportedJavaVersions.MINIMUM_JAVA_VERSION) {
            throw new UnsupportedJavaRuntimeException(String.format(
                "Gradle %s requires Java %d or later to run. " + message,
                GradleVersion.current().getVersion(),
                SupportedJavaVersions.MINIMUM_JAVA_VERSION,
                majorVersion
            ));
        }
    }

}
