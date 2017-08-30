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

import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;

import java.util.Map;

/**
 * A build option that takes a boolean value.
 * <p>
 * If a command line option is provided, this build option automatically creates a disabled option out-of-the-box e.g. {@code "no-daemon"} for the provided option {@code "daemon"}.
 *
 * @since 4.2
 */
public abstract class BooleanBuildOption<T> extends AbstractBuildOption<T> {

    protected BooleanBuildOption(Class<T> settingsType, String gradleProperty) {
        super(settingsType, gradleProperty);
    }

    public BooleanBuildOption(Class<T> settingsType, String gradleProperty, CommandLineOptionConfiguration commandLineOptionConfiguration) {
        super(settingsType, gradleProperty, commandLineOptionConfiguration);
    }

    @Override
    public void applyFromProperty(Map<String, String> properties, T settings) {
        boolean value = isTrue(properties.get(gradleProperty));
        applyTo(value, settings);
    }

    @Override
    public void configure(CommandLineParser parser) {
        if (hasCommandLineOption()) {
            parser.option(commandLineOptionConfiguration.getOption()).hasDescription(commandLineOptionConfiguration.getDescription());
            parser.option("no-" + commandLineOptionConfiguration.getOption()).hasDescription("Disables option --" + commandLineOptionConfiguration.getOption() + ".");
            parser.allowOneOf(commandLineOptionConfiguration.getOption(), "no-" + commandLineOptionConfiguration.getOption());
        }
    }

    @Override
    public void applyFromCommandLine(ParsedCommandLine options, T settings) {
        if (hasCommandLineOption()) {
            if (options.hasOption(commandLineOptionConfiguration.getOption())) {
                applyTo(true, settings);
            }

            if (options.hasOption("no-" + commandLineOptionConfiguration.getOption())) {
                applyTo(false, settings);
            }
        }
    }

    public abstract void applyTo(boolean value, T settings);
}
