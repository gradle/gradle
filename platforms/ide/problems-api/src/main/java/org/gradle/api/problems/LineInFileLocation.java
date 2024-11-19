/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * A basic location pointing to a specific part of a file using line number, column, and length for coordinates.
 * <p>
 * The line and column coordinates are one-indexed so that they can be easily matched to the content of a UI editor interface.
 *
 * @since 8.12
 */
@Incubating
public interface LineInFileLocation extends FileLocation {

    /**
     * The line number within the file.
     * <p>
     * The line is <b>one-indexed</b>, i.e. the first line in the file is line number 1.
     *
     * @return the line number
     * @since 8.12
     */
    int getLine();

    /**
     * The starting column on the selected line.
     * <p>
     * The column is <b>one-indexed</b>, i.e. the first column in the file is line number 1.
     * A non-positive value indicates that the column information is not available.
     *
     * @return the column
     * @since 8.12
     */
    int getColumn();

    /**
     * The length of the selected content starting from specified column.
     * A negative value indicates that the column information is not available.
     *
     * @return the length
     * @since 8.12
     */
    int getLength();
}
