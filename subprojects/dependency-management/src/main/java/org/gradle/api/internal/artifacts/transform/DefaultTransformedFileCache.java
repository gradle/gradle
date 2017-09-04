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
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.internal.changedetection.state.Snapshot;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.local.DefaultPathKeyFileStore;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.FileStoreAddActionException;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.util.BiFunction;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.api.internal.artifacts.ivyservice.CacheLayout.TRANSFORMS_META_DATA;
import static org.gradle.api.internal.artifacts.ivyservice.CacheLayout.TRANSFORMS_STORE;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultTransformedFileCache implements TransformedFileCache, Stoppable, RootBuildLifecycleListener {
    private final PersistentCache cache;
    private final PersistentIndexedCache<HashCode, List<File>> indexedCache;
    private final FileStore<String> fileStore;
    private final ProducerGuard<HashCode> producing = ProducerGuard.adaptive();
    private final Map<HashCode, List<File>> resultHashToResult = new ConcurrentHashMap<HashCode, List<File>>();
    private final FileSystemSnapshotter fileSystemSnapshotter;

    public DefaultTransformedFileCache(ArtifactCacheMetaData artifactCacheMetaData, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory cacheDecoratorFactory, FileSystemSnapshotter fileSystemSnapshotter) {
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        File transformsStoreDirectory = artifactCacheMetaData.getTransformsStoreDirectory();
        File filesOutputDirectory = new File(transformsStoreDirectory, TRANSFORMS_STORE.getKey());
        fileStore = new DefaultPathKeyFileStore(filesOutputDirectory);
        cache = cacheRepository
                .cache(transformsStoreDirectory)
                .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
                .withDisplayName("Artifact transforms cache")
                .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
                .open();
        String cacheName = TRANSFORMS_META_DATA.getKey() + "/results";
        PersistentIndexedCacheParameters<HashCode, List<File>> cacheParameters = new PersistentIndexedCacheParameters<HashCode, List<File>>(cacheName, new HashCodeSerializer(), new ListSerializer<File>(BaseSerializerFactory.FILE_SERIALIZER))
                .cacheDecorator(cacheDecoratorFactory.decorator(1000, true));
        indexedCache = cache.createCache(cacheParameters);
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
    public List<File> getResult(final File inputFile, HashCode inputsHash, final BiFunction<List<File>, File, File> transformer) {
        // Collect up hash of the input files and of the transform's configuration params and implementation to calculate the key
        Snapshot inputFileSnapshot = fileSystemSnapshotter.snapshotAll(inputFile);
        DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
        hasher.putHash(inputsHash);
        inputFileSnapshot.appendToHasher(hasher);
        final HashCode resultHash = hasher.hash();

        // Apply locking so that only this process is writing to the file store and only a single thread is running this particular transform
        return producing.guardByKey(resultHash, new Factory<List<File>>() {
            @Override
            public List<File> create() {
                List<File> files = resultHashToResult.get(resultHash);
                if (files != null) {
                    return files;
                }

                files = cache.withFileLock(new Factory<List<File>>() {
                    @Override
                    public List<File> create() {
                        List<File> files = indexedCache.get(resultHash);
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
                            // Else, recreate outputs
                        }

                        // File store takes care of cleaning up on failure/crash
                        String key = inputFile.getName() + "/" + resultHash;
                        TransformAction action = new TransformAction(transformer, inputFile);
                        try {
                            fileStore.add(key, action);
                        } catch (FileStoreAddActionException e) {
                            throw UncheckedException.throwAsUncheckedException(e.getCause());
                        }

                        indexedCache.put(resultHash, action.result);
                        return action.result;
                    }
                });

                resultHashToResult.put(resultHash, files);
                return files;
            }
        });
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
