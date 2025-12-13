/*
 * Copyright 2024 the original author or authors.
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

/**
 * Contains information about the Java versions that are supported by Gradle.
 */
public class SupportedJavaVersions {

    /**
     * The minimum JVM version that is required to run the Gradle wrapper or a
     * Gradle daemon client.
     * <p>
     * The Tooling API client and CLI Client are both Gradle daemon clients.
     */
    public static final int MINIMUM_CLIENT_JAVA_VERSION = 8;

    /**
     * The minimum JVM version that is required to run a Gradle worker process.
     * <p>
     * The Worker API, JVM tests, and JVM compiler daemons all run within a Gradle worker process.
     */
    public static final int MINIMUM_WORKER_JAVA_VERSION = 8;

    /**
     * The minimum JVM version that is required to run the Gradle daemon.
     */
    public static final int MINIMUM_DAEMON_JAVA_VERSION = 17;

    /**
     * The minimum JVM version that will be required to run the Gradle daemon in the next major Gradle version.
     * <p>
     * If this is the same version as {@link #MINIMUM_DAEMON_JAVA_VERSION}, then the next major
     * version will not require a newer minimum Java version. When you update this version,
     * be sure to add an entry to the upgrade guide.
     */
    public static final int FUTURE_MINIMUM_DAEMON_JAVA_VERSION = 17;

}
