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
import org.gradle.api.tasks.HasStringRepresentation;

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
     * @param value a value which can be converted to a String.
     */
    void add(String name, HasStringRepresentation value);

    /**
     * Adds a system property to use for the process.
     *
     * @param name The name of the property
     * @param value The value for the property. May be null.
     */
    void add(String name, @Nullable Object value);

    /**
     * Returns a value which is an input file.
     *
     * <p>
     * The string representation of the input file is its absolute path.
     * The file is resolved according to {@link org.gradle.api.Project#file}.
     * </p>
     *
     * <p>The path sensitivity of the input file is {@link org.gradle.api.tasks.PathSensitivity#NONE}.</p>
     */
    HasStringRepresentation inputFile(Object file);

    /**
     * Returns a value which is an input directory.
     *
     * <p>
     * The string representation of the input directory is its absolute path.
     * The directory is resolved according to {@link org.gradle.api.Project#file}.
     * </p>
     *
     * <p>The path sensitivity of the input directory is {@link org.gradle.api.tasks.PathSensitivity#RELATIVE}.</p>
     */
    HasStringRepresentation inputDirectory(Object directory);

    /**
     * Returns a value which is an output file.
     *
     * <p>
     * The string representation of the output file is its absolute path.
     * The file is resolved according to {@link org.gradle.api.Project#file}.
     * </p>
     */
    HasStringRepresentation outputFile(Object file);

    /**
     * Returns a value which is an output directory.
     *
     * <p>
     * The string representation of the output directory is its absolute path.
     * The directory is resolved according to {@link org.gradle.api.Project#file}.
     * </p>
     */
    HasStringRepresentation outputDirectory(Object directory);

    /**
     * Returns a value which is neither an input nor an output.
     *
     * <p>
     * The string representation of the value is the result of calling {@link Object#toString()}.
     * </p>
     */
    HasStringRepresentation ignored(Object value);
}
