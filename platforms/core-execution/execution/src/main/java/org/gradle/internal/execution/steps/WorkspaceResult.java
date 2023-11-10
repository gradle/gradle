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

import javax.annotation.Nullable;
import java.io.File;

public class WorkspaceResult extends CachingResult implements ExecutionEngine.Result {
    private final File workspace;

    public WorkspaceResult(CachingResult parent, @Nullable File workspace) {
        super(parent);
        this.workspace = workspace;
    }

    @Override
    public <T> Try<T> resolveOutputsFromWorkspaceAs(Class<T> type) {
        return getExecution()
            .map(ExecutionEngine.Execution::getOutput)
            .map(type::cast);
    }
}
