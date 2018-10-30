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

import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.CompositeCleanupAction;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.local.FileAccessTimeJournal;
import org.gradle.internal.resource.local.SingleDepthFileAccessTracker;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.ListSerializer;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.gradle.api.internal.artifacts.ivyservice.CacheLayout.TRANSFORMS_META_DATA;
import static org.gradle.api.internal.artifacts.ivyservice.CacheLayout.TRANSFORMS_STORE;
import static org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup.DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultTransformerExecutionHistoryRepository implements TransformerExecutionHistoryRepository, Stoppable {
    
    private static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 2;
    private static final String CACHE_PREFIX = TRANSFORMS_META_DATA.getKey() + "/";
    
    private final PersistentIndexedCache<HashCode, List<File>> indexedCache;
    private final SingleDepthFileAccessTracker fileAccessTracker;
    private final File filesOutputDirectory;
    private final PersistentCache cache;

    public DefaultTransformerExecutionHistoryRepository(File transformsStoreDirectory, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory cacheDecoratorFactory, FileAccessTimeJournal fileAccessTimeJournal) {
        filesOutputDirectory = new File(transformsStoreDirectory, TRANSFORMS_STORE.getKey());
        cache = cacheRepository
            .cache(transformsStoreDirectory)
            .withCleanup(createCleanupAction(filesOutputDirectory, fileAccessTimeJournal))
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withDisplayName("Artifact transforms cache")
            .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
            .open();
        indexedCache = cache.createCache(
            PersistentIndexedCacheParameters.of(CACHE_PREFIX + "results", new HashCodeSerializer(), new ListSerializer<File>(BaseSerializerFactory.FILE_SERIALIZER))
                .withCacheDecorator(cacheDecoratorFactory.decorator(1000, true))
        );
        fileAccessTracker = new SingleDepthFileAccessTracker(fileAccessTimeJournal, filesOutputDirectory, FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP);
    }

    private CleanupAction createCleanupAction(File filesOutputDirectory, FileAccessTimeJournal fileAccessTimeJournal) {
        return CompositeCleanupAction.builder()
            .add(filesOutputDirectory, new LeastRecentlyUsedCacheCleanup(new SingleDepthFilesFinder(FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP), fileAccessTimeJournal, DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES))
            .build();
    }

    @Override
    public Optional<List<File>> getPreviousExecution(HashCode cacheKey) {
        Optional<List<File>> previousExecutionResult = Optional.ofNullable(indexedCache.get(cacheKey));
        previousExecutionResult.ifPresent(files -> fileAccessTracker.markAccessed(files));
        return previousExecutionResult;
    }

    @Override
    public void persist(HashCode cacheKey, List<File> currentExecutionResult) {
        fileAccessTracker.markAccessed(currentExecutionResult);
        indexedCache.put(cacheKey, currentExecutionResult);
    }

    @Override
    public File getOutputDirectory(File toBeTransformed, HashCode cacheKey) {
        return new File(filesOutputDirectory, toBeTransformed.getName() + "/" + cacheKey);
    }

    @Override
    public void stop() {
        cache.close();
    }
}
