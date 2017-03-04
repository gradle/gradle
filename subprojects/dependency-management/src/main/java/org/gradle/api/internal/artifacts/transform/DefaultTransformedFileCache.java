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
import com.google.common.hash.HashCode;
import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy;
import org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.util.BiFunction;

import java.io.File;
import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.CacheLayout.TRANSFORMS_META_DATA;
import static org.gradle.api.internal.artifacts.ivyservice.CacheLayout.TRANSFORMS_STORE;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultTransformedFileCache implements TransformedFileCache, Stoppable {
    private final GenericFileCollectionSnapshotter fileCollectionSnapshotter;
    private final PersistentCache cache;
    private final PersistentIndexedCache<HashCode, List<File>> indexedCache;
    private final File filesOutputDirectory;

    public DefaultTransformedFileCache(ArtifactCacheMetaData artifactCacheMetaData, GenericFileCollectionSnapshotter fileCollectionSnapshotter, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory cacheDecoratorFactory) {
        this.fileCollectionSnapshotter = fileCollectionSnapshotter;
        File transformsStoreDirectory = artifactCacheMetaData.getTransformsStoreDirectory();
        filesOutputDirectory = new File(transformsStoreDirectory, TRANSFORMS_STORE.getKey());
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
    public Transformer<List<File>, File> applyCaching(final HashCode inputsHash, final BiFunction<List<File>, File, File> transformer) {
        return new Transformer<List<File>, File>() {
            @Override
            public List<File> transform(File file) {
                // Snapshot the input files
                final File absoluteFile = file.getAbsoluteFile();
                FileCollectionSnapshot snapshot = fileCollectionSnapshotter.snapshot(new SimpleFileCollection(absoluteFile), TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE);

                DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
                hasher.putBytes(inputsHash.asBytes());
                snapshot.appendToHasher(hasher);

                final HashCode resultHash = hasher.hash();
                return indexedCache.get(resultHash, new Transformer<List<File>, HashCode>() {
                    @Override
                    public List<File> transform(HashCode hashCode) {
                        File outputDir = new File(filesOutputDirectory, absoluteFile.getName() + "/" + resultHash);
                        outputDir.mkdirs();
                        return ImmutableList.copyOf(transformer.apply(absoluteFile, outputDir));
                    }
                });
            }
        };
    }
}
