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

package org.gradle.caching.internal.version2;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.Function;
import java8.util.function.Supplier;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.state.FileSystemMirror;
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata;
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.execution.TaskOutputChangesListener;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.OutputPropertySpec;
import org.gradle.caching.internal.tasks.AbstractLoadCommand;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginFactory;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader;
import org.gradle.internal.hash.HashCode;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;SkipCachedTaskExecuter.java
import java.util.SortedSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskOutputCacheCommandFactoryV2 implements Closeable {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final TaskOutputOriginFactory taskOutputOriginFactory;
    private final FileSystemMirror fileSystemMirror;
    private final StringInterner stringInterner;
    private final LocalBuildCacheServiceV2 local;

    public TaskOutputCacheCommandFactoryV2(TaskOutputOriginFactory taskOutputOriginFactory, FileSystemMirror fileSystemMirror, StringInterner stringInterner, LocalBuildCacheServiceV2 local) {
        this.taskOutputOriginFactory = taskOutputOriginFactory;
        this.fileSystemMirror = fileSystemMirror;
        this.stringInterner = stringInterner;
        this.local = local;
    }

    public BuildCacheLoadCommandV2<OriginTaskExecutionMetadata> createLoad(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, FileCollection localStateFiles, TaskOutputChangesListener taskOutputChangesListener, TaskArtifactState taskArtifactState) {
        return new LoadCommand(cacheKey, outputProperties, task, localStateFiles, taskOutputChangesListener, taskArtifactState);
    }

    private class LoadCommand extends AbstractLoadCommand<Void, OriginTaskExecutionMetadata> implements BuildCacheLoadCommandV2<OriginTaskExecutionMetadata> {
        public LoadCommand(BuildCacheKey key, SortedSet<? extends OutputPropertySpec> outputProperties, TaskInternal task, FileCollection localStateFiles, TaskOutputChangesListener taskOutputChangesListener, TaskArtifactState taskArtifactState) {
            super(key, outputProperties, task, localStateFiles, taskOutputChangesListener, taskArtifactState, fileSystemMirror, stringInterner, taskOutputOriginFactory);
        }

        @Override
        public Result<OriginTaskExecutionMetadata> load() {
            final OriginTaskExecutionMetadata metadata = performLoad(null);
            return new Result<OriginTaskExecutionMetadata>() {
                @Override
                public OriginTaskExecutionMetadata getMetadata() {
                    return metadata;
                }
            };
        }

        @Override
        protected OriginTaskExecutionMetadata performLoad(Void input, final SortedSet<? extends OutputPropertySpec> outputProperties, final TaskOutputOriginReader reader) throws IOException {
            final HashCode resultKey = HashCode.fromString(getKey().getHashCode());
            return CompletableFuture
                .supplyAsync(new Supplier<ResultEntry>() {
                    @Override
                    public ResultEntry get() {
                        CacheEntry entry = local.get(resultKey);
                        if (entry == null || entry instanceof ResultEntry) {
                            return (ResultEntry) entry;
                        } else {
                            throw new IllegalStateException("Found an entry of unknown type for " + resultKey);
                        }
                    }
                }, executor)
                .thenCompose(new Function<ResultEntry, CompletableFuture<OriginTaskExecutionMetadata>>() {
                    @Override
                    public CompletableFuture<OriginTaskExecutionMetadata> apply(final ResultEntry result) {
                        Map<String, HashCode> outputs = result.getOutputs();
                        List<CompletableFuture<Void>> childEntries = Lists.newArrayListWithCapacity(outputs.size());
                        for (OutputPropertySpec propertySpec : outputProperties) {
                            HashCode outputKey = outputs.get(propertySpec.getPropertyName());
                            if (outputKey == null) {
                                // Optional outputs are missing
                                FileUtils.deleteQuietly(propertySpec.getOutputRoot());
                                continue;
                            }
                            CompletableFuture<Void> childEntry = CompletableFuture.runAsync(
                                new LoadEntry(outputKey, propertySpec.getOutputRoot()), executor
                            );
                            childEntries.add(childEntry);
                        }
                        return CompletableFuture
                            .allOf(Iterables.toArray(childEntries, CompletableFuture.class))
                            .thenApply(new Function<Void, OriginTaskExecutionMetadata>() {
                                @Override
                                public OriginTaskExecutionMetadata apply(Void aVoid) {
                                    return reader.execute(result.getOriginMetadata());
                                }
                            });
                    }
                }).join();
        }

        class LoadEntry implements Runnable {
            private HashCode key;
            private File target;

            public LoadEntry(HashCode key, File target) {
                this.key = key;
                this.target = target;
            }

            @Override
            public void run() {
                try {
                    load(key, target);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            private void load(HashCode key, File target) throws IOException {
                CacheEntry entry = local.get(key);
                if (entry instanceof FileEntry) {
                    InputStream inputStream = ((FileEntry) entry).read();
                    try {
                        FileUtils.deleteQuietly(target);
                        OutputStream outputStream = new FileOutputStream(target);
                        try {
                            ByteStreams.copy(inputStream, outputStream);
                        } finally {
                            IOUtils.closeQuietly(outputStream);
                        }
                    } finally {
                        IOUtils.closeQuietly(inputStream);
                    }
                } else if (entry instanceof ManifestEntry) {
                    ManifestEntry manifest = (ManifestEntry) entry;
                    ImmutableSortedMap<String, HashCode> childEntries = manifest.getChildren();
                    FileUtils.deleteQuietly(target);
                    FileUtils.forceMkdir(target);
                    for (Map.Entry<String, HashCode> childEntry : childEntries.entrySet()) {
                        load(childEntry.getValue(), new File(target, childEntry.getKey()));
                    }
                } else if (entry == null) {
                    throw new IllegalStateException("Entry not found: " + key);
                } else {
                    throw new IllegalStateException("Invalid entry type: " + entry.getClass().getName());
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        executor.shutdown();
    }
}
