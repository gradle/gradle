/*
 * Copyright 2022 the original author or authors.
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

/**
 * Represents some user configurable value that can be defined outside the Gradle model, for example via a command-line option or a Gradle
 * property. Some options may also be configurable via the Gradle API, for example feature previews.
 */
public interface Option {
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
