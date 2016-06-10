/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.logging.LogLevel;
import org.gradle.cli.AbstractCommandLineConverter;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;

import java.util.*;

public class LoggingCommandLineConverter extends AbstractCommandLineConverter<LoggingConfiguration> {
    public static final String DEBUG = "d";
    public static final String DEBUG_LONG = "debug";
    public static final String INFO = "i";
    public static final String INFO_LONG = "info";
    public static final String QUIET = "q";
    public static final String QUIET_LONG = "quiet";
    public static final String CONSOLE = "console";
    public static final String FULL_STACKTRACE = "S";
    public static final String FULL_STACKTRACE_LONG = "full-stacktrace";
    public static final String STACKTRACE = "s";
    public static final String STACKTRACE_LONG = "stacktrace";
    private final BiMap<String, LogLevel> logLevelMap = HashBiMap.create();
    private final BiMap<String, ShowStacktrace> showStacktraceMap = HashBiMap.create();

    public LoggingCommandLineConverter() {
        logLevelMap.put(QUIET, LogLevel.QUIET);
        logLevelMap.put(INFO, LogLevel.INFO);
        logLevelMap.put(DEBUG, LogLevel.DEBUG);
        showStacktraceMap.put(FULL_STACKTRACE, ShowStacktrace.ALWAYS_FULL);
        showStacktraceMap.put(STACKTRACE, ShowStacktrace.ALWAYS);
    }

    public LoggingConfiguration convert(ParsedCommandLine commandLine, LoggingConfiguration loggingConfiguration) throws CommandLineArgumentException {
        for (Map.Entry<String, LogLevel> entry : logLevelMap.entrySet()) {
            if (commandLine.hasOption(entry.getKey())) {
                loggingConfiguration.setLogLevel(entry.getValue());
            }
        }

        for (Map.Entry<String, ShowStacktrace> entry : showStacktraceMap.entrySet()) {
            if (commandLine.hasOption(entry.getKey())) {
                loggingConfiguration.setShowStacktrace(entry.getValue());
            }
        }

        if (commandLine.hasOption(CONSOLE)) {
            String value = commandLine.option(CONSOLE).getValue();
            String consoleValue = StringUtils.capitalize(value.toLowerCase(Locale.ENGLISH));
            try {
                ConsoleOutput consoleOutput = ConsoleOutput.valueOf(consoleValue);
                loggingConfiguration.setConsoleOutput(consoleOutput);
            } catch (IllegalArgumentException e) {
                throw new CommandLineArgumentException(String.format("Unrecognized value '%s' for %s.", value, CONSOLE));
            }
        }

        return loggingConfiguration;
    }

    public void configure(CommandLineParser parser) {
        parser.option(DEBUG, DEBUG_LONG).hasDescription("Log in debug mode (includes normal stacktrace).");
        parser.option(QUIET, QUIET_LONG).hasDescription("Log errors only.");
        parser.option(INFO, INFO_LONG).hasDescription("Set log level to info.");
        parser.allowOneOf(DEBUG, QUIET, INFO);

        parser.option(CONSOLE).hasArgument().hasDescription("Specifies which type of console output to generate. Values are 'plain', 'auto' (default) or 'rich'.");

        parser.option(STACKTRACE, STACKTRACE_LONG).hasDescription("Print out the stacktrace for all exceptions.");
        parser.option(FULL_STACKTRACE, FULL_STACKTRACE_LONG).hasDescription("Print out the full (very verbose) stacktrace for all exceptions.");
        parser.allowOneOf(STACKTRACE, FULL_STACKTRACE_LONG);
    }

    /**
     * This returns the log level object represented by the command line argument
     *
     * @param commandLineArgument a single command line argument (with no '-')
     * @return the corresponding log level or null if it doesn't match any.
     */
    public LogLevel getLogLevel(String commandLineArgument) {
        LogLevel logLevel = logLevelMap.get(commandLineArgument);
        if (logLevel == null) {
            return null;
        }

        return logLevel;
    }

    /**
     * This returns the command line argument that represents the specified log level.
     *
     * @param logLevel the log level.
     * @return the command line argument or null if this level cannot be represented on the command line.
     */
    public String getLogLevelCommandLine(LogLevel logLevel) {
        String commandLine = logLevelMap.inverse().get(logLevel);
        if (commandLine == null) {
            return null;
        }

        return commandLine;
    }

    /**
     * This returns the log levels that are supported on the command line.
     *
     * @return a collection of available log levels
     */
    public Set<LogLevel> getLogLevels() {
        return new HashSet<LogLevel>(Arrays.asList(LogLevel.DEBUG, LogLevel.INFO, LogLevel.LIFECYCLE, LogLevel.QUIET));
    }

    /**
     * @return the set of short option strings that are used to configure log levels.
     */
    public Set<String> getLogLevelOptions() {
        return logLevelMap.keySet();
    }

    /**
     * This returns the stack trace level object represented by the command line argument
     *
     * @param commandLineArgument a single command line argument (with no '-')
     * @return the corresponding stack trace level or null if it doesn't match any.
     */
    public ShowStacktrace getShowStacktrace(String commandLineArgument) {
        ShowStacktrace showStacktrace = showStacktraceMap.get(commandLineArgument);
        if (showStacktrace == null) {
            return null;
        }

        return showStacktrace;
    }

    /**
     * This returns the command line argument that represents the specified stack trace level.
     *
     * @param showStacktrace the stack trace level.
     * @return the command line argument or null if this level cannot be represented on the command line.
     */
    public String getShowStacktraceCommandLine(ShowStacktrace showStacktrace) {
        String commandLine = showStacktraceMap.inverse().get(showStacktrace);
        if (commandLine == null) {
            return null;
        }

        return commandLine;
    }
}
