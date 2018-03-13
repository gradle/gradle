/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.process;

import org.gradle.api.Incubating;

import javax.annotation.Nullable;

/**
 * Allows adding system properties.
 *
 * @since 4.7
 */
@Incubating
public interface SystemPropertyHandler {
    /**
     * Adds a system property to use for the process.
     *
     * @param name The name of the property.
     * @param value an @{@link InputOutputValue} representing the property.
     */
    void add(String name, InputOutputValue value);

    /**
     * Adds a system property to use for the process.
     *
     * @param name The name of the property
     * @param value The value for the property. May be null.
     */
    void add(String name, @Nullable Object value);

    /**
     * An input file with path sensitivity {@link org.gradle.api.tasks.PathSensitivity#NONE}.
     */
    InputOutputValue inputFile(Object file);

    /**
     * An input directory with path sensitivity {@link org.gradle.api.tasks.PathSensitivity#RELATIVE}.
     */
    InputOutputValue inputDirectory(Object file);

    /**
     * An output file.
     */
    InputOutputValue outputFile(Object file);

    /**
     * An output directory.
     */
    InputOutputValue outputDirectory(Object file);

    /**
     * A value declaring no inputs or outputs.
     */
    InputOutputValue ignored(Object file);
}
