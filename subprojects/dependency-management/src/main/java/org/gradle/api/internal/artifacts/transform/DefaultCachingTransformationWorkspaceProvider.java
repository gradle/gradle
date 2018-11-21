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
import com.google.common.util.concurrent.ExecutionError;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.execution.history.ExecutionHistoryStore;

import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.util.concurrent.ExecutionException;

@ThreadSafe
public abstract class DefaultCachingTransformationWorkspaceProvider implements CachingTransformationWorkspaceProvider {

    private final TransformationWorkspaceProvider delegate;
    private final Cache<TransformationIdentity, ImmutableList<File>> inMemoryResultCache = CacheBuilder.newBuilder().build();

    public DefaultCachingTransformationWorkspaceProvider(TransformationWorkspaceProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public ExecutionHistoryStore getExecutionHistoryStore() {
        return delegate.getExecutionHistoryStore();
    }

    @Override
    public boolean hasCachedResult(TransformationIdentity identity) {
        return inMemoryResultCache.getIfPresent(identity) != null;
    }

    @Override
    public ImmutableList<File> withWorkspace(TransformationIdentity identity, TransformationWorkspaceAction workspaceAction) {
        try {
            return inMemoryResultCache.get(identity, () -> {
                    return delegate.withWorkspace(identity, workspaceAction);
                });
        } catch (ExecutionException | UncheckedException | ExecutionError e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    public void clearInMemoryCache() {
        inMemoryResultCache.invalidateAll();
    }
}
