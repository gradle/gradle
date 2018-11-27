/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata;
import org.gradle.api.internal.artifacts.ivyservice.DefaultArtifactCacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.DefaultArtifactCacheMetadata;
import org.gradle.api.internal.artifacts.transform.ImmutableCachingTransformationWorkspaceProvider;
import org.gradle.api.internal.artifacts.transform.ImmutableTransformationWorkspaceProvider;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.DefaultExecutionHistoryCacheAccess;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.UsedGradleVersions;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.history.ExecutionHistoryCacheAccess;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.impl.DefaultExecutionHistoryStore;
import org.gradle.internal.resource.local.FileAccessTimeJournal;

public class DependencyManagementGradleUserHomeScopeServices {
    DefaultArtifactCacheMetadata createArtifactCacheMetaData(CacheScopeMapping cacheScopeMapping) {
        return new DefaultArtifactCacheMetadata(cacheScopeMapping);
    }

    ArtifactCacheLockingManager createArtifactCacheLockingManager(CacheRepository cacheRepository, ArtifactCacheMetadata artifactCacheMetadata, FileAccessTimeJournal fileAccessTimeJournal,
                                                                  UsedGradleVersions usedGradleVersions) {
        return new DefaultArtifactCacheLockingManager(cacheRepository, artifactCacheMetadata, fileAccessTimeJournal, usedGradleVersions);
    }

    ExecutionHistoryCacheAccess createExecutionHistoryCacheAccess(CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        return new DefaultExecutionHistoryCacheAccess(null, cacheRepository, inMemoryCacheDecoratorFactory);
    }

    ExecutionHistoryStore createExecutionHistoryStore(ExecutionHistoryCacheAccess executionHistoryCacheAccess, StringInterner stringInterner) {
        return new DefaultExecutionHistoryStore(executionHistoryCacheAccess, stringInterner);
    }

    ImmutableTransformationWorkspaceProvider createTransformerWorkspaceProvider(ArtifactCacheMetadata artifactCacheMetadata, CacheRepository cacheRepository, FileAccessTimeJournal fileAccessTimeJournal, ExecutionHistoryStore executionHistoryStore) {
        return new ImmutableTransformationWorkspaceProvider(artifactCacheMetadata.getTransformsStoreDirectory(), cacheRepository, fileAccessTimeJournal, executionHistoryStore);
    }

    ImmutableCachingTransformationWorkspaceProvider createCachingTransformerWorkspaceProvider(ImmutableTransformationWorkspaceProvider immutableTransformationWorkspaceProvider, ListenerManager listenerManager) {
        ImmutableCachingTransformationWorkspaceProvider cachingWorkspaceProvider = new ImmutableCachingTransformationWorkspaceProvider(immutableTransformationWorkspaceProvider);
        listenerManager.addListener(new RootBuildLifecycleListener() {
            @Override
            public void afterStart() {
            }

            @Override
            public void beforeComplete() {
                cachingWorkspaceProvider.clearInMemoryCache();
            }
        });
        return cachingWorkspaceProvider;
    }
}
