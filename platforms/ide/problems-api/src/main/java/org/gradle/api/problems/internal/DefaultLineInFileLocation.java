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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.FileLocation;
import org.gradle.api.problems.LineInFileLocation;

public class DefaultLineInFileLocation extends DefaultFileLocation implements LineInFileLocation {

    private final int line;
    private final int column;
    private final int length;

    private DefaultLineInFileLocation(String path, int line, int column, int length) {
        super(path);
        this.line = line;
        this.column = column;
        this.length = length;
    }

    @Override
    public int getLine() {
        return line;
    }

    @Override
    public int getColumn() {
        return column;
    }

    @Override
    public int getLength() {
        return length;
    }

    public static FileLocation from(String path, Integer line) {
        return new DefaultLineInFileLocation(path, line, -1, -1);
    }

    public static FileLocation from(String path, Integer line, Integer column) {
        return new DefaultLineInFileLocation(path, line, column, -1);
    }

    public static FileLocation from(String path, Integer line, Integer column, Integer length) {
        return new DefaultLineInFileLocation(path, line, column, length);
    }
}
