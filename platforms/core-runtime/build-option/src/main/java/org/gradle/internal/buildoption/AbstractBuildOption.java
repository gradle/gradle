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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Provides a basic infrastructure for build option implementations.
 *
 * @since 4.3
 */
public abstract class AbstractBuildOption<T, V extends CommandLineOptionConfiguration> implements BuildOption<T> {

    protected final String gradleProperty;
    protected final List<V> commandLineOptionConfigurations;
    protected final String deprecatedGradleProperty;

    public AbstractBuildOption(String gradleProperty) {
        this(gradleProperty, null, Collections.<V>emptyList());
    }

    public AbstractBuildOption(String gradleProperty, String deprecatedGradleProperty, V... commandLineOptionConfiguration) {
        this(gradleProperty, deprecatedGradleProperty, commandLineOptionConfiguration != null ? Arrays.asList(commandLineOptionConfiguration) : Collections.<V>emptyList());
    }

    public AbstractBuildOption(String gradleProperty, V... commandLineOptionConfiguration) {
        this(gradleProperty, null, commandLineOptionConfiguration != null ? Arrays.asList(commandLineOptionConfiguration) : Collections.<V>emptyList());
    }

    private AbstractBuildOption(String gradleProperty, String deprecatedGradleProperty, List<V> commandLineOptionConfigurations) {
        this.gradleProperty = gradleProperty;
        this.deprecatedGradleProperty = deprecatedGradleProperty;
        this.commandLineOptionConfigurations = commandLineOptionConfigurations;
    }

    @Override
    public String getGradleProperty() {
        return gradleProperty;
    }

    @Override
    public String getDeprecatedGradleProperty() {
        return deprecatedGradleProperty;
    }

    protected boolean isTrue(String value) {
        return value != null && value.trim().equalsIgnoreCase("true");
    }

    protected CommandLineOption configureCommandLineOption(CommandLineParser parser, String[] options, String description, boolean deprecated, boolean incubating) {
        CommandLineOption option = parser.option(options)
            .hasDescription(description);

        if(deprecated) {
            option.deprecated();
        }

        if (incubating) {
            option.incubating();
        }

        return option;
    }

    protected OptionValue<String> getFromProperties(Map<String, String> properties) {
        String value = properties.get(gradleProperty);
        if (value != null) {
            return new OptionValue<String>(value, Origin.forGradleProperty(gradleProperty));
        }
        if (deprecatedGradleProperty != null) {
            value = properties.get(deprecatedGradleProperty);
            if (value != null) {
                return new OptionValue<String>(value, Origin.forGradleProperty(deprecatedGradleProperty));
            }
        }
        return new OptionValue<String>(null, null);
    }

    protected static class OptionValue<T> {
        private final T value;
        private final Origin origin;

        public OptionValue(T value, Origin origin) {
            this.value = value;
            this.origin = origin;
        }
        public T getValue() {
            return value;
        }
        public Origin getOrigin() {
            return origin;
        }
    }
}
