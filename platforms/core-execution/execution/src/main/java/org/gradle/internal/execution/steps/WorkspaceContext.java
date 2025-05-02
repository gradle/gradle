/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Optional;

public class WorkspaceContext extends IdentityContext {
    private final File workspace;
    private final ExecutionHistoryStore history;
    private final boolean captureBeforeExecutionState;

    public WorkspaceContext(IdentityContext parent, File workspace, @Nullable ExecutionHistoryStore history, boolean captureBeforeExecutionState) {
        super(parent);
        this.workspace = workspace;
        this.history = history;
        this.captureBeforeExecutionState = captureBeforeExecutionState;
    }

    protected WorkspaceContext(WorkspaceContext parent) {
        this(parent, parent.getWorkspace(), parent.getHistory().orElse(null), parent.shouldCaptureBeforeExecutionState());
    }

    public File getWorkspace() {
        return workspace;
    }

    public boolean shouldCaptureBeforeExecutionState() {
        return captureBeforeExecutionState;
    }

    public Optional<ExecutionHistoryStore> getHistory() {
        return Optional.ofNullable(history);
    }
}
