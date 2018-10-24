/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
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
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.execution.CacheHandler;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.impl.steps.UpToDateResult;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.resource.local.DefaultPathKeyFileStore;
import org.gradle.internal.resource.local.FileAccessTimeJournal;
import org.gradle.internal.resource.local.FileAccessTracker;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.FileStoreAddActionException;
import org.gradle.internal.resource.local.SingleDepthFileAccessTracker;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotter;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.gradle.api.internal.artifacts.ivyservice.CacheLayout.TRANSFORMS_META_DATA;
import static org.gradle.api.internal.artifacts.ivyservice.CacheLayout.TRANSFORMS_STORE;
import static org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup.DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultCachingTransformerExecutor implements CachingTransformerExecutor, Stoppable, RootBuildLifecycleListener {

    private static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 2;
    private static final String CACHE_PREFIX = TRANSFORMS_META_DATA.getKey() + "/";

    private final PersistentCache cache;
    private final PersistentIndexedCache<HashCode, List<File>> indexedCache;
    private final FileStore<String> fileStore;
    private final ProducerGuard<CacheKey> producing = ProducerGuard.adaptive();
    private final Map<CacheKey, List<File>> resultHashToResult = new ConcurrentHashMap<CacheKey, List<File>>();
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final FileAccessTracker fileAccessTracker;
    private final WorkExecutor<UpToDateResult> workExecutor;

    public DefaultCachingTransformerExecutor(WorkExecutor<UpToDateResult> workExecutor, ArtifactCacheMetadata artifactCacheMetadata, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory cacheDecoratorFactory,
                                             FileSystemSnapshotter fileSystemSnapshotter, FileAccessTimeJournal fileAccessTimeJournal) {
        this.workExecutor = workExecutor;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        File transformsStoreDirectory = artifactCacheMetadata.getTransformsStoreDirectory();
        File filesOutputDirectory = new File(transformsStoreDirectory, TRANSFORMS_STORE.getKey());
        fileStore = new DefaultPathKeyFileStore(filesOutputDirectory);
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
    public void stop() {
        cache.close();
    }

    @Override
    public void afterStart() {
    }

    @Override
    public void beforeComplete() {
        // Discard cached results between builds
        resultHashToResult.clear();
    }

    @Override
    public boolean contains(File absoluteFile, HashCode inputsHash) {
        return resultHashToResult.containsKey(getCacheKey(absoluteFile, inputsHash));
    }

    @Override
    public List<File> getResult(File primaryInput, Transformer transformer) {
        File absolutePrimaryInput = primaryInput.getAbsoluteFile();
        CacheKey cacheKey = getCacheKey(absolutePrimaryInput, transformer);
        List<File> transformedFiles = resultHashToResult.get(cacheKey);
        if (transformedFiles != null) {
            return transformedFiles;
        }
        return loadIntoCache(absolutePrimaryInput, cacheKey, transformer);
    }

    /**
     * Loads the transformed files from the file system cache into memory. Creates them if they are not present yet.
     * This makes sure that only one thread tries to load a result for a given key.
     */
    private List<File> loadIntoCache(final File inputFile, final CacheKey cacheKey, final Transformer transformer) {
        return producing.guardByKey(cacheKey, new Factory<List<File>>() {
            @Override
            public List<File> create() {
                List<File> files = resultHashToResult.get(cacheKey);
                if (files != null) {
                    return files;
                }
                files = cache.withFileLock(new Factory<List<File>>() {
                    @Override
                    public List<File> create() {
                        HashCode persistentCacheKey = cacheKey.getPersistentCacheKey();
                        List<File> files = indexedCache.get(persistentCacheKey);
                        if (files != null) {
                            boolean allExist = true;
                            for (File file : files) {
                                if (!file.exists()) {
                                    allExist = false;
                                    break;
                                }
                            }
                            if (allExist) {
                                return files;
                            }
                        }

                        String key = inputFile.getName() + "/" + persistentCacheKey;
                        TransformAction action = new TransformAction(transformer, inputFile);
                        try {
                            fileStore.add(key, action);
                        } catch (FileStoreAddActionException e) {
                            throw UncheckedException.throwAsUncheckedException(e.getCause());
                        }

                        indexedCache.put(persistentCacheKey, action.result);
                        return action.result;
                    }
                });

                fileAccessTracker.markAccessed(files);
                resultHashToResult.put(cacheKey, files);
                return files;
            }
        });
    }
    
    private CacheKey getCacheKey(File primaryInput, Transformer transformer) {
        return getCacheKey(primaryInput, transformer.getSecondaryInputHash());
    }

    private CacheKey getCacheKey(File inputFile, HashCode inputsHash) {
        FileSystemLocationSnapshot snapshot = fileSystemSnapshotter.snapshot(inputFile);
        return new CacheKey(inputsHash, snapshot.getAbsolutePath(), snapshot.getHash());
    }

    /**
     * A lightweight key for in-memory caching of transformation results.
     * Computing the hash key for the persistent cache is a rather expensive
     * operation, so we only calculate it when we have a cache miss in memory.
     */
    private static class CacheKey {
        private final String absolutePath;
        private final HashCode fileContentHash;
        private final HashCode inputHash;

        public CacheKey(HashCode inputHash, String absolutePath, HashCode fileContentHash) {
            this.absolutePath = absolutePath;
            this.fileContentHash = fileContentHash;
            this.inputHash = inputHash;
        }

        public HashCode getPersistentCacheKey() {
            Hasher hasher = Hashing.newHasher();
            hasher.putHash(inputHash);
            hasher.putString(absolutePath);
            hasher.putHash(fileContentHash);
            return hasher.hash();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CacheKey cacheKey = (CacheKey) o;

            if (!fileContentHash.equals(cacheKey.fileContentHash)) {
                return false;
            }
            if (!inputHash.equals(cacheKey.inputHash)) {
                return false;
            }
            return absolutePath.equals(cacheKey.absolutePath);
        }

        @Override
        public int hashCode() {
            int result = fileContentHash.hashCode();
            result = 31 * result + absolutePath.hashCode();
            result = 31 * result + inputHash.hashCode();
            return result;
        }
    }

    private class TransformAction implements Action<File> {
        private final Transformer transformer;
        private final File primarInput;
        private ImmutableList<File> result;

        public TransformAction(Transformer transformer, File primarInput) {
            this.transformer = transformer;
            this.primarInput = primarInput;
        }

        @Override
        public void execute(final File outputDir) {
            UpToDateResult result = workExecutor.execute(new TransformerExecution(primarInput, outputDir, transformer, (files) -> this.result = files));
            if (result.getFailure() != null) {
                throw UncheckedException.throwAsUncheckedException(result.getFailure());
            }
        }
    }

    private class TransformerExecution implements UnitOfWork {
        private final File primaryInput;
        private final File outputDir;
        private final Transformer transformer;
        private final Consumer<ImmutableList<File>> resultHandler;

        public TransformerExecution(File primaryInput, File outputDir, Transformer transformer, Consumer<ImmutableList<File>> resultHandler) {
            this.primaryInput = primaryInput;
            this.outputDir = outputDir;
            this.transformer = transformer;
            this.resultHandler = resultHandler;
        }

        @Override
        public boolean execute() {
            ImmutableList<File> result = ImmutableList.copyOf(transformer.transform(primaryInput, outputDir));
            resultHandler.accept(result);
            return true;
        }

        @Override
        public Optional<Duration> getTimeout() {
            return Optional.empty();
        }

        @Override
        public void visitOutputs(OutputVisitor outputVisitor) {
            outputVisitor.visitOutput("output", TreeType.DIRECTORY, ImmutableFileCollection.of(outputDir));
        }

        @Override
        public long markExecutionTime() {
            // TODO Handle execution time
            return 0;
        }

        @Override
        public FileCollection getLocalState() {
            return ImmutableFileCollection.of();
        }

        @Override
        public void afterOutputsRemovedBeforeTask() {
        }

        @Override
        public CacheHandler createCacheHandler() {
            return new CacheHandler() {
                @Override
                public <T> Optional<T> load(Function<BuildCacheKey, T> loader) {
                    return Optional.empty();
                }

                @Override
                public void store(Consumer<BuildCacheKey> storer) {
                }
            };
        }

        @Override
        public void persistResult(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs, boolean successful, OriginMetadata originMetadata) {
        }

        @Override
        public Optional<ExecutionStateChanges> getChangesSincePreviousExecution() {
            return Optional.empty();
        }

        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterOutputsGenerated() {
            return ImmutableSortedMap.of();
        }

        @Override
        public String getIdentity() {
            return "fake";
        }

        @Override
        public void visitTrees(CacheableTreeVisitor visitor) {
            throw new UnsupportedOperationException("we don't cache yet");
        }

        @Override
        public String getDisplayName() {
            return transformer.getDisplayName() + ": " + primaryInput;
        }
    }
}
