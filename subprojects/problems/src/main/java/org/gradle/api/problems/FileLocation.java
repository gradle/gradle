/*
 * Copyright 2023 the original author or authors.
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

import javax.annotation.Nullable;

/**
 * A basic problem location pointing to a specific part of a file.
 *
 * @since 8.5
 */
@Incubating
public class FileLocation implements ProblemLocation {

    private final String path;
    private final Integer line;
    private final Integer column;
    private final Integer length;

    public FileLocation(String path, @Nullable Integer line, @Nullable Integer column, @Nullable Integer length) {
        this.path = path;
        this.line = line;
        this.column = column;
        this.length = length;
    }

    @Override
    public String getType() {
        return "file";
    }

    public String getPath() {
        return path;
    }
    @Nullable
    public Integer getLine() {
        return line;
    }

    @Nullable
    public Integer getColumn() {
        return column;
    }

    @Nullable
    public Integer getLength() {
        return length;
    }
}
