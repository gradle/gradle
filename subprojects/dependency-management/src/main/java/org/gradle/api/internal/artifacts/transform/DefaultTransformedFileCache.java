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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy;
import org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.util.BiFunction;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class DefaultTransformedFileCache implements TransformedFileCache {
    private final Cache<HashCode, List<File>> results = CacheBuilder.newBuilder().build();
    private final ArtifactCacheMetaData artifactCacheMetaData;
    private final GenericFileCollectionSnapshotter fileCollectionSnapshotter;

    public DefaultTransformedFileCache(ArtifactCacheMetaData artifactCacheMetaData, GenericFileCollectionSnapshotter fileCollectionSnapshotter) {
        this.artifactCacheMetaData = artifactCacheMetaData;
        this.fileCollectionSnapshotter = fileCollectionSnapshotter;
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

                try {
                    return results.get(resultHash, new Callable<List<File>>() {
                        @Override
                        public List<File> call() {
                            File outputDir = new File(artifactCacheMetaData.getTransformsStoreDirectory(), absoluteFile.getName() + "/" + resultHash);
                            outputDir.mkdirs();

                            return ImmutableList.copyOf(transformer.apply(absoluteFile, outputDir));
                        }
                    });
                } catch (ExecutionException e) {
                    throw UncheckedException.throwAsUncheckedException(e.getCause());
                } catch (UncheckedExecutionException e) {
                    throw UncheckedException.throwAsUncheckedException(e.getCause());
                }
            }
        };
    }
}
