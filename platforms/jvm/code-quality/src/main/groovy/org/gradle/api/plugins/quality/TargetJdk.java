/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.plugins.quality;

import javax.annotation.Nullable;

/**
 * Represents the PMD targetjdk property available for PMD &lt; 5.0
 */
public enum TargetJdk {
    VERSION_1_3,
    VERSION_1_4,
    VERSION_1_5,
    VERSION_1_6,
    VERSION_1_7,
    VERSION_JSP;

    /**
     * Converts the given object into a {@code TargetJdk}.
     *
     * @param value An object whose toString() value is to be converted. May be null.
     * @return The version, or null if the provided value is null.
     * @throws IllegalArgumentException when the provided value cannot be converted.
     */
    @Nullable
    public static TargetJdk toVersion(@Nullable Object value) throws IllegalArgumentException {
        if (value == null) {
            return null;
        }
        if (value instanceof TargetJdk) {
            return (TargetJdk) value;
        }

        String name = value.toString();
        if (name.equalsIgnoreCase("1.7")) {
            return VERSION_1_7;
        } else if (name.equalsIgnoreCase("1.6")) {
            return VERSION_1_6;
        } else if (name.equalsIgnoreCase("1.5")) {
            return VERSION_1_5;
        } else if (name.equalsIgnoreCase("1.4")) {
            return VERSION_1_4;
        } else if (name.equalsIgnoreCase("1.3")) {
            return VERSION_1_3;
        } else if (name.equalsIgnoreCase("jsp")) {
            return VERSION_JSP;
        } else {
            throw new IllegalArgumentException(String.format("Could not determine targetjdk from '%s'.", name));
        }
    }

    @Override
    public String toString() {
        return getName();
    }

    public String getName() {
        return name().substring("VERSION_".length()).replace('_', '.').toLowerCase();
    }
}
