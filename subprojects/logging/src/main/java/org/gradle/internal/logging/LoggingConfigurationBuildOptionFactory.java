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
import org.gradle.api.Transformer;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineOption;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.internal.Factory;
import org.gradle.internal.buildoption.AbstractBuildOption;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.CommandLineOptionConfiguration;
import org.gradle.internal.buildoption.StringBuildOption;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LoggingConfigurationBuildOptionFactory implements Factory<List<BuildOption<LoggingConfiguration>>> {

    private final List<BuildOption<LoggingConfiguration>> options = new ArrayList<BuildOption<LoggingConfiguration>>();

    public LoggingConfigurationBuildOptionFactory() {
        options.add(new LogLevelOption());
        options.add(new StacktraceOption());
        options.add(new ConsoleOption());
    }

    @Override
    public List<BuildOption<LoggingConfiguration>> create() {
        return options;
    }

    public static class LogLevelOption extends AbstractBuildOption<LoggingConfiguration> {
        public static final String QUIET_LONG_OPTION = "quiet";
        public static final String QUIET_SHORT_OPTION = "q";
        public static final String WARN_LONG_OPTION = "warn";
        public static final String WARN_SHORT_OPTION = "w";
        public static final String INFO_LONG_OPTION = "info";
        public static final String INFO_SHORT_OPTION = "i";
        public static final String DEBUG_LONG_OPTION = "debug";
        public static final String DEBUG_SHORT_OPTION = "d";

        public LogLevelOption() {
            super(null, CommandLineOptionConfiguration.create(QUIET_LONG_OPTION, QUIET_SHORT_OPTION, "Log errors only."), CommandLineOptionConfiguration.create(WARN_LONG_OPTION, WARN_SHORT_OPTION, "Set log level to warn."), CommandLineOptionConfiguration.create(INFO_LONG_OPTION, INFO_SHORT_OPTION, "Set log level to info."), CommandLineOptionConfiguration.create(DEBUG_LONG_OPTION, DEBUG_SHORT_OPTION, "Log in debug mode (includes normal stacktrace)."));
        }

        @Override
        public void applyFromProperty(Map<String, String> properties, LoggingConfiguration settings) {
            // not supported
        }

        @Override
        public void configure(CommandLineParser parser) {
            for (CommandLineOptionConfiguration config : commandLineOptionConfigurations) {
                CommandLineOption option = parser.option(config.getAllOptions())
                    .hasDescription(config.getDescription())
                    .deprecated(config.getDeprecationWarning());

                if (config.isIncubating()) {
                    option.incubating();
                }
            }

            List<String> allShortOptions = CollectionUtils.collect(commandLineOptionConfigurations, new Transformer<String, CommandLineOptionConfiguration>() {
                @Override
                public String transform(CommandLineOptionConfiguration commandLineOptionConfiguration) {
                    return commandLineOptionConfiguration.getShortOption();
                }
            });

            parser.allowOneOf(allShortOptions.toArray(new String[allShortOptions.size()]));
        }

        @Override
        public void applyFromCommandLine(ParsedCommandLine options, LoggingConfiguration settings) {
            if (options.hasOption(QUIET_LONG_OPTION)) {
                settings.setLogLevel(LogLevel.QUIET);
            } else if (options.hasOption(WARN_LONG_OPTION)) {
                settings.setLogLevel(LogLevel.WARN);
            } else if (options.hasOption(INFO_LONG_OPTION)) {
                settings.setLogLevel(LogLevel.INFO);
            } else if (options.hasOption(DEBUG_LONG_OPTION)) {
                settings.setLogLevel(LogLevel.DEBUG);
            }
        }
    }

    public static class StacktraceOption extends AbstractBuildOption<LoggingConfiguration> {
        public static final String STACKTRACE_LONG_OPTION = "stacktrace";
        public static final String STACKTRACE_SHORT_OPTION = "s";
        public static final String FULL_STACKTRACE_LONG_OPTION = "full-stacktrace";
        public static final String FULL_STACKTRACE_SHORT_OPTION = "S";

        public StacktraceOption() {
            super(null, CommandLineOptionConfiguration.create(STACKTRACE_LONG_OPTION, STACKTRACE_SHORT_OPTION, "Print out the stacktrace for all exceptions."), CommandLineOptionConfiguration.create(FULL_STACKTRACE_LONG_OPTION, FULL_STACKTRACE_SHORT_OPTION, "Print out the full (very verbose) stacktrace for all exceptions."));
        }

        @Override
        public void applyFromProperty(Map<String, String> properties, LoggingConfiguration settings) {
            // not supported
        }

        @Override
        public void configure(CommandLineParser parser) {
            for (CommandLineOptionConfiguration config : commandLineOptionConfigurations) {
                CommandLineOption option = parser.option(config.getAllOptions())
                    .hasDescription(config.getDescription())
                    .deprecated(config.getDeprecationWarning());

                if (config.isIncubating()) {
                    option.incubating();
                }
            }

            List<String> allShortOptions = CollectionUtils.collect(commandLineOptionConfigurations, new Transformer<String, CommandLineOptionConfiguration>() {
                @Override
                public String transform(CommandLineOptionConfiguration commandLineOptionConfiguration) {
                    return commandLineOptionConfiguration.getShortOption();
                }
            });

            parser.allowOneOf(allShortOptions.toArray(new String[allShortOptions.size()]));
        }

        @Override
        public void applyFromCommandLine(ParsedCommandLine options, LoggingConfiguration settings) {
            if (options.hasOption(STACKTRACE_LONG_OPTION)) {
                settings.setShowStacktrace(ShowStacktrace.ALWAYS);
            } else if (options.hasOption(FULL_STACKTRACE_LONG_OPTION)) {
                settings.setShowStacktrace(ShowStacktrace.ALWAYS_FULL);
            }
        }
    }

    public static class ConsoleOption extends StringBuildOption<LoggingConfiguration> {
        public static final String LONG_OPTION = "console";

        public ConsoleOption() {
            super(null, CommandLineOptionConfiguration.create("console", "Specifies which type of console output to generate. Values are 'plain', 'auto' (default) or 'rich'."));
        }

        @Override
        public void applyTo(String value, LoggingConfiguration settings, Origin origin) {
            String consoleValue = StringUtils.capitalize(value.toLowerCase(Locale.ENGLISH));
            try {
                ConsoleOutput consoleOutput = ConsoleOutput.valueOf(consoleValue);
                settings.setConsoleOutput(consoleOutput);
            } catch (IllegalArgumentException e) {
                throw new CommandLineArgumentException(String.format("Unrecognized value '%s' for %s.", value, LONG_OPTION));
            }
        }
    }
}
