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

import org.jspecify.annotations.Nullable;

/**
 * An internal Gradle option, that can be set using a system property.
 *
 * @param <T> The value of the option.
 */
public abstract class InternalOption<T extends @Nullable Object> implements Option {

    private final static String INTERNAL_PROPERTY_PREFIX = "org.gradle.internal.";

    private final String propertyName;

    public InternalOption(String propertyName) {
        if (!isInternalOption(propertyName)) {
            throw new IllegalArgumentException("Internal property name must start with '" + INTERNAL_PROPERTY_PREFIX + "', got '" + propertyName + "'");
        }

        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public abstract T getDefaultValue();

    public abstract T convert(String value);

    @Override
    public String toString() {
        return "InternalOption('" + getPropertyName() + "', default=" + getDefaultValue() + ")";
    }

    public static boolean isInternalOption(String name) {
        return name.startsWith(INTERNAL_PROPERTY_PREFIX) ||
            name.startsWith("org.gradle.unsafe.") || // TODO: avoid reading public 'unsafe' properties via internal options
            name.startsWith("org.gradle.configuration-cache.internal."); // TODO:configuration-cache - https://github.com/gradle/gradle/issues/35489
    }
}
