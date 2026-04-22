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
import org.gradle.internal.deprecation.Documentation;
import org.jspecify.annotations.NullMarked;

/**
 * The error for Groovy code that reads {@code properties} or calls {@code getProperties()} on a project or script.
 *
 * <p>The project and script classes keep a {@code getProperties()} method that throws this error.
 * Without that method, Groovy resolves such calls to its own {@code Object.getProperties()}, which returns a map of the bean properties of the object.
 */
@NullMarked
public final class RemovedGetPropertiesAccess {

    private static final String MESSAGE =
        "Accessing 'properties' on a Project or script is no longer supported. " +
        "The Project.getProperties() and script getProperties() methods were removed in Gradle 10.0.0. " +
        "Use providers.gradleProperty(name) (lazy, Configuration Cache compatible) for Gradle properties, " +
        "findProperty(name) for hierarchical lookup, or the 'ext' extension for extra properties. " +
        Documentation.upgradeMajorGuide(10, "removed_get_properties").getConsultDocumentationMessage();

    private RemovedGetPropertiesAccess() {
    }

    public static InvalidUserCodeException failure() {
        return new InvalidUserCodeException(MESSAGE);
    }
}
