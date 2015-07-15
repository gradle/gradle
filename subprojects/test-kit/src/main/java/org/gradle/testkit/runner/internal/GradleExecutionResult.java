/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.internal;

import org.gradle.testkit.runner.BuildTask;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class GradleExecutionResult {
    private final ByteArrayOutputStream standardOutput;
    private final ByteArrayOutputStream standardError;
    private final List<BuildTask> tasks;
    private final Throwable throwable;

    public GradleExecutionResult(ByteArrayOutputStream standardOutput, ByteArrayOutputStream standardError, List<BuildTask> tasks) {
        this(standardOutput, standardError, tasks, null);
    }

    public GradleExecutionResult(ByteArrayOutputStream standardOutput, ByteArrayOutputStream standardError, List<BuildTask> tasks, Throwable throwable) {
        this.standardOutput = standardOutput;
        this.standardError = standardError;
        this.tasks = tasks;
        this.throwable = throwable;
    }

    public String getStandardOutput() {
        return standardOutput.toString();
    }

    public String getStandardError() {
        return standardError.toString();
    }

    public List<BuildTask> getTasks() {
        return tasks;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public boolean isSuccessful() {
        return throwable == null;
    }
}
