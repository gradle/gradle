/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog;

import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.cache.FineGrainedCacheCleanupStrategyFactory;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.execution.workspace.impl.CacheBasedImmutableWorkspaceProvider;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.Closeable;

@ServiceScope(Scope.BuildSession.class)
public class DependenciesAccessorsWorkspaceProvider implements ImmutableWorkspaceProvider, Closeable {
    private final CacheBasedImmutableWorkspaceProvider delegate;

    public DependenciesAccessorsWorkspaceProvider(
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        FileAccessTimeJournal fileAccessTimeJournal,
        CacheConfigurationsInternal cacheConfigurations,
        FineGrainedCacheCleanupStrategyFactory cacheCleanupStrategyFactory
    ) {
        this.delegate = CacheBasedImmutableWorkspaceProvider.createWorkspaceProvider(
            cacheBuilderFactory
                .createFineGrainedCacheBuilder("dependencies-accessors")
                .withDisplayName("dependencies-accessors"),
            fileAccessTimeJournal,
            cacheConfigurations,
            cacheCleanupStrategyFactory
        );
    }

    @Override
    public LockingImmutableWorkspace getLockingWorkspace(String path) {
        return delegate.getLockingWorkspace(path);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
