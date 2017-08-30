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

package org.gradle.initialization.option;

import org.gradle.cli.CommandLineOption;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;

import java.util.Map;

/**
 * A build option that takes a no argument e.g. {@code "--foreground"}.
 *
 * @since 4.2
 */
public abstract class NoArgumentBuildOption<T> extends AbstractBuildOption<T> {

    public NoArgumentBuildOption(Class<T> settingsType, String gradleProperty) {
        super(settingsType, gradleProperty);
    }

    public NoArgumentBuildOption(Class<T> settingsType, String gradleProperty, CommandLineOptionConfiguration commandLineOptionConfiguration) {
        super(settingsType, gradleProperty, commandLineOptionConfiguration);
    }

    @Override
    public void applyFromProperty(Map<String, String> properties, T settings) {
        if (properties.get(gradleProperty) != null) {
            applyTo(settings);
        }
    }

    @Override
    public void configure(CommandLineParser parser) {
        if (hasCommandLineOption()) {
            CommandLineOption option = parser.option(commandLineOptionConfiguration.getOption()).hasDescription(commandLineOptionConfiguration.getDescription());

            if (commandLineOptionConfiguration.isIncubating()) {
                option.incubating();
            }

            if (commandLineOptionConfiguration.isArgument()) {
                option.hasArgument();
            }
        }
    }

    @Override
    public void applyFromCommandLine(ParsedCommandLine options, T settings) {
        if (hasCommandLineOption() && options.hasOption(commandLineOptionConfiguration.getOption())) {
            applyTo(settings);
        }
    }

    public abstract void applyTo(T settings);
}
