/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks;

/**
 * Enumeration of different path handling strategies for task properties.
 *
 * @see PathSensitive
 *
 * @since 3.1
 */
public enum PathSensitivity {
    /**
     * Consider the full path of files and directories.
     *
     * <p><b>This will prevent the task's outputs from being shared across different workspaces via the build cache.</b></p>
     */
    ABSOLUTE,

    /**
     * Use the location of the file related to a hierarchy.
     *
     * <p>
     *     For files in the root of the file collection, the file name is used as the normalized path.
     *     For directories in the root of the file collection, an empty string is used as normalized path.
     *     For files in directories in the root of the file collection, the normalized path is the relative path of the file to the root directory containing it.
     * </p>
     *
     * <br>
     * Example: The property is an input directory.
     * <ul>
     *     <li>The path of the input directory is ignored.</li>
     *     <li>The path of the files in the input directory are considered relative to the input directory.</li>
     * </ul>
     */
    RELATIVE,

    /**
     * Consider only the name of files and directories.
     */
    NAME_ONLY,

    /**
     * Ignore file paths and directories altogether.
     *
     * <p>
     *     When used on an {@literal @}{@link org.gradle.work.Incremental} input, instead of
     *     {@link org.gradle.work.ChangeType#MODIFIED} events Gradle may produce
     *     {@link org.gradle.work.ChangeType#ADDED} and {@link org.gradle.work.ChangeType#REMOVED} events.
     *     This is because by ignoring the path of the individual inputs it cannot identify <em>what</em>
     *     has been modified.
     * </p>
     */
    NONE
}
