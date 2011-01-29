/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.logging.internal;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.gradle.CommandLineArgumentException;
import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.AbstractCommandLineConverter;
import org.gradle.initialization.CommandLineParser;
import org.gradle.initialization.ParsedCommandLine;
import org.gradle.logging.LoggingConfiguration;

import java.util.Collection;
import java.util.Collections;

public class LoggingCommandLineConverter extends AbstractCommandLineConverter<LoggingConfiguration> {
    public static final String DEBUG = "d";
    public static final String DEBUG_LONG = "debug";
    public static final String INFO = "i";
    public static final String INFO_LONG = "info";
    public static final String QUIET = "q";
    public static final String QUIET_LONG = "quiet";
    public static final String NO_COLOR = "no-color";
    private final BiMap<String, LogLevel> logLevelMap = HashBiMap.create();

    public LoggingCommandLineConverter() {
        logLevelMap.put(QUIET, LogLevel.QUIET);
        logLevelMap.put(INFO, LogLevel.INFO);
        logLevelMap.put(DEBUG, LogLevel.DEBUG);
        logLevelMap.put("", LogLevel.LIFECYCLE);
    }

    @Override
    protected LoggingConfiguration newInstance() {
        return new LoggingConfiguration();
    }

    public LoggingConfiguration convert(ParsedCommandLine commandLine, LoggingConfiguration loggingConfiguration) throws CommandLineArgumentException {
        loggingConfiguration.setLogLevel(getLogLevel(commandLine));
        if (commandLine.hasOption(NO_COLOR)) {
            loggingConfiguration.setColorOutput(false);
        }
        return loggingConfiguration;
    }

    private LogLevel getLogLevel(ParsedCommandLine options) {
        LogLevel logLevel = LogLevel.LIFECYCLE;
        if (options.hasOption(QUIET)) {
            logLevel = LogLevel.QUIET;
        }
        if (options.hasOption(INFO)) {
            logLevel = LogLevel.INFO;
        }
        if (options.hasOption(DEBUG)) {
            logLevel = LogLevel.DEBUG;
        }
        return logLevel;
    }

    public void configure(CommandLineParser parser) {
        parser.option(DEBUG, DEBUG_LONG).hasDescription("Log in debug mode (includes normal stacktrace).");
        parser.option(QUIET, QUIET_LONG).hasDescription("Log errors only.");
        parser.option(INFO, INFO_LONG).hasDescription("Set log level to info.");
        parser.option(NO_COLOR).hasDescription("Do not use color in the console output.");
    }

    /**
     * This returns the log level object represented by the command line argument
     *
     * @param commandLineArgument a single command line argument (with no '-')
     * @return the corresponding log level or null if it doesn't match any.
     * @author mhunsicker
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
     * @author mhunsicker
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
     * @author mhunsicker
     */
    public Collection<LogLevel> getLogLevels() {
        return Collections.unmodifiableCollection(logLevelMap.values());
    }

}
