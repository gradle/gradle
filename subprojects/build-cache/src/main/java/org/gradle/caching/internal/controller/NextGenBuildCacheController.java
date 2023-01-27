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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.controller.CacheManifest.ManifestEntry;
import org.gradle.caching.internal.controller.service.BuildCacheLoadResult;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.RelativePathSupplier;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.RelativePathTracker;
import org.gradle.internal.snapshot.RelativePathTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class NextGenBuildCacheController implements BuildCacheController {
    private final NextGenBuildCacheAccess cacheAccess;
    private final FileSystemAccess fileSystemAccess;
    private final Deleter deleter;


    public NextGenBuildCacheController(NextGenBuildCacheAccess cacheAccess, FileSystemAccess fileSystemAccess, Deleter deleter) {
        this.cacheAccess = cacheAccess;
        this.fileSystemAccess = fileSystemAccess;
        this.deleter = deleter;
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
        AtomicReference<BuildCacheLoadResult> result = new AtomicReference<>();
        cacheAccess.load(Collections.singleton(manifestCacheKey), (__, manifestStream) -> {
            CacheManifest manifest = new Gson().fromJson(new InputStreamReader(manifestStream), CacheManifest.class);

            // TODO Do all properties at once instead of doing separate bathches
            AtomicLong entryCount = new AtomicLong(0);
            ImmutableSortedMap.Builder<String, FileSystemSnapshot> snaphsots = ImmutableSortedMap.naturalOrder();
            cacheableEntity.visitOutputTrees((propertyName, type, root) -> {
                try {
                    cleanOutputDirectory(type, root);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                List<ManifestEntry> manifestEntries = manifest.getPropertyManifests().get(propertyName);
                if (type == TreeType.FILE) {
                    root.getParentFile().mkdirs();
                } else {
                    root.mkdirs();
                }

                // Ugly hack
                manifestEntries.forEach(entry -> {
                    if (entry.getType() == CacheManifest.EntryType.DIRECTORY) {
                        new File(root, entry.getRelativePath()).mkdirs();
                    }
                });

                Multimap<BuildCacheKey, String> fileContentHashes = indexManifestFileEntries(manifestEntries);

                // TODO Filter out entries that are already in the right place in the output directory
                // TODO Handle missing entries

                cacheAccess.load(fileContentHashes.keySet(), (contentHash, input) -> {
                    byte[] data;
                    try {
                        data = ByteStreams.toByteArray(input);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could not read cache entry " + contentHash, e);
                    }
                    fileContentHashes.get(contentHash).forEach(relativePath -> {
                        File file = new File(root, relativePath);
                        try {
                            file.createNewFile();
                            try (FileOutputStream output = new FileOutputStream(file)) {
                                output.write(data);
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException("Couldn't create " + file.getAbsolutePath(), e);
                        }
                        entryCount.incrementAndGet();
                    });
                });

                // TODO Reuse the data in the manifest instead of re-reading just written files
                FileSystemLocationSnapshot snapshot = fileSystemAccess.read(root.getAbsolutePath());

                snaphsots.put(propertyName, snapshot);
            });

            result.set(new BuildCacheLoadResult() {
                @Override
                public long getArtifactEntryCount() {
                    return entryCount.get();
                }

                @Override
                public OriginMetadata getOriginMetadata() {
                    return manifest.getOriginMetadata();
                }

                @Override
                public ImmutableSortedMap<String, FileSystemSnapshot> getResultingSnapshots() {
                    // TODO Collect snapshots
                    return snaphsots.build();
                }
            });
        });

        return Optional.ofNullable(result.get());
    }

    @Override
    public void store(BuildCacheKey manifestCacheKey, CacheableEntity entity, Map<String, FileSystemSnapshot> snapshots, Duration executionTime) {
        ImmutableMap.Builder<String, List<ManifestEntry>> propertyManifests = ImmutableMap.builder();

        entity.visitOutputTrees((propertyName, type, root) -> {
            ImmutableList.Builder<ManifestEntry> manifestEntries = ImmutableList.builder();
            FileSystemSnapshot rootSnapshot = snapshots.get(propertyName);
            rootSnapshot.accept(new RelativePathTracker(), new RelativePathTrackingFileSystemSnapshotHierarchyVisitor() {
                @Override
                public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot, RelativePathSupplier relativePath) {
                    CacheManifest.EntryType type = (snapshot instanceof RegularFileSnapshot) ? CacheManifest.EntryType.FILE : CacheManifest.EntryType.DIRECTORY;
                    manifestEntries.add(new ManifestEntry(type, relativePath.toRelativePath(), snapshot.getHash().toString()));
                    return SnapshotVisitResult.CONTINUE;
                }
            });
            propertyManifests.put(propertyName, manifestEntries.build());
        });

        CacheManifest manifest = new CacheManifest(
            // TODO Set build invocation ID properly
            new OriginMetadata("", executionTime), propertyManifests.build());

        entity.visitOutputTrees((propertyName, type, root) -> {
            List<ManifestEntry> manifestEntries = manifest.getPropertyManifests().get(propertyName);
            ListMultimap<BuildCacheKey, String> manifestIndex = indexManifestFileEntries(manifestEntries);

            cacheAccess.store(manifestIndex.keySet(), buildCacheKey -> {
                // It doesn't matter which identical file we read
                // TODO We can do this without a multimap actually
                String relativePath = manifestIndex.get(buildCacheKey).get(0);
                File file = new File(root, relativePath);
                return new BuildCacheEntryWriter() {
                    @Override
                    public void writeTo(OutputStream output) throws IOException {
                        Files.copy(file.toPath(), output);
                    }

                    @Override
                    public long getSize() {
                        return file.length();
                    }
                };
            });
        });

        cacheAccess.store(Collections.singleton(manifestCacheKey), __ -> {
            String manifestJson = new Gson().toJson(manifest);
            byte[] bytes = manifestJson.getBytes(StandardCharsets.UTF_8);

            return new BuildCacheEntryWriter() {
                @Override
                public void writeTo(OutputStream output) throws IOException {
                    output.write(bytes);
                }

                @Override
                public long getSize() {
                    return bytes.length;
                }
            };
        });
    }

    @Override
    public void close() throws IOException {
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

    private static ListMultimap<BuildCacheKey, String> indexManifestFileEntries(List<ManifestEntry> manifestEntries) {
        return manifestEntries.stream()
            .filter(entry -> entry.getType() == CacheManifest.EntryType.FILE)
            .collect(ImmutableListMultimap.toImmutableListMultimap(
                manifestEntry -> new SimpleBuildCacheKey(manifestEntry.getContentHash()),
                ManifestEntry::getRelativePath)
            );
    }

    private static class SimpleBuildCacheKey implements BuildCacheKey {
        private final HashCode hashCode;

        public SimpleBuildCacheKey(String hashCode) {
            this.hashCode = HashCode.fromString(hashCode);
        }

        @Override
        public String getDisplayName() {
            return getHashCode();
        }

        @Override
        public String getHashCode() {
            return hashCode.toString();
        }

        @Override
        public byte[] toByteArray() {
            return hashCode.toByteArray();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SimpleBuildCacheKey that = (SimpleBuildCacheKey) o;

            return hashCode.equals(that.hashCode);
        }

        @Override
        public int hashCode() {
            return hashCode.hashCode();
        }

        @Override
        public String toString() {
            return hashCode.toString();
        }
    }
}
