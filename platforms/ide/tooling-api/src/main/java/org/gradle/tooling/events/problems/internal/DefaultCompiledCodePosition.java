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

package org.gradle.tooling.events.problems.internal;

import org.gradle.tooling.events.problems.CompiledCodePosition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class DefaultCompiledCodePosition implements CompiledCodePosition {

    private final String fileName;
    private final String className;
    @Nullable
    private final String module;
    @Nullable
    private final Integer line;

    public DefaultCompiledCodePosition(String fileName, String className, @Nullable String module, @Nullable Integer line) {
        this.fileName = fileName;
        this.className = className;
        this.module = module;
        this.line = line;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Nullable
    @Override
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
}
