/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;

import java.util.Map;

public abstract class IntegerBuildOption<T> extends AbstractBuildOption<T, CommandLineOptionConfiguration> {

    public IntegerBuildOption(String gradleProperty) {
        super(gradleProperty);
    }

    public IntegerBuildOption(String gradleProperty, String deprecatedGradleProperty) {
        super(gradleProperty, deprecatedGradleProperty);
    }

    public IntegerBuildOption(String gradleProperty, CommandLineOptionConfiguration... commandLineOptionConfigurations) {
        super(gradleProperty, commandLineOptionConfigurations);
    }


    @Override
    public void applyFromProperty(Map<String, String> properties, T settings) {
        OptionValue<String> propertyValue = getFromProperties(properties);
        String value = propertyValue.getValue();
        if (value != null) {
            applyTo(Integer.parseInt(value), settings, propertyValue.getOrigin());
        }
    }

    @Override
    public void configure(CommandLineParser parser) {
        for (CommandLineOptionConfiguration config : commandLineOptionConfigurations) {
            configureCommandLineOption(parser, config.getAllOptions(), config.getDescription(), config.isDeprecated(), config.isIncubating()).hasArgument();
        }
    }

    @Override
    public void applyFromCommandLine(ParsedCommandLine options, T settings) {
        for (CommandLineOptionConfiguration config : commandLineOptionConfigurations) {
            if (options.hasOption(config.getLongOption())) {
                String value = options.option(config.getLongOption()).getValue();
                applyTo(Integer.parseInt(value), settings, Origin.forCommandLine(config.getLongOption()));
            }
        }
    }

    public abstract void applyTo(int value, T settings, Origin origin);
}
