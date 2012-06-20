/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.logging.internal.logback;

import ch.qos.logback.classic.Level;
import org.gradle.api.Nullable;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.slf4j.Marker;

public class LogLevelConverter {
    /**
     * Maps a Logback log level and optional marker to a Gradle log level.
     * Returns null if there is no equivalent Gradle log level (such as for TRACE).
     */
    @Nullable
    public static LogLevel toGradleLogLevel(Level level, @Nullable Marker marker) {
        switch(level.toInt()) {
            case Level.TRACE_INT:
                return null;
            case Level.DEBUG_INT:
                return LogLevel.DEBUG;
            case Level.INFO_INT:
                if (marker == Logging.LIFECYCLE) {
                    return LogLevel.LIFECYCLE;
                }
                if (marker == Logging.QUIET) {
                    return LogLevel.QUIET;
                }
                return LogLevel.INFO;
            case Level.WARN_INT:
                return LogLevel.WARN;
            case Level.ERROR_INT:
                return LogLevel.ERROR;
            default:
                throw new IllegalArgumentException("Don't know how to map Logback log level '" + level + "' to a Gradle log level");
        }
    }

    public static Level toLogbackLevel(LogLevel level) {
        switch (level) {
            case DEBUG:
                return Level.DEBUG;
            case INFO:
            case LIFECYCLE:
            case QUIET:
                return Level.INFO;
            case WARN:
                return Level.WARN;
            case ERROR:
                return Level.ERROR;
            default:
                throw new IllegalArgumentException("Don't know how to map Gradle log level '" + level + "' to a Logback log level");
        }
    }
}
