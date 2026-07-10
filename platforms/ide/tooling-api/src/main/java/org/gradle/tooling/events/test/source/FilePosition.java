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

package org.gradle.tooling.events.test.source;

import org.gradle.api.Incubating;
import org.jspecify.annotations.Nullable;

/**
 * Position inside a file represented by line and column numbers.
 *
 * @since 9.4.0
 */
@Incubating
public interface FilePosition {

    /**
     * The line number in the file.
     * <p>
     * The result is 1-based, i.e. the first line in the file is line 1.
     *
     * @since 9.4.0
     */
    int getLine();

    /**
     * The column number of this FilePosition, if available.
     *
     * @since 9.4.0
     */
    @Nullable
    Integer getColumn();
}
