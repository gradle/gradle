/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.internal.controller;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Closer;
import com.google.common.io.CountingInputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.DefaultBuildCacheKey;
import org.gradle.caching.internal.NextGenBuildCacheService;
import org.gradle.caching.internal.controller.CacheManifest.ManifestEntry;
import org.gradle.caching.internal.controller.operations.LoadOperationDetails;
import org.gradle.caching.internal.controller.operations.LoadOperationHitResult;
import org.gradle.caching.internal.controller.operations.LoadOperationMissResult;
import org.gradle.caching.internal.controller.operations.PackOperationDetails;
import org.gradle.caching.internal.controller.operations.PackOperationResult;
import org.gradle.caching.internal.controller.operations.UnpackOperationDetails;
import org.gradle.caching.internal.controller.operations.UnpackOperationResult;
import org.gradle.caching.internal.controller.service.BuildCacheLoadResult;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.packaging.impl.RelativePathParser;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.file.BufferProvider;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.file.impl.DefaultFileMetadata;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.snapshot.DirectorySnapshotBuilder;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.RelativePathTracker;
import org.gradle.internal.snapshot.SnapshotUtil;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.vfs.FileSystemAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.gradle.internal.file.FileMetadata.AccessType.DIRECT;
import static org.gradle.internal.file.FileType.Directory;
import static org.gradle.internal.snapshot.DirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.INCLUDE_EMPTY_DIRS;

public class NextGenBuildCacheController implements BuildCacheController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NextGenBuildCacheController.class);

    public static final String NEXT_GEN_CACHE_SYSTEM_PROPERTY = "org.gradle.unsafe.cache.ng";

    private final BufferProvider bufferProvider;
    private final BuildOperationExecutor buildOperationExecutor;
    private final NextGenBuildCacheAccess cacheAccess;
    private final FileSystemAccess fileSystemAccess;
    private final String buildInvocationId;
    private final Deleter deleter;
    private final StringInterner stringInterner;
    private final Gson gson;

    public NextGenBuildCacheController(
        String buildInvocationId,
        Deleter deleter,
        FileSystemAccess fileSystemAccess,
        BufferProvider bufferProvider,
        StringInterner stringInterner,
        BuildOperationExecutor buildOperationExecutor,
        NextGenBuildCacheAccess cacheAccess
    ) {
        this.buildInvocationId = buildInvocationId;
        this.deleter = deleter;
        this.fileSystemAccess = fileSystemAccess;
        this.bufferProvider = bufferProvider;
        this.buildOperationExecutor = buildOperationExecutor;
        this.cacheAccess = cacheAccess;
        this.stringInterner = stringInterner;
        this.gson = new GsonBuilder()
            .registerTypeAdapter(Duration.class, new TypeAdapter<Duration>() {
                @Override
                public void write(JsonWriter out, Duration value) throws IOException {
                    out.value(value.toMillis());
                }

                @Override
                public Duration read(JsonReader in) throws IOException {
                    return Duration.ofMillis(in.nextLong());
                }
            })
            .registerTypeAdapter(HashCode.class, new TypeAdapter<HashCode>() {
                @Override
                public void write(JsonWriter out, HashCode value) throws IOException {
                    out.value(value.toString());
                }

                @Override
                public HashCode read(JsonReader in) throws IOException {
                    return HashCode.fromString(in.nextString());
                }
            })
            .create();

        LOGGER.warn("Creating next-generation build cache controller");
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isEmitDebugLogging() {
        return false;
    }

    @Override
    public Optional<BuildCacheLoadResult> load(BuildCacheKey manifestCacheKey, CacheableEntity cacheableEntity) {
        try (OperationFiringLoadHandlerFactory handlerFactory = new OperationFiringLoadHandlerFactory()) {
            // TODO Make load() return T
            AtomicReference<CacheManifest> manifestRef = new AtomicReference<>();
            AtomicLong manifestSize = new AtomicLong(-1L);
            cacheAccess.load(Collections.singletonMap(manifestCacheKey, null), handlerFactory.create((manifestStream, __) -> {
                CountingInputStream counterStream = new CountingInputStream(manifestStream);
                manifestRef.set(gson.fromJson(new InputStreamReader(counterStream), CacheManifest.class));
                manifestSize.set(counterStream.getCount());
            }));
            CacheManifest manifest = manifestRef.get();
            if (manifest == null) {
                return Optional.empty();
            }

            return Optional.of(loadInUnpackOperation(manifestCacheKey, cacheableEntity, manifest, manifestSize.get(), handlerFactory));
        }
    }

    private class OperationFiringLoadHandlerFactory implements Closeable {
        private volatile BuildOperationContext remoteBuildOp;
        private final AtomicLong totalSize = new AtomicLong(0L);
        private final AtomicBoolean missEncountered = new AtomicBoolean(false);
        private final List<Throwable> errors = new CopyOnWriteArrayList<>();

        public <T> NextGenBuildCacheAccess.LoadHandler<T> create(BiConsumer<InputStream, T> delegate) {
            return new NextGenBuildCacheAccess.LoadHandler<T>() {
                @Override
                public void handle(InputStream input, T payload) {
                    delegate.accept(input, payload);
                }

                @Override
                public void startRemoteDownload(BuildCacheKey key) {
                    if (remoteBuildOp == null) {
                        synchronized (OperationFiringLoadHandlerFactory.this) {
                            if (remoteBuildOp == null) {
                                remoteBuildOp = buildOperationExecutor.start(
                                    BuildOperationDescriptor.displayName("Load entry " + key.getDisplayName() + " from remote build cache")
                                        .details(new LoadOperationDetails(key))
                                        .progressDisplayName("Requesting from remote build cache"));
                            }
                        }
                    }
                }

                @Override
                public void recordRemoteHit(BuildCacheKey key, long size) {
                    totalSize.addAndGet(size);
                }

                @Override
                public void recordRemoteMiss(BuildCacheKey key) {
                    missEncountered.set(true);
                }

                @Override
                public void recordRemoteFailure(BuildCacheKey key, Throwable failure) {
                    errors.add(failure);
                }
            };
        }

        @Override
        public void close() {
            if (remoteBuildOp != null) {
                if (!errors.isEmpty()) {
                    DefaultMultiCauseException failure = new DefaultMultiCauseException("Errors encountered while loading entries from remote cache", errors);
                    remoteBuildOp.failed(failure);
                    throw failure;
                } else if (missEncountered.get()) {
                    remoteBuildOp.setResult(LoadOperationMissResult.INSTANCE);
                } else {
                    remoteBuildOp.setResult(new LoadOperationHitResult(totalSize.get()));
                }
            }
        }
    }

    private BuildCacheLoadResult loadInUnpackOperation(BuildCacheKey manifestCacheKey, CacheableEntity cacheableEntity, CacheManifest manifest, long manifestSize, OperationFiringLoadHandlerFactory handlerFactory) {
        return buildOperationExecutor.call(new CallableBuildOperation<BuildCacheLoadResult>() {
            @Override
            public BuildCacheLoadResult call(BuildOperationContext context) {
                BuildCacheLoadResult result = loadInner(cacheableEntity, manifest, handlerFactory);
                context.setResult(new UnpackOperationResult(result.getArtifactEntryCount()));
                return result;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                long totalSize = manifest.getPropertyManifests().values().stream()
                    .flatMap(List::stream)
                    .map(ManifestEntry::getLength)
                    .reduce(manifestSize, Long::sum);
                // TODO Use "load" instead of "unpack" here
                return BuildOperationDescriptor.displayName("Unpack build cache entry " + manifestCacheKey.getHashCode())
                    .details(new UnpackOperationDetails(manifestCacheKey, totalSize))
                    .progressDisplayName("Unpacking build cache entry");
            }
        });
    }

    private BuildCacheLoadResult loadInner(CacheableEntity cacheableEntity, CacheManifest manifest, OperationFiringLoadHandlerFactory handlerFactory) {
        // We already loaded the manifest, so that's 1
        AtomicLong loadedEntryCount = new AtomicLong(1L);
        ImmutableSortedMap.Builder<String, FileSystemSnapshot> snapshots = ImmutableSortedMap.naturalOrder();

        cacheableEntity.visitOutputTrees((propertyName, type, root) -> {
            // Invalidate VFS
            fileSystemAccess.write(Collections.singleton(root.getAbsolutePath()), () -> {});

            // TODO Apply diff to outputs instead of clearing them here and loading everything
            try {
                cleanOutputDirectory(type, root);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            // Note that there can be multiple output files with the same content
            ImmutableListMultimap.Builder<BuildCacheKey, File> filesBuilder = ImmutableListMultimap.builder();
            List<ManifestEntry> manifestEntries = manifest.getPropertyManifests().get(propertyName);
            manifestEntries.forEach(entry -> {
                File file = new File(root, entry.getRelativePath());
                switch (entry.getType()) {
                    case Directory:
                        // TODO set correct file permissions
                        // TODO Handle this
                        //noinspection ResultOfMethodCallIgnored
                        file.mkdirs();
                        break;
                    case RegularFile:
                        // TODO set correct file permissions
                        filesBuilder.put(new DefaultBuildCacheKey(entry.getContentHash()), file);
                        break;
                    case Missing:
                        FileUtils.deleteQuietly(file);
                        break;
                }
            });

            // TODO Filter out entries that are already in the right place in the output directory
            cacheAccess.load(filesBuilder.build().asMap(), handlerFactory.create((input, filesForHash) -> {
                loadedEntryCount.addAndGet(filesForHash.size());

                try (Closer closer = Closer.create()) {
                    OutputStream output = filesForHash.stream()
                        .map(file -> {
                            try {
                                return closer.register(new FileOutputStream(file));
                            } catch (FileNotFoundException e) {
                                throw new UncheckedIOException("Couldn't create " + file.getAbsolutePath(), e);
                            }
                        })
                        .map(OutputStream.class::cast)
                        .reduce(TeeOutputStream::new)
                        .orElse(NullOutputStream.NULL_OUTPUT_STREAM);

                    IOUtils.copyLarge(input, output, bufferProvider.getBuffer());
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }));

            createSnapshot(type, root, manifestEntries)
                .ifPresent(snapshot -> {
                    snapshots.put(propertyName, snapshot);
                    fileSystemAccess.record(snapshot);
                });
        });

        ImmutableSortedMap<String, FileSystemSnapshot> resultingSnapshots = snapshots.build();
        return new BuildCacheLoadResult() {
            @Override
            public long getArtifactEntryCount() {
                return loadedEntryCount.get();
            }

            @Override
            public OriginMetadata getOriginMetadata() {
                return manifest.getOriginMetadata();
            }

            @Override
            public ImmutableSortedMap<String, FileSystemSnapshot> getResultingSnapshots() {
                return resultingSnapshots;
            }
        };
    }

    // TODO Extract snapshotting part to it's own class
    @VisibleForTesting
    Optional<FileSystemLocationSnapshot> createSnapshot(TreeType type, File root, List<ManifestEntry> entries) {
        switch (type) {
            case DIRECTORY:
                return Optional.of(createDirectorySnapshot(root, entries));
            case FILE:
                if (entries.size() != 1) {
                    throw new IllegalStateException("Expected a single manifest entry, found " + entries.size());
                }
                ManifestEntry rootEntry = entries.get(0);
                switch (rootEntry.getType()) {
                    case Directory:
                        throw new IllegalStateException("Directory manifest entry found for a file output");
                    case RegularFile:
                        return Optional.of(createFileSnapshot(rootEntry, root));
                    case Missing:
                        // No need to create a
                        return Optional.empty();
                    default:
                        throw new AssertionError("Unknown manifest entry type " + rootEntry.getType());
                }
            default:
                throw new AssertionError("Unknown output type " + type);
        }
    }

    // TODO We should not capture any snapshot for a missing directory output
    private FileSystemLocationSnapshot createDirectorySnapshot(File root, List<ManifestEntry> entries) {
        String rootPath = root.getName() + "/";
        RelativePathParser parser = new RelativePathParser(rootPath);
        DirectorySnapshotBuilder builder = MerkleDirectorySnapshotBuilder.noSortingRequired();
        builder.enterDirectory(DIRECT, stringInterner.intern(root.getAbsolutePath()), stringInterner.intern(root.getName()), INCLUDE_EMPTY_DIRS);

        for (ManifestEntry entry : Iterables.skip(entries, 1)) {
            File file = new File(root, entry.getRelativePath());

            boolean isDirectory = entry.getType() == Directory;
            String relativePath = isDirectory
                ? rootPath + entry.getRelativePath() + "/"
                : rootPath + entry.getRelativePath();
            boolean outsideOfRoot = parser.nextPath(relativePath, isDirectory, builder::leaveDirectory);
            if (outsideOfRoot) {
                break;
            }

            switch (entry.getType()) {
                case Directory:
                    String internedAbsolutePath = stringInterner.intern(file.getAbsolutePath());
                    String internedName = stringInterner.intern(parser.getName());
                    builder.enterDirectory(DIRECT, internedAbsolutePath, internedName, INCLUDE_EMPTY_DIRS);
                    break;
                case RegularFile:
                    RegularFileSnapshot fileSnapshot = createFileSnapshot(entry, file);
                    builder.visitLeafElement(fileSnapshot);
                    break;
                case Missing:
                    // No need to store a snapshot for a missing file
                    break;
            }
        }

        parser.exitToRoot(builder::leaveDirectory);
        builder.leaveDirectory();
        return checkNotNull(builder.getResult());
    }

    private RegularFileSnapshot createFileSnapshot(CacheManifest.ManifestEntry entry, File file) {
        return new RegularFileSnapshot(
            stringInterner.intern(file.getAbsolutePath()),
            stringInterner.intern(file.getName()),
            entry.getContentHash(),
            DefaultFileMetadata.file(file.lastModified(), entry.getLength(), DIRECT)
        );
    }

    @Override
    public void store(BuildCacheKey manifestCacheKey, CacheableEntity entity, Map<String, FileSystemSnapshot> snapshots, Duration executionTime) {
        ImmutableMap.Builder<String, List<ManifestEntry>> propertyManifests = ImmutableMap.builder();

        entity.visitOutputTrees((propertyName, type, root) -> {
            ImmutableList.Builder<ManifestEntry> manifestEntries = ImmutableList.builder();
            FileSystemSnapshot rootSnapshot = snapshots.get(propertyName);
            rootSnapshot.accept(new RelativePathTracker(), (snapshot, relativePath) -> {
                if (relativePath.isRoot()) {
                    assertCorrectType(type, snapshot);
                }
                manifestEntries.add(new ManifestEntry(
                    snapshot.getType(),
                    relativePath.toRelativePath(),
                    snapshot.getHash(),
                    SnapshotUtil.getLength(snapshot)));
                return SnapshotVisitResult.CONTINUE;
            });
            propertyManifests.put(propertyName, manifestEntries.build());
        });

        CacheManifest manifest = new CacheManifest(
            new OriginMetadata(buildInvocationId, executionTime),
            propertyManifests.build());

        storeInOperation(manifestCacheKey, entity, manifest);
    }

    private interface StoreResult {
        long getEntryCount();

        long getTotalSize();
    }

    private static abstract class CountingWriter implements NextGenBuildCacheService.NextGenWriter {
        private final AtomicLong entryCount;
        private final AtomicLong totalSize;

        public CountingWriter(AtomicLong entryCount, AtomicLong totalSize) {
            this.entryCount = entryCount;
            this.totalSize = totalSize;
        }

        @Override
        public final InputStream openStream() throws IOException {
            markStored();
            return doOpenStream();
        }

        @Override
        public final void writeTo(OutputStream output) throws IOException {
            markStored();
            doWriteTo(output);
        }

        protected abstract InputStream doOpenStream() throws IOException;

        protected abstract void doWriteTo(OutputStream output) throws IOException;

        private void markStored() {
            entryCount.incrementAndGet();
            totalSize.addAndGet(getSize());
        }
    }

    private void storeInOperation(BuildCacheKey manifestCacheKey, CacheableEntity entity, CacheManifest manifest) {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                StoreResult result = storeInner(manifestCacheKey, entity, manifest);
                context.setResult(new PackOperationResult(result.getEntryCount(), result.getTotalSize()));
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Pack build cache entry " + manifestCacheKey)
                    .details(new PackOperationDetails(manifestCacheKey))
                    .progressDisplayName("Packing build cache entry");
            }
        });
    }

    private StoreResult storeInner(BuildCacheKey manifestCacheKey, CacheableEntity entity, CacheManifest manifest) {
        AtomicLong entryCount = new AtomicLong(0L);
        AtomicLong totalSize = new AtomicLong(0L);
        entity.visitOutputTrees((propertyName, type, root) -> {
            Map<BuildCacheKey, ManifestEntry> manifestIndex = manifest.getPropertyManifests().get(propertyName).stream()
                .filter(entry -> entry.getType() == FileType.RegularFile)
                .collect(ImmutableMap.toImmutableMap(
                    manifestEntry -> new DefaultBuildCacheKey(manifestEntry.getContentHash()),
                    Function.identity(),
                    // When there are multiple identical files to store, it doesn't matter which one we read
                    (a, b) -> a)
                );

            cacheAccess.store(manifestIndex, manifestEntry -> new CountingWriter(entryCount, totalSize) {
                @Override
                protected InputStream doOpenStream() throws IOException {
                    // TODO Replace with "Files.newInputStream()" as it seems to be more efficient
                    //      Might be a good idea to pass `root` as `Path` instead of `File` then
                    //noinspection IOStreamConstructor
                    return new FileInputStream(new File(root, manifestEntry.getRelativePath()));
                }

                @Override
                protected void doWriteTo(OutputStream output) throws IOException {
                    try (InputStream input = openStream()) {
                        IOUtils.copyLarge(input, output, bufferProvider.getBuffer());
                    }
                }

                @Override
                public long getSize() {
                    return manifestEntry.getLength();
                }
            });
        });

        cacheAccess.store(Collections.singletonMap(manifestCacheKey, manifest), __ -> {
            String manifestJson = gson.toJson(manifest);
            byte[] bytes = manifestJson.getBytes(StandardCharsets.UTF_8);

            return new CountingWriter(entryCount, totalSize) {
                @Override
                protected InputStream doOpenStream() {
                    return new UnsynchronizedByteArrayInputStream(bytes);
                }

                @Override
                protected void doWriteTo(OutputStream output) throws IOException {
                    output.write(bytes);
                }

                @Override
                public long getSize() {
                    return bytes.length;
                }
            };
        });
        return new StoreResult() {
            @Override
            public long getEntryCount() {
                return entryCount.get();
            }

            @Override
            public long getTotalSize() {
                return totalSize.get();
            }
        };
    }

    private static void assertCorrectType(TreeType type, FileSystemLocationSnapshot snapshot) {
        if (snapshot.getType() == FileType.Missing) {
            return;
        }
        switch (type) {
            case DIRECTORY:
                if (snapshot.getType() != Directory) {
                    throw new IllegalArgumentException(String.format("Expected '%s' to be a directory", snapshot.getAbsolutePath()));
                }
                break;
            case FILE:
                if (snapshot.getType() != FileType.RegularFile) {
                    throw new IllegalArgumentException(String.format("Expected '%s' to be a file", snapshot.getAbsolutePath()));
                }
                break;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public void close() throws IOException {
        LOGGER.warn("Closing next-generation build cache controller");
        cacheAccess.close();
    }

    // FIXME code duplicate
    private void cleanOutputDirectory(TreeType type, File root) throws IOException {
        switch (type) {
            case DIRECTORY:
                deleter.ensureEmptyDirectory(root);
                break;
            case FILE:
                if (!makeDirectory(root.getParentFile())) {
                    if (root.exists()) {
                        deleter.deleteRecursively(root);
                    }
                }
                break;
            default:
                throw new AssertionError();
        }
    }

    // FIXME code duplicate
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean makeDirectory(File target) throws IOException {
        if (target.isDirectory()) {
            return false;
        } else if (target.isFile()) {
            deleter.delete(target);
        }
        FileUtils.forceMkdir(target);
        return true;
    }

    public static boolean isNextGenCachingEnabled() {
        return Boolean.getBoolean(NEXT_GEN_CACHE_SYSTEM_PROPERTY) == Boolean.TRUE;
    }
}
