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

package org.gradle.internal.logging;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.internal.Factory;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.CommandLineOptionConfiguration;
import org.gradle.internal.buildoption.NoArgumentBuildOption;
import org.gradle.internal.buildoption.StringBuildOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LoggingConfigurationBuildOptionFactory implements Factory<List<BuildOption<LoggingConfiguration>>> {

    private final List<BuildOption<LoggingConfiguration>> options = new ArrayList<BuildOption<LoggingConfiguration>>();

    public LoggingConfigurationBuildOptionFactory() {
        options.add(new QuietOption());
        options.add(new WarnOption());
        options.add(new InfoOption());
        options.add(new DebugOption());
        options.add(new StacktraceOption());
        options.add(new FullStacktraceOption());
        options.add(new ConsoleOption());
    }

    @Override
    public List<BuildOption<LoggingConfiguration>> create() {
        return options;
    }

    public static class QuietOption extends NoArgumentBuildOption<LoggingConfiguration> {
        public static final String LONG_OPTION = "quiet";
        public static final String SHORT_OPTION = "q";

        public QuietOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, "Log errors only."));
        }

        @Override
        public void applyTo(LoggingConfiguration settings) {
            settings.setLogLevel(LogLevel.QUIET);
        }
    }

    public static class WarnOption extends NoArgumentBuildOption<LoggingConfiguration> {
        public static final String LONG_OPTION = "warn";
        public static final String SHORT_OPTION = "w";

        public WarnOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, "Set log level to warn."));
        }

        @Override
        public void applyTo(LoggingConfiguration settings) {
            settings.setLogLevel(LogLevel.WARN);
        }
    }

    public static class InfoOption extends NoArgumentBuildOption<LoggingConfiguration> {
        public static final String LONG_OPTION = "info";
        public static final String SHORT_OPTION = "i";

        public InfoOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, "Set log level to info."));
        }

        @Override
        public void applyTo(LoggingConfiguration settings) {
            settings.setLogLevel(LogLevel.INFO);
        }
    }

    public static class DebugOption extends NoArgumentBuildOption<LoggingConfiguration> {
        public static final String LONG_OPTION = "debug";
        public static final String SHORT_OPTION = "d";

        public DebugOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, "Log in debug mode (includes normal stacktrace)."));
        }

        @Override
        public void applyTo(LoggingConfiguration settings) {
            settings.setLogLevel(LogLevel.DEBUG);
        }
    }

    public static class StacktraceOption extends NoArgumentBuildOption<LoggingConfiguration> {
        public static final String LONG_OPTION = "stacktrace";
        public static final String SHORT_OPTION = "s";

        public StacktraceOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, "Print out the stacktrace for all exceptions."));
        }

        @Override
        public void applyTo(LoggingConfiguration settings) {
            settings.setShowStacktrace(ShowStacktrace.ALWAYS);
        }
    }

    public static class FullStacktraceOption extends NoArgumentBuildOption<LoggingConfiguration> {
        public static final String LONG_OPTION = "full-stacktrace";
        public static final String SHORT_OPTION = "S";

        public FullStacktraceOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, "Print out the full (very verbose) stacktrace for all exceptions."));
        }

        @Override
        public void applyTo(LoggingConfiguration settings) {
            settings.setShowStacktrace(ShowStacktrace.ALWAYS_FULL);
        }
    }

    public static class ConsoleOption extends StringBuildOption<LoggingConfiguration> {

        public ConsoleOption() {
            super(null, CommandLineOptionConfiguration.create("console", "Specifies which type of console output to generate. Values are 'plain', 'auto' (default) or 'rich'."));
        }

        @Override
        public void applyTo(String value, LoggingConfiguration settings) {
            String consoleValue = StringUtils.capitalize(value.toLowerCase(Locale.ENGLISH));
            try {
                ConsoleOutput consoleOutput = ConsoleOutput.valueOf(consoleValue);
                settings.setConsoleOutput(consoleOutput);
            } catch (IllegalArgumentException e) {
                throw new CommandLineArgumentException(String.format("Unrecognized value '%s' for %s.", value, commandLineOptionConfiguration.getLongOption()));
            }
        }
    }
}
