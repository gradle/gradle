/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.launcher.cli.converter;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.launcher.daemon.configuration.GradleProperties;

import java.util.Map;

public class PropertiesToLogLevelConfigurationConverter {
    public LoggingConfiguration convert(Map<String, String> properties, LoggingConfiguration loggingConfiguration) {
        String logLevel = properties.get(GradleProperties.LOG_LEVEL_PROPERTY);
        if (logLevel != null) {
            LogLevel level = parseLogLevel(logLevel);
            loggingConfiguration.setLogLevel(level);
        }

        return loggingConfiguration;
    }

    private LogLevel parseLogLevel(String value) {
        try {
            LogLevel logLevel = LogLevel.valueOf(value.toUpperCase());
            if (logLevel == LogLevel.ERROR) {
                throw new IllegalArgumentException("Log level cannot be set to 'ERROR'.");
            }
            return logLevel;
        } catch (IllegalArgumentException e) {
            String message = String.format("Value '%s' given for %s system property is invalid.  (must be one of quiet, warn, lifecycle, info, or debug)", value, GradleProperties.LOG_LEVEL_PROPERTY);
            throw new IllegalArgumentException(message, e);
        }
    }
}
