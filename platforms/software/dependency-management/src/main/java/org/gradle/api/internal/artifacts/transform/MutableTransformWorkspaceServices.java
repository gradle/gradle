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

import org.gradle.api.file.Directory;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.cache.Cache;
import org.gradle.cache.ManualEvictionInMemoryCache;
import org.gradle.internal.Try;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.workspace.WorkspaceProvider;
import org.gradle.internal.file.ReservedFileSystemLocation;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.File;
import java.util.Optional;

@NotThreadSafe
public class MutableTransformWorkspaceServices implements TransformWorkspaceServices, ReservedFileSystemLocation {

    private final Cache<UnitOfWork.Identity, Try<TransformExecutionResult>> identityCache = new ManualEvictionInMemoryCache<>();
    private final Provider<Directory> baseDirectory;
    private final WorkspaceProvider workspaceProvider;
    private final ExecutionHistoryStore executionHistoryStore;

    public MutableTransformWorkspaceServices(Provider<Directory> baseDirectory, ExecutionHistoryStore executionHistoryStore) {
        this.baseDirectory = baseDirectory;
        this.workspaceProvider = new MutableTransformWorkspaceProvider();
        this.executionHistoryStore = executionHistoryStore;
    }

    @Override
    public WorkspaceProvider getWorkspaceProvider() {
        return workspaceProvider;
    }

    @Override
    public Cache<UnitOfWork.Identity, Try<TransformExecutionResult>> getIdentityCache() {
        return identityCache;
    }

    @Override
    public Provider<? extends FileSystemLocation> getReservedFileSystemLocation() {
        return baseDirectory;
    }

    private class MutableTransformWorkspaceProvider implements WorkspaceProvider {
        @Override
        public Optional<ExecutionHistoryStore> getHistory() {
            return Optional.of(executionHistoryStore);
        }

        @Override
        public <T> T withWorkspace(String path, WorkspaceAction<T> action) {
            File workspaceDir = new File(baseDirectory.get().getAsFile(), path);
            return action.executeInWorkspace(workspaceDir);
        }
    }
}
