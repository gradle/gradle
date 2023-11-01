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

package org.gradle.internal.execution.workspace.impl;

import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.workspace.Workspace;
import org.gradle.internal.execution.workspace.WorkspaceProvider;

import java.io.File;
import java.util.Optional;

public class NonLockingWorkspaceProvider implements WorkspaceProvider {
    private final ExecutionHistoryStore executionHistoryStore;
    private final File baseDirectory;

    public NonLockingWorkspaceProvider(ExecutionHistoryStore executionHistoryStore, File baseDirectory) {
        this.executionHistoryStore = executionHistoryStore;
        this.baseDirectory = baseDirectory;
    }

    @Override
    public Optional<ExecutionHistoryStore> getHistory() {
        return Optional.of(executionHistoryStore);
    }

    @Override
    public Workspace allocateWorkspace(String path) {
        File workspaceDir = new File(baseDirectory, path);
        Workspace.WorkspaceLocation workspace = new DefaultWorkspaceLocation(workspaceDir);
        return new Workspace() {
            @Override
            public <T> T mutate(WorkspaceAction<T> action) {
                return action.executeInWorkspace(workspace);
            }
        };
    }
}
