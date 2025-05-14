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

package org.gradle.internal.execution.steps;

import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine;
import org.jspecify.annotations.Nullable;

import java.io.File;

public class WorkspaceResult extends CachingResult implements ExecutionEngine.Result {
    private final File workspace;

    public WorkspaceResult(CachingResult parent, @Nullable File workspace) {
        super(parent);
        this.workspace = workspace;
    }

    @Override
    public <T> Try<T> getOutputAs(Class<T> type) {
        return getExecution()
            .map(execution -> execution.getOutput(workspace))
            .map(type::cast);
    }
}
