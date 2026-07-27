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
 * An internal Gradle option that can be defined in (first one wins):
 * <ol>
 *     <li>A system property ({@code -Dorg.gradle.internal.…})</li>
 *     <li>Gradle User Home {@code gradle.properties}</li>
 *     <li>Build root {@code gradle.properties}</li>
 *     <li>Gradle installation (GRADLE_HOME) {@code gradle.properties}</li>
 * </ol>
 *
 * Values are scoped to the build tree and are not overridden by included builds.
 *
 * @param <T> the type of the option value
 * @see InternalOptions
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
            name.equals("org.gradle.unsafe.suppress-gradle-api"); // TODO: remove this exception, once the property is either removed or becomes public
    }
}
