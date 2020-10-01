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
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.ExecutionHistoryStore;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.ExecutionException;

@ThreadSafe
public abstract class AbstractCachingTransformationWorkspaceProvider implements CachingTransformationWorkspaceProvider {

    private final TransformationWorkspaceProvider delegate;
    private final Cache<UnitOfWork.Identity, Object> inMemoryResultCache = CacheBuilder.newBuilder().build();

    public AbstractCachingTransformationWorkspaceProvider(TransformationWorkspaceProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public ExecutionHistoryStore getExecutionHistoryStore() {
        return delegate.getExecutionHistoryStore();
    }

    @Nullable
    @Override
    public <T> T getCachedResult(UnitOfWork.Identity identity) {
        return Cast.uncheckedCast(inMemoryResultCache.getIfPresent(identity));
    }

    @Override
    public <T> T withWorkspace(UnitOfWork.Identity identity, TransformationWorkspaceAction<T> workspaceAction) {
        try {
            return Cast.uncheckedNonnullCast(inMemoryResultCache.get(identity, () -> delegate.withWorkspace(identity, workspaceAction)));
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public void clearInMemoryCache() {
        inMemoryResultCache.invalidateAll();
    }
}
