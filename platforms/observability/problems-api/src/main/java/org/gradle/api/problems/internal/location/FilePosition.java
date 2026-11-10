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

package org.gradle.api.problems.internal.location;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

/**
 * A position in a file of the build.
 */
public final class FilePosition implements Position, Serializable {

    private final String path;
    private final Integer line;
    private final Integer column;
    private final Integer offset;
    private final Integer length;

    public FilePosition(String path, @Nullable Integer line, @Nullable Integer column, @Nullable Integer offset, @Nullable Integer length) {
        this.path = path;
        this.line = line;
        this.column = column;
        this.offset = offset;
        this.length = length;
    }

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

    /**
     * The position in the form tools link: {@code path:line:column}, with the parts that are known.
     */
    public String getDisplayName() {
        StringBuilder result = new StringBuilder(path);
        if (line != null) {
            result.append(':').append(line);
            if (column != null) {
                result.append(':').append(column);
            }
        }
        return result.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FilePosition that = (FilePosition) o;
        return path.equals(that.path) && Objects.equals(line, that.line) && Objects.equals(column, that.column) && Objects.equals(offset, that.offset) && Objects.equals(length, that.length);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, line, column, offset, length);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
