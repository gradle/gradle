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
 * A build option that takes a boolean value.
 * <p>
 * If a command line option is provided, this build option automatically creates a disabled option out-of-the-box e.g. {@code "--no-daemon"} for the provided option {@code "--daemon"}.
 *
 * @since 4.3
 */
public abstract class BooleanBuildOption<T> extends AbstractBuildOption<T> {

    public BooleanBuildOption(String gradleProperty) {
        super(gradleProperty);
    }

    public BooleanBuildOption(String gradleProperty, CommandLineOptionConfiguration commandLineOptionConfiguration) {
        super(gradleProperty, commandLineOptionConfiguration);
    }

    @Override
    public void applyFromProperty(Map<String, String> properties, T settings) {
        boolean value = isTrue(properties.get(gradleProperty));
        applyTo(value, settings);
    }

    @Override
    public void configure(CommandLineParser parser) {
        if (hasCommandLineOption()) {
            String disabledOption = getDisabledCommandLineOption();
            CommandLineOption enabledCommandLineOption = parser.option(commandLineOptionConfiguration.getLongOption()).hasDescription(commandLineOptionConfiguration.getDescription());

            if (commandLineOptionConfiguration.isIncubating()) {
                enabledCommandLineOption.incubating();
            }

            CommandLineOption disabledCommandLineOption = parser.option(disabledOption).hasDescription(getDisabledCommandLineDescription());

            if (commandLineOptionConfiguration.isIncubating()) {
                disabledCommandLineOption.incubating();
            }

            parser.allowOneOf(commandLineOptionConfiguration.getLongOption(), disabledOption);
        }
    }

    @Override
    public void applyFromCommandLine(ParsedCommandLine options, T settings) {
        if (hasCommandLineOption()) {
            if (options.hasOption(commandLineOptionConfiguration.getLongOption())) {
                applyTo(true, settings);
            }

            if (options.hasOption(getDisabledCommandLineOption())) {
                applyTo(false, settings);
            }
        }
    }

    protected String getDisabledCommandLineOption() {
        return hasCommandLineOption() ? "no-" + commandLineOptionConfiguration.getLongOption() : null;
    }

    protected String getDisabledCommandLineDescription() {
        return hasCommandLineOption() ? "Disables option --" + commandLineOptionConfiguration.getLongOption() + "." : null;
    }

    public abstract void applyTo(boolean value, T settings);
}
