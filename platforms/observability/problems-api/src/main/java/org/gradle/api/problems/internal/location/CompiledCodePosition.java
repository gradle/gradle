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
 * A position in compiled code, from the debug information of a class: the source file name, the class, and the line.
 * It names no module: a jar has coordinates only if it was resolved as a module, and many jars of a build were not.
 */
public final class CompiledCodePosition implements Position, Serializable {

    private final String fileName;
    private final String className;
    private final Integer line;

    public CompiledCodePosition(String fileName, String className, @Nullable Integer line) {
        this.fileName = fileName;
        this.className = className;
        this.line = line;
    }

    public String getFileName() {
        return fileName;
    }

    public String getClassName() {
        return className;
    }

    @Nullable
    @Override
    public Integer getLine() {
        return line;
    }

    @Nullable
    @Override
    public Integer getColumn() {
        return null;
    }

    @Nullable
    @Override
    public Integer getOffset() {
        return null;
    }

    @Nullable
    @Override
    public Integer getLength() {
        return null;
    }

    public String getDisplayName() {
        return line == null ? fileName : fileName + ":" + line;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CompiledCodePosition that = (CompiledCodePosition) o;
        return fileName.equals(that.fileName) && className.equals(that.className) && Objects.equals(line, that.line);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, className, line);
    }

    @Override
    public String toString() {
        return getDisplayName() + " (" + className + ")";
    }
}
