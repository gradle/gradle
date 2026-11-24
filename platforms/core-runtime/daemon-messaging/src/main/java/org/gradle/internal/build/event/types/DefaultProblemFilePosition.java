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

package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.problem.InternalFilePosition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * A position in a file of the build, as a problem frame points at it. Not to be confused with the position of a test event.
 */
@NullMarked
public class DefaultProblemFilePosition implements InternalFilePosition, Serializable {

    private final String path;
    @Nullable
    private final Integer line;
    @Nullable
    private final Integer column;
    @Nullable
    private final Integer offset;
    @Nullable
    private final Integer length;

    public DefaultProblemFilePosition(String path, @Nullable Integer line, @Nullable Integer column, @Nullable Integer offset, @Nullable Integer length) {
        this.path = path;
        this.line = line;
        this.column = column;
        this.offset = offset;
        this.length = length;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Nullable
    @Override
    public Integer getLine() {
        return line;
    }

    @Nullable
    @Override
    public Integer getColumn() {
        return column;
    }

    @Nullable
    @Override
    public Integer getOffset() {
        return offset;
    }

    @Nullable
    @Override
    public Integer getLength() {
        return length;
    }
}
