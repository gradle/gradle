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
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

/**
 * Provides a basic infrastructure for build option implementations.
 *
 * @since 4.3
 */
public abstract class AbstractBuildOption<T, V extends CommandLineOptionConfiguration> implements BuildOption<T> {

    @Nullable
    protected final String property;
    protected final List<V> commandLineOptionConfigurations;
    @Nullable
    protected final String deprecatedProperty;

    public AbstractBuildOption(String property) {
        this(property, null, emptyList());
    }

    @SuppressWarnings("unchecked") // otherwise, vararg heap pollution warning-as-error
    public AbstractBuildOption(@Nullable String property, V... commandLineOptionConfiguration) {
        this(property, null, Arrays.asList(commandLineOptionConfiguration));
    }

    public AbstractBuildOption(@Nullable String property, String deprecatedProperty) {
        this(property, deprecatedProperty, emptyList());
    }

    @SuppressWarnings("unchecked") // otherwise, vararg heap pollution warning-as-error
    public AbstractBuildOption(@Nullable String property, @Nullable String deprecatedProperty, V... commandLineOptionConfiguration) {
        this(property, deprecatedProperty, Arrays.asList(commandLineOptionConfiguration));
    }

    private AbstractBuildOption(@Nullable String property, @Nullable String deprecatedProperty, List<V> commandLineOptionConfigurations) {
        this.property = property;
        this.deprecatedProperty = deprecatedProperty;
        this.commandLineOptionConfigurations = commandLineOptionConfigurations;
    }

    @Override
    @Nullable
    public String getProperty() {
        return property;
    }

    @Override
    @Nullable
    public String getDeprecatedProperty() {
        return deprecatedProperty;
    }

    protected CommandLineOption configureCommandLineOption(CommandLineParser parser, String[] options, String description, boolean deprecated, boolean incubating) {
        CommandLineOption option = parser.option(options)
            .hasDescription(description);

        if (deprecated) {
            option.deprecated();
        }

        if (incubating) {
            option.incubating();
        }

        return option;
    }

    protected OptionValue<String> getFromProperties(Map<String, String> properties) {
        String value = properties.get(property);
        if (value != null) {
            return new OptionValue<String>(value, Origin.forGradleProperty(property));
        }
        if (deprecatedProperty != null) {
            value = properties.get(deprecatedProperty);
            if (value != null) {
                return new OptionValue<String>(value, Origin.forGradleProperty(deprecatedProperty));
            }
        }
        return new OptionValue<String>(null, null);
    }

    protected static class OptionValue<T> {
        @Nullable
        private final T value;
        @Nullable
        private final Origin origin;

        public OptionValue(@Nullable T value, @Nullable Origin origin) {
            this.value = value;
            this.origin = origin;
        }

        @Nullable
        public T getValue() {
            return value;
        }

        @Nullable
        public Origin getOrigin() {
            return origin;
        }
    }
}
