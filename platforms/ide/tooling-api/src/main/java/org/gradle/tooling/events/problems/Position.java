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

package org.gradle.tooling.events.problems;

import org.gradle.api.Incubating;
import org.jspecify.annotations.Nullable;

/**
 * Where in a file a {@link Frame} points.
 * <p>
 * Line and column count from 1, offset from 0. A value that isn't known is {@code null}.
 * A position has a line, with or without a column, or an offset: a reporter gives the one its tool knows, and Gradle doesn't convert between them.
 * <p>
 * Column, offset and length count characters, where a character is a Java {@code char}.
 * A tab is one character, however wide an editor shows it.
 * A character outside the Basic Multilingual Plane, such as an emoji, counts as two.
 * <p>
 * A {@link FilePosition} names a file of the build.
 *
 * @since 9.10.0
 */
@Incubating
public interface Position {

    /**
     * Returns the line, counting from 1.
     *
     * @return the line, or {@code null} if not known
     * @since 9.10.0
     */
    @Nullable
    Integer getLine();

    /**
     * Returns the column in the line, counting characters from 1.
     *
     * @return the column, or {@code null} if not known
     * @since 9.10.0
     */
    @Nullable
    Integer getColumn();

    /**
     * Returns the offset from the start of the file, counting characters from 0.
     *
     * @return the offset, or {@code null} if not known
     * @since 9.10.0
     */
    @Nullable
    Integer getOffset();

    /**
     * Returns the length of the range the frame points at, in characters.
     *
     * @return the length, or {@code null} if not known
     * @since 9.10.0
     */
    @Nullable
    Integer getLength();
}
