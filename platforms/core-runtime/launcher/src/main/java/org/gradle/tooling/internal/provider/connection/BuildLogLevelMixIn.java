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

package org.gradle.tooling.internal.provider.connection;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.cli.CommandLineConverter;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.cli.SystemPropertiesCommandLineConverter;
import org.gradle.internal.logging.LoggingConfigurationBuildOptions;
import org.gradle.internal.logging.LoggingConfigurationBuildOptions.LogLevelOption;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.empty;

public class BuildLogLevelMixIn {
    private final LogLevel logLevel;

    public BuildLogLevelMixIn(ProviderOperationParameters parameters) {
        this.logLevel = calcBuildLogLevel(parameters);
    }

    public LogLevel getBuildLogLevel() {
        return this.logLevel;
    }

    private static LogLevel calcBuildLogLevel(ProviderOperationParameters parameters) {
        LoggingConfigurationBuildOptions loggingBuildOptions = new LoggingConfigurationBuildOptions();
        CommandLineConverter<LoggingConfiguration> converter = loggingBuildOptions.commandLineConverter();

        SystemPropertiesCommandLineConverter propertiesCommandLineConverter = new SystemPropertiesCommandLineConverter();
        CommandLineParser parser = new CommandLineParser().allowUnknownOptions().allowMixedSubcommandsAndOptions();

        converter.configure(parser);
        propertiesCommandLineConverter.configure(parser);

        List<String> arguments = parameters.getArguments();
        ParsedCommandLine parsedCommandLine = parser.parse(arguments == null ? Collections.emptyList() : arguments);

        //configure verbosely only if arguments do not specify any log level.
        return getLogLevelFromCommandLineOptions(loggingBuildOptions, parsedCommandLine)
            .orElseGet(() ->
                getLogLevelFromCommandLineProperties(propertiesCommandLineConverter, parsedCommandLine).orElseGet(() -> {
                    if (parameters.getVerboseLogging()) {
                        return LogLevel.DEBUG;
                    }
                    return null;
                })
            );
    }

    @Nonnull
    private static Optional<LogLevel> getLogLevelFromCommandLineOptions(LoggingConfigurationBuildOptions loggingBuildOptions, ParsedCommandLine parsedCommandLine) {
        return loggingBuildOptions.getLongLogLevelOptions().stream()
            .filter(parsedCommandLine::hasOption)
            .map(LogLevelOption::parseLogLevel)
            .findFirst();
    }

    private static Optional<LogLevel> getLogLevelFromCommandLineProperties(SystemPropertiesCommandLineConverter propertiesCommandLineConverter, ParsedCommandLine parsedCommandLine) {
        Map<String, String> properties = propertiesCommandLineConverter.convert(parsedCommandLine, new HashMap<>());
        String logLevelCommandLineProperty = properties.get(LogLevelOption.GRADLE_PROPERTY);
        if (logLevelCommandLineProperty != null) {
            try {
                return Optional.of(LogLevelOption.parseLogLevel(logLevelCommandLineProperty));
            } catch (IllegalArgumentException e) {
                // fall through
            }
        }
        return empty();

    }
}
