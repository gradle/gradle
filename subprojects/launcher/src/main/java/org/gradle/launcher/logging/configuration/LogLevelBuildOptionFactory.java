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

package org.gradle.launcher.logging.configuration;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.internal.Factory;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.StringBuildOption;

import java.util.ArrayList;
import java.util.List;

public class LogLevelBuildOptionFactory implements Factory<List<BuildOption<LoggingConfiguration>>> {

    private final List<BuildOption<LoggingConfiguration>> options = new ArrayList<BuildOption<LoggingConfiguration>>();

    public LogLevelBuildOptionFactory() {
        options.add(new LogLevelOption());
    }

    @Override
    public List<BuildOption<LoggingConfiguration>> create() {
        return options;
    }

    public static class LogLevelOption extends StringBuildOption<LoggingConfiguration> {
        public static final String GRADLE_PROPERTY = "org.gradle.logging.level";

        public LogLevelOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, LoggingConfiguration settings) {
            LogLevel level = parseLogLevel(value);
            settings.setLogLevel(level);
        }

        private LogLevel parseLogLevel(String value) {
            try {
                LogLevel logLevel = LogLevel.valueOf(value.toUpperCase());
                if (logLevel == LogLevel.ERROR) {
                    throw new IllegalArgumentException("Log level cannot be set to 'ERROR'.");
                }
                return logLevel;
            } catch (IllegalArgumentException e) {
                String message = String.format("Value '%s' given for %s system property is invalid.  (must be one of quiet, warn, lifecycle, info, or debug)", value, gradleProperty);
                throw new IllegalArgumentException(message, e);
            }
        }
    }
}
