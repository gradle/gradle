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

import org.gradle.api.internal.jvm.JavaVersionParser;

/**
 * An exception thrown when using an invalid JVM for running Gradle.
 */
public class UnsupportedJavaRuntimeException extends RuntimeException {

    public UnsupportedJavaRuntimeException(String message) {
        super(message);
    }

    /**
     * Assert the current JVM is capable of running the daemon for current Gradle version.
     * <p>
     * In most cases, this assertion is not expected to be triggered, as it is triggered
     * from within daemon code. It stands mostly as a sanity check. Instead, the daemon
     * client should verify the JVM compatibility using {@link #assertIsSupportedDaemonJvmVersion(int)}.
     */
    public static void assertCurrentProcessSupportsDaemonJavaVersion() throws UnsupportedJavaRuntimeException {
        int currentVersion = JavaVersionParser.parseCurrentMajorVersion();
        if (currentVersion < SupportedJavaVersions.MINIMUM_DAEMON_JAVA_VERSION) {
            String message = String.format(
                "Gradle requires JVM %d or later to run. You are currently using JVM %d.",
                SupportedJavaVersions.MINIMUM_DAEMON_JAVA_VERSION,
                currentVersion
            );
            throw new UnsupportedJavaRuntimeException(message);
        }
    }

    /**
     * Assert the given JVM version is capable of running the daemon for the current Gradle version.
     */
    public static void assertIsSupportedDaemonJvmVersion(int majorVersion) {
        if (majorVersion < SupportedJavaVersions.MINIMUM_DAEMON_JAVA_VERSION) {
            throw new UnsupportedJavaRuntimeException(String.format(
                "Gradle requires JVM %d or later to run. Your build is currently configured to use JVM %d.",
                SupportedJavaVersions.MINIMUM_DAEMON_JAVA_VERSION,
                majorVersion
            ));
        }
    }

}
