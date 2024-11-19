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
 * A basic location pointing to a specific part of a file using a global offset and length for coordinates.
 * <p>
 * The coordinates are expected to be zero indexed.
 *
 * @since 8.12
 */
@Incubating
public interface OffsetInFileLocation extends FileLocation {

    /**
     * The global offset from the beginning of the file.
     *
     * @return the zero-indexed the offset
     * @since 8.12
     */
    int getOffset();

    /**
     * The content of the content starting from {@link #getOffset()}.
     *
     * @return the length
     * @since 8.12
     */
    int getLength();
}
