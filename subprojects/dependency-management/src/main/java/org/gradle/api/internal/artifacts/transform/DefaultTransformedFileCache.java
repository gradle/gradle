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
import org.gradle.api.Action;
import org.gradle.api.artifacts.transform.TransformInvocationException;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata;
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.CompositeCleanupAction;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
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
import org.gradle.internal.util.BiFunction;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.api.internal.artifacts.ivyservice.CacheLayout.TRANSFORMS_META_DATA;
import static org.gradle.api.internal.artifacts.ivyservice.CacheLayout.TRANSFORMS_STORE;
import static org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup.DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultTransformedFileCache implements TransformedFileCache, Stoppable, RootBuildLifecycleListener {

    private static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 2;
    private static final String CACHE_PREFIX = TRANSFORMS_META_DATA.getKey() + "/";

    private final PersistentCache cache;
    private final PersistentIndexedCache<HashCode, List<File>> indexedCache;
    private final FileStore<String> fileStore;
    private final ProducerGuard<CacheKey> producing = ProducerGuard.adaptive();
    private final Map<CacheKey, List<File>> resultHashToResult = new ConcurrentHashMap<CacheKey, List<File>>();
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final FileAccessTracker fileAccessTracker;

    public DefaultTransformedFileCache(ArtifactCacheMetadata artifactCacheMetadata, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory cacheDecoratorFactory,
                                       FileSystemSnapshotter fileSystemSnapshotter, FileAccessTimeJournal fileAccessTimeJournal) {
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
        indexedCache = cache.createCache(PersistentIndexedCacheParameters.of(CACHE_PREFIX + "results", new HashCodeSerializer(), new ListSerializer<File>(BaseSerializerFactory.FILE_SERIALIZER))
            .cacheDecorator(cacheDecoratorFactory.decorator(1000, true)));
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
    public List<File> runTransformer(File primaryInput, TransformerRegistration transformerRegistration) {
        File absolutePrimaryInput = primaryInput.getAbsoluteFile();
        try {
            CacheKey cacheKey = getCacheKey(absolutePrimaryInput, transformerRegistration);
            List<File> transformedFiles = resultHashToResult.get(cacheKey);
            if (transformedFiles != null) {
                return transformedFiles;
            }
            return loadIntoCache(absolutePrimaryInput, cacheKey, transformerRegistration);
        } catch (Throwable t) {
            throw new TransformInvocationException(absolutePrimaryInput, transformerRegistration.getImplementationClass(), t);
        }
    }

    /*
     * Loads the transformed files from the file system cache into memory. Creates them if they are not present yet.
     * This makes sure that only one thread tries to load a result for a given key.
     */

    private List<File> loadIntoCache(final File inputFile, final CacheKey cacheKey, final BiFunction<List<File>, File, File> transformer) {
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
    
    private CacheKey getCacheKey(File primaryInput, TransformerRegistration transformerRegistration) {
        return getCacheKey(primaryInput, transformerRegistration.getInputsHash());
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

    private static class TransformAction implements Action<File> {
        private final BiFunction<List<File>, File, File> transformer;
        private final File inputFile;
        private ImmutableList<File> result;

        TransformAction(BiFunction<List<File>, File, File> transformer, File inputFile) {
            this.transformer = transformer;
            this.inputFile = inputFile;
        }

        @Override
        public void execute(File outputDir) {
            outputDir.mkdirs();
            result = ImmutableList.copyOf(transformer.apply(inputFile, outputDir));
        }
    }
}
