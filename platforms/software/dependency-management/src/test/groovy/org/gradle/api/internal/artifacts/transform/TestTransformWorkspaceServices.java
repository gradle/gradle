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
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.execution.workspace.MutableWorkspaceProvider;

import java.io.File;
import java.util.UUID;
import java.util.function.Supplier;

public abstract class TestTransformWorkspaceServices {

    public static ImmutableTransformWorkspaceServices immutable(File transformationsStoreDirectory) {
        Cache<UnitOfWork.Identity, Try<TransformWorkspaceResult>> identityCache = new ManualEvictionInMemoryCache<>();
        return new ImmutableTransformWorkspaceServices() {
            @Override
            public ImmutableWorkspaceProvider getWorkspaceProvider() {
                return path -> new ImmutableWorkspaceProvider.ImmutableWorkspace() {
                    @Override
                    public File getImmutableLocation() {
                        return new File(transformationsStoreDirectory, path);
                    }

                    @Override
                    public <T> T withTemporaryWorkspace(TemporaryWorkspaceAction<T> action) {
                        return action.executeInTemporaryWorkspace(new File(transformationsStoreDirectory, path + "-" + UUID.randomUUID().toString()));
                    }
                };
            }

            @Override
            public Cache<UnitOfWork.Identity, Try<TransformWorkspaceResult>> getIdentityCache() {
                return identityCache;
            }

            @Override
            public void close() {
            }
        };
    }

    public static MutableTransformWorkspaceServices mutable(File transformationsStoreDirectory, ExecutionHistoryStore executionHistoryStore) {
        Cache<UnitOfWork.Identity, Try<TransformWorkspaceResult>> identityCache = new ManualEvictionInMemoryCache<>();
        return new MutableTransformWorkspaceServices() {
            @Override
            public MutableWorkspaceProvider getWorkspaceProvider() {
                return new MutableWorkspaceProvider() {
                    @Override
                    public <T> T withWorkspace(String path, WorkspaceAction<T> action) {
                        File workspace = new File(transformationsStoreDirectory, path);
                        return action.executeInWorkspace(workspace, executionHistoryStore);
                    }
                };
            }

            @Override
            public Cache<UnitOfWork.Identity, Try<TransformWorkspaceResult>> getIdentityCache() {
                return identityCache;
            }

            @Override
            public Supplier<File> getReservedFileSystemLocation() {
                return () -> transformationsStoreDirectory;
            }
        };
    }
}
