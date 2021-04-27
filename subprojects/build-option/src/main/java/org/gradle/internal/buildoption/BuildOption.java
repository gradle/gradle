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

import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Represents a option for a build provided by the user via Gradle property and/or a command line option.
 *
 * @param <T> the type of object that ultimately expresses the option to consumers
 * @since 4.3
 */
public interface BuildOption<T> {

    @Nullable
    String getGradleProperty();

    void applyFromProperty(Map<String, String> properties, T settings);

    void configure(CommandLineParser parser);

    void applyFromCommandLine(ParsedCommandLine options, T settings);

    abstract class Value<T> {
        public abstract boolean isExplicit();

        public abstract T get();

        /**
         * Creates the default value for an option.
         */
        public static <T> Value<T> defaultValue(final T value) {
            return new Value<T>() {
                @Override
                public boolean isExplicit() {
                    return false;
                }

                @Override
                public T get() {
                    return value;
                }
            };
        }

        /**
         * Creates an explicit value for an option.
         */
        public static <T> Value<T> value(final T value) {
            return new Value<T>() {
                @Override
                public boolean isExplicit() {
                    return true;
                }

                @Override
                public T get() {
                    return value;
                }
            };
        }
    }

}
