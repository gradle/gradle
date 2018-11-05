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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.cache.StringInterner;
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
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryCacheAccess;
import org.gradle.internal.execution.history.impl.DefaultExecutionHistoryStore;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.local.FileAccessTimeJournal;
import org.gradle.internal.resource.local.SingleDepthFileAccessTracker;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import java.io.Closeable;
import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.api.internal.artifacts.ivyservice.CacheLayout.TRANSFORMS_META_DATA;
import static org.gradle.api.internal.artifacts.ivyservice.CacheLayout.TRANSFORMS_STORE;
import static org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup.DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultTransformerExecutionHistoryRepository implements TransformerExecutionHistoryRepository, Closeable {
    
    private static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 2;
    private static final String CACHE_PREFIX = TRANSFORMS_META_DATA.getKey() + "/";
    
    private final SingleDepthFileAccessTracker fileAccessTracker;
    private final File filesOutputDirectory;
    private final PersistentCache cache;
    private final DefaultExecutionHistoryStore executionHistoryStore;

    public DefaultTransformerExecutionHistoryRepository(File transformsStoreDirectory, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory cacheDecoratorFactory, FileAccessTimeJournal fileAccessTimeJournal, StringInterner stringInterner) {
        filesOutputDirectory = new File(transformsStoreDirectory, TRANSFORMS_STORE.getKey());
        cache = cacheRepository
            .cache(transformsStoreDirectory)
            .withCleanup(createCleanupAction(filesOutputDirectory, fileAccessTimeJournal))
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withDisplayName("Artifact transforms cache")
            .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
            .open();
        fileAccessTracker = new SingleDepthFileAccessTracker(fileAccessTimeJournal, filesOutputDirectory, FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP);
        ExecutionHistoryCacheAccess executionHistoryCacheAccess = new ExecutionHistoryCacheAccess() {

            @Override
            public <K, V> PersistentIndexedCache<K, V> createCache(PersistentIndexedCacheParameters<K, V> parameters, int maxEntriesToKeepInMemory, boolean cacheInMemoryForShortLivedProcesses) {
                return cache.createCache(parameters
                    .withCacheDecorator(cacheDecoratorFactory.decorator(maxEntriesToKeepInMemory, cacheInMemoryForShortLivedProcesses))
                );
            }
        };
        executionHistoryStore = new DefaultExecutionHistoryStore(executionHistoryCacheAccess, stringInterner, CACHE_PREFIX);
    }

    private CleanupAction createCleanupAction(File filesOutputDirectory, FileAccessTimeJournal fileAccessTimeJournal) {
        return CompositeCleanupAction.builder()
            .add(filesOutputDirectory, new LeastRecentlyUsedCacheCleanup(new SingleDepthFilesFinder(FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP), fileAccessTimeJournal, DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES))
            .build();
    }

    @Override
    public Optional<AfterPreviousExecutionState> getPreviousExecution(HashCode cacheKey) {
        Optional<AfterPreviousExecutionState> previousExecutionState = Optional.ofNullable(executionHistoryStore.load(cacheKey.toString()));
        previousExecutionState.ifPresent(execution -> fileAccessTracker.markAccessed(extractOutputFiles(execution.getOutputFileProperties())));
        return previousExecutionState;
    }

    private Collection<File> extractOutputFiles(ImmutableSortedMap<String, ? extends FileCollectionFingerprint> outputFileProperties) {
        return outputFileProperties.values().stream()
            .flatMap(DefaultTransformerExecutionHistoryRepository::extractRootPaths)
            .map(File::new)
            .collect(Collectors.toList());
    }

    private static Stream<String> extractRootPaths(FileCollectionFingerprint fingerprint) {
        return fingerprint.getRootHashes().keySet().stream();
    }

    @Override
    public void persist(HashCode cacheKey, OriginMetadata originMetadata, ImplementationSnapshot implementationSnapshot, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFingerprints, boolean successful) {
        fileAccessTracker.markAccessed(extractOutputFiles(outputFingerprints));
        executionHistoryStore.store(cacheKey.toString(), originMetadata, implementationSnapshot, ImmutableList.of(), ImmutableSortedMap.of(), ImmutableSortedMap.of(), outputFingerprints, successful);
    }

    @Override
    public File getOutputDirectory(File toBeTransformed, String cacheKey) {
        return new File(filesOutputDirectory, toBeTransformed.getName() + "/" + cacheKey);
    }

    @Override
    public void close() {
        cache.close();
    }

}
