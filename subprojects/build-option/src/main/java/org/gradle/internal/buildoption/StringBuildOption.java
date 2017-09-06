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

package org.gradle.internal.buildoption;

import org.gradle.cli.CommandLineOption;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;

import java.util.Map;

/**
 * A build option that takes a string value e.g. {@code "--max-workers=4"}.
 *
 * @since 4.3
 */
public abstract class StringBuildOption<T> extends AbstractBuildOption<T> {

    public StringBuildOption(String gradleProperty) {
        super(gradleProperty);
    }

    public StringBuildOption(String gradleProperty, CommandLineOptionConfiguration commandLineOptionConfiguration) {
        super(gradleProperty, commandLineOptionConfiguration);
    }


    @Override
    public void applyFromProperty(Map<String, String> properties, T settings) {
        String value = properties.get(gradleProperty);

        if (value != null) {
            applyTo(value, settings);
        }
    }

    @Override
    public void configure(CommandLineParser parser) {
        if (hasCommandLineOption()) {
            CommandLineOption option = parser.option(commandLineOptionConfiguration.getAllOptions())
                .hasDescription(commandLineOptionConfiguration.getDescription())
                .hasArgument();

            if (commandLineOptionConfiguration.isIncubating()) {
                option.incubating();
            }
        }
    }

    @Override
    public void applyFromCommandLine(ParsedCommandLine options, T settings) {
        if (hasCommandLineOption()) {
            if (options.hasOption(commandLineOptionConfiguration.getLongOption())) {
                String value = options.option(commandLineOptionConfiguration.getLongOption()).getValue();
                applyTo(value, settings);
            }
        }
    }

    public abstract void applyTo(String value, T settings);
}
