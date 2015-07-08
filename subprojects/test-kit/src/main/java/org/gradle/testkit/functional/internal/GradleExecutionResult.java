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

package org.gradle.testkit.functional.internal;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class GradleExecutionResult {
    private final ByteArrayOutputStream standardOutput;
    private final ByteArrayOutputStream standardError;
    private final List<String> executedTasks;
    private final List<String> skippedTasks;
    private final Throwable throwable;

    public GradleExecutionResult(ByteArrayOutputStream standardOutput, ByteArrayOutputStream standardError, List<String> executedTasks, List<String> skippedTasks) {
        this(standardOutput, standardError, executedTasks, skippedTasks, null);
    }

    public GradleExecutionResult(ByteArrayOutputStream standardOutput, ByteArrayOutputStream standardError, List<String> executedTasks, List<String> skippedTasks, Throwable throwable) {
        this.standardOutput = standardOutput;
        this.standardError = standardError;
        this.executedTasks = executedTasks;
        this.skippedTasks = skippedTasks;
        this.throwable = throwable;
    }

    public String getStandardOutput() {
        return standardOutput.toString();
    }

    public String getStandardError() {
        return standardError.toString();
    }

    public List<String> getExecutedTasks() {
        return executedTasks;
    }

    public List<String> getSkippedTasks() {
        return skippedTasks;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public boolean isSuccessful() {
        return throwable == null;
    }
}
