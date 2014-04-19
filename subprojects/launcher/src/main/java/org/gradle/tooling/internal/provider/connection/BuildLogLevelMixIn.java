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
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.logging.LoggingConfiguration;
import org.gradle.logging.internal.LoggingCommandLineConverter;

import java.util.Collections;

public class BuildLogLevelMixIn {
    private final ProviderOperationParameters parameters;

    public BuildLogLevelMixIn(ProviderOperationParameters parameters) {
        this.parameters = parameters;
    }

    public LogLevel getBuildLogLevel() {
        LoggingCommandLineConverter converter = new LoggingCommandLineConverter();
        CommandLineParser parser = new CommandLineParser().allowUnknownOptions().allowMixedSubcommandsAndOptions();
        converter.configure(parser);
        ParsedCommandLine parsedCommandLine = parser.parse(parameters.getArguments(Collections.<String>emptyList()));
        //configure verbosely only if arguments do not specify any log level.
        if (parameters.getVerboseLogging(false) && !parsedCommandLine.hasAnyOption(converter.getLogLevelOptions())) {
            return LogLevel.DEBUG;
        }

        LoggingConfiguration loggingConfiguration = converter.convert(parsedCommandLine, new LoggingConfiguration());
        return loggingConfiguration.getLogLevel();
    }
}