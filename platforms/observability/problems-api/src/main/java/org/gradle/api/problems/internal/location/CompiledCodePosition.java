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
 * The module coordinates of the jar the class came from, when known, let an IDE open the line from a sources jar.
 */
public final class CompiledCodePosition implements Position, Serializable {

    private final String fileName;
    private final String className;
    private final String module;
    private final Integer line;

    public CompiledCodePosition(String fileName, String className, @Nullable String module, @Nullable Integer line) {
        this.fileName = fileName;
        this.className = className;
        this.module = module;
        this.line = line;
    }

    public String getFileName() {
        return fileName;
    }

    public String getClassName() {
        return className;
    }

    /**
     * The coordinates of the module the class came from, such as {@code com.acme:acme-plugin:1.0}, or null if not known.
     */
    @Nullable
    public String getModule() {
        return module;
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
        return fileName.equals(that.fileName) && className.equals(that.className) && Objects.equals(module, that.module) && Objects.equals(line, that.line);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, className, module, line);
    }

    @Override
    public String toString() {
        return getDisplayName() + " (" + className + ")";
    }
}
