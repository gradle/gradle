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

import com.google.common.io.ByteSource;
import org.gradle.testkit.runner.BuildTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.List;

public class GradleExecutionResult {

    private final BuildOperationParameters buildOperationParameters;
    private final ByteSource outputSource;
    private final List<BuildTask> tasks;
    @Nullable
    private final Throwable throwable;

    public GradleExecutionResult(BuildOperationParameters buildOperationParameters, String standardOutput, List<BuildTask> tasks) {
        this(buildOperationParameters, standardOutput, tasks, null);
    }

    public GradleExecutionResult(BuildOperationParameters buildOperationParameters, String standardOutput, List<BuildTask> tasks, @Nullable Throwable throwable) {
        this(buildOperationParameters, ByteSource.wrap(standardOutput.getBytes(Charset.defaultCharset())), tasks, throwable);
    }

    public GradleExecutionResult(BuildOperationParameters buildOperationParameters, ByteSource outputSource, List<BuildTask> tasks) {
        this(buildOperationParameters, outputSource, tasks, null);
    }

    public GradleExecutionResult(
        BuildOperationParameters buildOperationParameters,
        ByteSource outputSource,
        List<BuildTask> tasks,
        @Nullable Throwable throwable
    ) {
        this.buildOperationParameters = buildOperationParameters;
        this.outputSource = outputSource;
        this.tasks = tasks;
        this.throwable = throwable;
    }

    public BuildOperationParameters getBuildOperationParameters() {
        return buildOperationParameters;
    }

    @Nonnull
    public ByteSource getOutputSource() {
        return outputSource;
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
