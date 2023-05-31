/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.cache.Cache;
import org.gradle.cache.ManualEvictionInMemoryCache;
import org.gradle.internal.Try;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.workspace.WorkspaceProvider;

import java.io.File;

public class TestTransformWorkspaceServices implements TransformWorkspaceServices {
    private final File transformationsStoreDirectory;
    private final ExecutionHistoryStore executionHistoryStore;

    public TestTransformWorkspaceServices(File transformationsStoreDirectory, ExecutionHistoryStore executionHistoryStore) {
        this.transformationsStoreDirectory = transformationsStoreDirectory;
        this.executionHistoryStore = executionHistoryStore;
    }

    @Override
    public WorkspaceProvider getWorkspaceProvider() {
        return new WorkspaceProvider() {
            @Override
            public <T> T withWorkspace(String path, WorkspaceAction<T> action) {
                File workspace = new File(transformationsStoreDirectory, path);
                return action.executeInWorkspace(workspace, executionHistoryStore);
            }
        };
    }

    @Override
    public Cache<UnitOfWork.Identity, Try<TransformExecutionResult>> getIdentityCache() {
        return new ManualEvictionInMemoryCache<>();
    }
}
