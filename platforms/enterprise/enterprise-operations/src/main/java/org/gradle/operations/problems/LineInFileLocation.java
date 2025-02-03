/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.operations.problems;

import javax.annotation.Nullable;

public interface LineInFileLocation extends FileLocation {
    /**
     * The line number within the file.
     * <p>
     * The line is <b>one-indexed</b>, i.e. the first line in the file is line number 1.
     *
     * @return the line number
     */
    int getLine();

    /**
     * The starting column on the selected line.
     * <p>
     * The column is <b>one-indexed</b>, i.e. the first column in the file is line number 1.
     * A non-positive value indicates that the column information is not available.
     *
     * @return the column
     */
    @Nullable
    Integer getColumn();

    /**
     * The length of the selected content starting from specified column.
     * A negative value indicates that the column information is not available.
     *
     * @return the length
     */
    @Nullable
    Integer getLength();
}
