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

package org.gradle.launcher.daemon.logging;

/**
 * Constants related to daemon log files.
 */
public class DaemonLogConstants {
    /**
     * The suffix for daemon log files.
     */
    public static final String DAEMON_LOG_SUFFIX = ".out.log";

    /**
     * The prefix for daemon log files.
     */
    public static final String DAEMON_LOG_PREFIX = "daemon-";

    /**
     * The directory name where daemon logs are stored, relative to Gradle user home.
     */
    public static final String DAEMON_LOG_DIR = "daemon";

    private DaemonLogConstants() {
        // Utility class
    }
}

