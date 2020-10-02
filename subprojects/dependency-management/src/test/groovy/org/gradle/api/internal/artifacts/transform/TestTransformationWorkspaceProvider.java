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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import org.gradle.internal.Try;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.ExecutionHistoryStore;

import java.io.File;

public class TestTransformationWorkspaceProvider implements TransformationWorkspaceProvider {
    private final File transformationsStoreDirectory;
    private final ExecutionHistoryStore executionHistoryStore;
    private final Cache<UnitOfWork.Identity, Try<ImmutableList<File>>> emptyCache = CacheBuilder.newBuilder().maximumSize(0).build();

    public TestTransformationWorkspaceProvider(File transformationsStoreDirectory, ExecutionHistoryStore executionHistoryStore) {
        this.transformationsStoreDirectory = transformationsStoreDirectory;
        this.executionHistoryStore = executionHistoryStore;
    }

    @Override
    public ExecutionHistoryStore getExecutionHistoryStore() {
        return executionHistoryStore;
    }

    @Override
    public Cache<UnitOfWork.Identity, Try<ImmutableList<File>>> getIdentityCache() {
        return emptyCache;
    }

    @Override
    public <T> T withWorkspace(UnitOfWork.Identity identity, TransformationWorkspaceAction<T> workspaceAction) {
        String identityString = identity.getUniqueId();
        return workspaceAction.useWorkspace(identityString, new File(transformationsStoreDirectory, identityString));
    }
}
