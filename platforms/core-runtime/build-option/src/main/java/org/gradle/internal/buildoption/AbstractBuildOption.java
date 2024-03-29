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

    protected final PropertyOrigin propertyOrigin;
    protected final String property;
    protected final List<V> commandLineOptionConfigurations;
    protected final String deprecatedProperty;

    public AbstractBuildOption(String property) {
        this(property, null, PropertyOrigin.GRADLE_PROPERTIES, Collections.<V>emptyList());
    }

    public AbstractBuildOption(String property, String deprecatedProperty, V... commandLineOptionConfiguration) {
        this(property, deprecatedProperty, PropertyOrigin.GRADLE_PROPERTIES, commandLineOptionConfiguration != null ? Arrays.asList(commandLineOptionConfiguration) : Collections.<V>emptyList());
    }

    public AbstractBuildOption(String property, V... commandLineOptionConfiguration) {
        this(property, null, PropertyOrigin.GRADLE_PROPERTIES, commandLineOptionConfiguration != null ? Arrays.asList(commandLineOptionConfiguration) : Collections.<V>emptyList());
    }

    public AbstractBuildOption(String property, PropertyOrigin propertyOrigin) {
        this(property, null, propertyOrigin, Collections.<V>emptyList());
    }

    public AbstractBuildOption(String property, String deprecatedProperty, PropertyOrigin propertyOrigin, V... commandLineOptionConfiguration) {
        this(property, deprecatedProperty, propertyOrigin, commandLineOptionConfiguration != null ? Arrays.asList(commandLineOptionConfiguration) : Collections.<V>emptyList());
    }

    public AbstractBuildOption(String property, PropertyOrigin propertyOrigin, V... commandLineOptionConfiguration) {
        this(property, null, propertyOrigin, commandLineOptionConfiguration != null ? Arrays.asList(commandLineOptionConfiguration) : Collections.<V>emptyList());
    }

    private AbstractBuildOption(String property, String deprecatedProperty, PropertyOrigin propertyOrigin, List<V> commandLineOptionConfigurations) {
        this.property = property;
        this.deprecatedProperty = deprecatedProperty;
        this.propertyOrigin = propertyOrigin;
        this.commandLineOptionConfigurations = commandLineOptionConfigurations;
    }

    @Override
    public String getProperty() {
        return property;
    }

    @Override
    public String getDeprecatedProperty() {
        return deprecatedProperty;
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
        String value = properties.get(property);
        if (value != null) {
            return new OptionValue<String>(value, property, propertyOrigin);
        }
        if (deprecatedProperty != null) {
            value = properties.get(deprecatedProperty);
            if (value != null) {
                return new OptionValue<String>(value, deprecatedProperty, propertyOrigin);
            }
        }
        return new OptionValue<String>(null, null, propertyOrigin);
    }

    protected static class OptionValue<T> {
        private final T value;
        private final Origin origin;

        public OptionValue(T value, String property, PropertyOrigin propertyOrigin) {
            this.value = value;
            this.origin = propertyOrigin.toOrigin(property);
        }

        public T getValue() {
            return value;
        }
        public Origin getOrigin() {
            return origin;
        }
    }
}
