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

package org.gradle.internal.execution.workspace;

import org.gradle.internal.execution.history.ExecutionHistoryStore;

import java.io.File;

public interface ImmutableWorkspaceProvider {
    /**
     * Provides a workspace and execution history store for executing the transformation.
     */
    <T> T withWorkspace(String identity, WorkspaceAction<T> action);

    @FunctionalInterface
    interface WorkspaceAction<T> {
        T executeInWorkspace(File workspace, ExecutionHistoryStore executionHistoryStore);
    }
}
