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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.gson.Gson;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.controller.service.BuildCacheLoadResult;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class NextGenBuildCacheController implements BuildCacheController {
    private final NextGenBuildCacheAccess cacheAccess;

    public NextGenBuildCacheController(NextGenBuildCacheAccess cacheAccess) {
        this.cacheAccess = cacheAccess;
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

            // TODO Remove task outputs first

            // TODO Do all properties at once instead of doing separate bathches
            cacheableEntity.visitOutputTrees((propertyName, type, root) -> {
                List<CacheManifest.ManifestEntry> manifestEntries = manifest.getPropertyManifests().get(propertyName);
                Map<HashCode, String> contentHashes = manifestEntries.stream()
                        .collect(ImmutableMap.toImmutableMap(
                            CacheManifest.ManifestEntry::getContentHash,
                            CacheManifest.ManifestEntry::getRelativePath
                        ));

                // TODO Filter out entries that are already in the right place in the output directory
                // TODO Handle missing entries

                Iterable<BuildCacheKey> buildCacheKeys = contentHashes.keySet().stream()
                    .map(SimpleBuildCacheKey::new)
                    .collect(ImmutableSet.toImmutableSet());
                cacheAccess.load(buildCacheKeys, (contentHash, inputStream) -> {
                    String relativePath = contentHashes.get(contentHash);
                    try {
                        Files.copy(inputStream, new File(root, relativePath).toPath());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            });

            result.set(new BuildCacheLoadResult() {
                @Override
                public long getArtifactEntryCount() {
                    // TODO Set count
                    return 0;
                }

                @Override
                public OriginMetadata getOriginMetadata() {
                    return manifest.getOriginMetadata();
                }

                @Override
                public ImmutableSortedMap<String, FileSystemSnapshot> getResultingSnapshots() {
                    // TODO Collect snapshots
                    return null;
                }
            });
        });

        return Optional.ofNullable(result.get());
    }

    @Override
    public void store(BuildCacheKey manifestCacheKey, CacheableEntity entity, Map<String, FileSystemSnapshot> snapshots, Duration executionTime) {
        ImmutableMap.Builder<String, List<CacheManifest.ManifestEntry>> propertyManifests = ImmutableMap.builder();

        entity.visitOutputTrees((propertyName, type, root) -> {
            // TODO Do this
        });

        CacheManifest manifest = new CacheManifest(
            // TODO Set build invocation ID properly
            new OriginMetadata("", executionTime),
            propertyManifests.build()
        );

//        cacheAccess.store(propertyManifest.getAllHashes(), (hashCode, outputStream) -> {
//            // TODO Handle only files
//            // TODO Handle empty directories, too
//        });

        cacheAccess.store(Collections.singleton(manifestCacheKey), (__, outputStream) -> {
            String manifestJson = new Gson().toJson(manifest);
            try {
                outputStream.write(manifestJson.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public void close() throws IOException {
    }

    private static class SimpleBuildCacheKey implements BuildCacheKey {
        private final HashCode hashCode;

        public SimpleBuildCacheKey(HashCode hashCode) {
            this.hashCode = hashCode;
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
    }
}
