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

package org.gradle.internal.problems;

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.interfaces.Problem;

import javax.annotation.Nullable;

@NonNullApi
public class DefaultProblem implements Problem {

    private final String message;
    private final String severity;
    private final String file;
    private final Integer line;
    private final Integer column;

    public DefaultProblem(String message, String severity, String file, Integer line, Integer column) {
        this.message = message;
        this.severity = severity;
        this.file = file;
        this.line = line;
        this.column = column;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getSeverity() {
        return severity;
    }

    @Nullable
    @Override
    public String getFile() {
        return file;
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
}
