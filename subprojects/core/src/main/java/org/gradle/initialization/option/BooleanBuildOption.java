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
 *
 * @since 4.2
 */
public abstract class BooleanBuildOption<T> implements BuildOption<T> {
    protected final Class<T> settingsType;
    protected final String gradleProperty;
    protected final String commandLineOption;
    protected final String description;

    protected BooleanBuildOption(Class<T> settingsType, String gradleProperty) {
        this(settingsType, gradleProperty, null, null);
    }

    public BooleanBuildOption(Class<T> settingsType, String gradleProperty, String commandLineOption, String description) {
        this.settingsType = settingsType;
        this.gradleProperty = gradleProperty;
        this.commandLineOption = commandLineOption;
        this.description = description;
    }

    @Override
    public String getGradleProperty() {
        return gradleProperty;
    }

    @Override
    public void applyFromProperty(Map<String, String> properties, T settings) {
        boolean value = isTrue(properties.get(gradleProperty));
        applyTo(value, settings);
    }

    @Override
    public void configure(CommandLineParser parser) {
        parser.option(commandLineOption).hasDescription(description);
        parser.option("no-" + commandLineOption).hasDescription("Disables option --" + commandLineOption + ".");
        parser.allowOneOf(commandLineOption, "no-" + commandLineOption);
    }

    @Override
    public void applyFromCommandLine(ParsedCommandLine options, T settings) {
        if (options.hasOption(commandLineOption)) {
            applyTo(true, settings);
        }

        if (options.hasOption("no-" + commandLineOption)) {
            applyTo(false, settings);
        }
    }

    private boolean isTrue(String value) {
        return value != null && value.trim().equalsIgnoreCase("true");
    }

    public abstract void applyTo(boolean value, T settings);
}
