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
import com.google.common.collect.Maps;
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
import org.gradle.api.internal.changedetection.state.DirContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileHashSnapshot;
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
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("Since15")
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

    public BuildCacheStoreCommandV2 createStore(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, Map<String, Map<String, FileContentSnapshot>> outputSnapshots, TaskInternal task, long taskExecutionTime) {
        return new StoreCommand(cacheKey, outputProperties, outputSnapshots, task, taskExecutionTime);
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


    private class StoreCommand implements BuildCacheStoreCommandV2 {
        private final TaskOutputCachingBuildCacheKey cacheKey;
        private final SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties;
        private final Map<String, Map<String, FileContentSnapshot>> outputSnapshots;
        private final TaskInternal task;
        private final long taskExecutionTime;

        private StoreCommand(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, Map<String, Map<String, FileContentSnapshot>> outputSnapshots, TaskInternal task, long taskExecutionTime) {
            this.cacheKey = cacheKey;
            this.outputProperties = outputProperties;
            this.outputSnapshots = outputSnapshots;
            this.task = task;
            this.taskExecutionTime = taskExecutionTime;
        }

        @Override
        public Result store() {
            final AtomicInteger count = new AtomicInteger();
            ImmutableSortedMap.Builder<Comparable<?>, Object> outputHashes = ImmutableSortedMap.naturalOrder();
            for (final OutputPropertySpec outputProperty : outputProperties) {
                final File outputRoot = outputProperty.getOutputRoot();
                if (outputRoot == null) {
                    continue;
                }
                String outputPropertyName = outputProperty.getPropertyName();
                final Map<String, FileContentSnapshot> outputs = outputSnapshots.get(outputPropertyName);
                if (outputs == null) {
                    throw new IllegalStateException("Cannot find outputs for " + outputProperty);
                }
                if (outputs.isEmpty()) {
                    // TODO Support missing output
                    continue;
                }

                HashCode outputHash;
                switch (outputProperty.getOutputType()) {
                    case DIRECTORY:
                        Path rootPath = outputRoot.toPath();
                        DirectorySnapshotX root = new DirectorySnapshotX();
                        for (Map.Entry<String, FileContentSnapshot> entry : outputs.entrySet()) {
                            File absoluteFile = new File(entry.getKey());
                            Path relativePath = rootPath.relativize(absoluteFile.toPath());
                            Iterator<Path> iPathElements = relativePath.iterator();
                            DirectorySnapshotX parent = root;
                            FileContentSnapshot snapshot = entry.getValue();
                            while (iPathElements.hasNext()) {
                                Path element = iPathElements.next();
                                if (iPathElements.hasNext() || snapshot instanceof DirContentSnapshot) {
                                    // This is a directory
                                    parent = parent.getOrAddDirectory(element.toString());
                                } else if (snapshot instanceof FileHashSnapshot) {
                                    // This is the final file
                                    parent.addFile(element.toString(), snapshot.getContentMd5(), absoluteFile);
                                } else {
                                    throw new IllegalStateException("Invalid content snapshot type: " + snapshot);
                                }
                            }
                        }
                        outputHash = root.put(local);
                        break;
                    case FILE:
                        FileContentSnapshot fileSnapshot = Iterables.getOnlyElement(outputs.values());
                        outputHash = fileSnapshot.getContentMd5();
                        local.put(outputHash, outputRoot);
                        count.incrementAndGet();
                        break;
                    default:
                        throw new AssertionError();
                }
                outputHashes.put(outputPropertyName, outputHash);
            }
            return new Result() {
                @Override
                public long getArtifactEntryCount() {
                    return count.get();
                }
            };
        }

        private abstract class SnapshotX {
            public abstract HashCode put(LocalBuildCacheServiceV2 local);
        }

        private class DirectorySnapshotX extends SnapshotX {
            private final Map<String, SnapshotX> children = Maps.newHashMap();

            public DirectorySnapshotX getOrAddDirectory(String name) {
                SnapshotX entry = children.get(name);
                if (entry == null) {
                    DirectorySnapshotX directory = new DirectorySnapshotX();
                    children.put(name, directory);
                    return directory;
                } else if (entry instanceof DirectorySnapshotX) {
                    return (DirectorySnapshotX) entry;
                } else {
                    throw new IllegalStateException("Incorrect child type");
                }
            }

            public void addFile(String name, HashCode hashCode, File file) {
                children.put(name, new FileSnapshotX(hashCode, file));
            }

            @Override
            public HashCode put(LocalBuildCacheServiceV2 local) {
                ImmutableSortedMap.Builder<String, HashCode> childHashes = ImmutableSortedMap.naturalOrder();
                Hasher hasher = Hashing.md5().newHasher();
                for (Map.Entry<String, SnapshotX> entry : children.entrySet()) {
                    String childName = entry.getKey();
                    SnapshotX child = entry.getValue();
                    HashCode childHash = child.put(local);
                    hasher.putString(entry.getKey());
                    hasher.putHash(childHash);
                    childHashes.put(childName, childHash);
                }
                HashCode hashCode = hasher.hash();
                local.put(hashCode, childHashes.build());
                return hashCode;
            }
        }

        private class FileSnapshotX extends SnapshotX {
            private final HashCode hashCode;
            private File file;

            public FileSnapshotX(HashCode hashCode, File file) {
                this.hashCode = hashCode;
                this.file = file;
            }

            @Override
            public HashCode put(LocalBuildCacheServiceV2 local) {
                local.put(hashCode, file);
                return hashCode;
            }
        }
    }

    @Override
    public void close() throws IOException {
        executor.shutdown();
    }
}
