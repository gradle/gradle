/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.InvalidUserCodeException;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RemovedGetPropertiesAccess {

    private static final String MESSAGE =
        "Accessing 'properties' on a Project or script is no longer supported. " +
        "The Project.getProperties() and script getProperties() methods were removed in Gradle 10.0.0. " +
        "Use providers.gradleProperty(name) (lazy, Configuration Cache compatible) for Gradle properties, " +
        "findProperty(name) for hierarchical lookup, or the 'ext' extension for extra properties. " +
        "See https://docs.gradle.org/current/userguide/upgrading_major_version_10.html#removed_get_properties";

    private RemovedGetPropertiesAccess() {
    }

    public static void failIfPropertiesAccess(String propertyName) {
        if ("properties".equals(propertyName)) {
            throw new InvalidUserCodeException(MESSAGE);
        }
    }
}
