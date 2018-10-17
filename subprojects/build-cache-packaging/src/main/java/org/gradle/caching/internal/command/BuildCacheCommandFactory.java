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

package org.gradle.caching.internal.command;

import com.google.common.collect.ImmutableSortedMap;
import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.CacheableThing;
import org.gradle.caching.internal.controller.BuildCacheLoadCommand;
import org.gradle.caching.internal.controller.BuildCacheStoreCommand;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.origin.OriginMetadataFactory;
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.CacheableTree;
import org.gradle.caching.internal.packaging.UnrecoverableUnpackingException;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint;
import org.gradle.internal.nativeintegration.filesystem.DefaultFileMetadata;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemMirror;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

public class BuildCacheCommandFactory {

    private static final Logger LOGGER = Logging.getLogger(BuildCacheCommandFactory.class);

    private final BuildCacheEntryPacker packer;
    private final OriginMetadataFactory originMetadataFactory;
    private final FileSystemMirror fileSystemMirror;
    private final StringInterner stringInterner;

    public BuildCacheCommandFactory(BuildCacheEntryPacker packer, OriginMetadataFactory originMetadataFactory, FileSystemMirror fileSystemMirror, StringInterner stringInterner) {
        this.packer = packer;
        this.originMetadataFactory = originMetadataFactory;
        this.fileSystemMirror = fileSystemMirror;
        this.stringInterner = stringInterner;
    }

    public BuildCacheLoadCommand<OriginMetadata> createLoad(BuildCacheKey cacheKey, SortedSet<CacheableTree> outputProperties, CacheableThing entry, Iterable<File> localState, BuildCacheLoadListener loadListener) {
        return new LoadCommand(cacheKey, outputProperties, entry, localState, loadListener);
    }

    public BuildCacheStoreCommand createStore(BuildCacheKey cacheKey, SortedSet<CacheableTree> outputProperties, Map<String, CurrentFileCollectionFingerprint> outputFingerprints, CacheableThing entry, long executionTime) {
        return new StoreCommand(cacheKey, outputProperties, outputFingerprints, entry, executionTime);
    }

    private class LoadCommand implements BuildCacheLoadCommand<OriginMetadata> {

        private final BuildCacheKey cacheKey;
        private final SortedSet<CacheableTree> outputProperties;
        private final CacheableThing entry;
        private final Iterable<File> localState;
        private final BuildCacheLoadListener loadListener;

        private LoadCommand(BuildCacheKey cacheKey, SortedSet<CacheableTree> outputProperties, CacheableThing entry, Iterable<File> localState, BuildCacheLoadListener loadListener) {
            this.cacheKey = cacheKey;
            this.outputProperties = outputProperties;
            this.entry = entry;
            this.localState = localState;
            this.loadListener = loadListener;
        }

        @Override
        public BuildCacheKey getKey() {
            return cacheKey;
        }

        @Override
        public BuildCacheLoadCommand.Result<OriginMetadata> load(InputStream input) {
            loadListener.beforeLoad();
            final BuildCacheEntryPacker.UnpackResult unpackResult;
            try {
                unpackResult = packer.unpack(outputProperties, input, originMetadataFactory.createReader(entry));
                ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshots = snapshotUnpackedData(unpackResult.getSnapshots());
                loadListener.afterLoad(snapshots, unpackResult.getOriginMetadata());
            } catch (Exception e) {
                LOGGER.warn("Cleaning outputs for {} after failed load from cache.", entry);
                try {
                    cleanupOutputsAfterUnpackFailure();
                    loadListener.afterLoad(e);
                } catch (Exception eCleanup) {
                    LOGGER.warn("Unrecoverable error during cleaning up after unpack failure", eCleanup);
                    throw new UnrecoverableUnpackingException(String.format("Failed to unpack outputs for %s, and then failed to clean up; see log above for details", entry), e);
                }
                throw new GradleException(String.format("Failed to unpack outputs for %s", entry), e);
            } finally {
                cleanLocalState();
            }
            LOGGER.info("Unpacked output for {} from cache.", entry);

            return new BuildCacheLoadCommand.Result<OriginMetadata>() {
                @Override
                public long getArtifactEntryCount() {
                    return unpackResult.getEntries();
                }

                @Override
                public OriginMetadata getMetadata() {
                    return unpackResult.getOriginMetadata();
                }
            };
        }

        private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotUnpackedData(Map<String, ? extends FileSystemLocationSnapshot> propertySnapshots) {
            ImmutableSortedMap.Builder<String, CurrentFileCollectionFingerprint> propertyFingerprintsBuilder = ImmutableSortedMap.naturalOrder();
            FingerprintingStrategy fingerprintingStrategy = AbsolutePathFingerprintingStrategy.IGNORE_MISSING;
            for (CacheableTree property : outputProperties) {
                String propertyName = property.getName();
                File outputFile = property.getRoot();
                if (outputFile == null) {
                    propertyFingerprintsBuilder.put(propertyName, fingerprintingStrategy.getEmptyFingerprint());
                    continue;
                }
                FileSystemLocationSnapshot snapshot = propertySnapshots.get(propertyName);
                String absolutePath = internedAbsolutePath(outputFile);
                List<FileSystemSnapshot> roots = new ArrayList<FileSystemSnapshot>();

                if (snapshot == null) {
                    fileSystemMirror.putMetadata(absolutePath, DefaultFileMetadata.missing());
                    fileSystemMirror.putSnapshot(new MissingFileSnapshot(absolutePath, property.getRoot().getName()));
                    propertyFingerprintsBuilder.put(propertyName, fingerprintingStrategy.getEmptyFingerprint());
                    continue;
                }

                switch (property.getType()) {
                    case FILE:
                        if (snapshot.getType() != FileType.RegularFile) {
                            throw new IllegalStateException(String.format("Only a regular file should be produced by unpacking tree '%s', but saw a %s", propertyName, snapshot.getType()));
                        }
                        roots.add(snapshot);
                        fileSystemMirror.putSnapshot(snapshot);
                        break;
                    case DIRECTORY:
                        roots.add(snapshot);
                        fileSystemMirror.putMetadata(absolutePath, DefaultFileMetadata.directory());
                        fileSystemMirror.putSnapshot(snapshot);
                        break;
                    default:
                        throw new AssertionError();
                }
                propertyFingerprintsBuilder.put(propertyName, DefaultCurrentFileCollectionFingerprint.from(roots, fingerprintingStrategy));
            }
            return propertyFingerprintsBuilder.build();
        }

        private void cleanLocalState() {
            for (File localStateFile : localState) {
                try {
                    remove(localStateFile);
                } catch (IOException ex) {
                    throw new UncheckedIOException(String.format("Failed to clean up local state files for %s: %s", entry, localStateFile), ex);
                }
            }
        }

        private void cleanupOutputsAfterUnpackFailure() {
            for (CacheableTree outputProperty : outputProperties) {
                File root = outputProperty.getRoot();
                try {
                    remove(root);
                } catch (IOException ex) {
                    throw new UncheckedIOException(String.format("Failed to clean up files for tree '%s' of %s: %s", outputProperty.getName(), entry, root), ex);
                }
            }
        }

        private void remove(File file) throws IOException {
            if (file != null && file.exists()) {
                if (file.isDirectory()) {
                    FileUtils.cleanDirectory(file);
                } else {
                    FileUtils.forceDelete(file);
                }
            }
        }
    }

    private String internedAbsolutePath(File outputFile) {
        return stringInterner.intern(outputFile.getAbsolutePath());
    }

    private class StoreCommand implements BuildCacheStoreCommand {

        private final BuildCacheKey cacheKey;
        private final SortedSet<CacheableTree> outputProperties;
        private final Map<String, CurrentFileCollectionFingerprint> outputFingerprints;
        private final CacheableThing entry;
        private final long executionTime;

        private StoreCommand(BuildCacheKey cacheKey, SortedSet<CacheableTree> outputProperties, Map<String, CurrentFileCollectionFingerprint> outputFingerprints, CacheableThing entry, long executionTime) {
            this.cacheKey = cacheKey;
            this.outputProperties = outputProperties;
            this.outputFingerprints = outputFingerprints;
            this.entry = entry;
            this.executionTime = executionTime;
        }

        @Override
        public BuildCacheKey getKey() {
            return cacheKey;
        }

        @Override
        public BuildCacheStoreCommand.Result store(OutputStream output) throws IOException {
            LOGGER.info("Packing {}", entry);
            final BuildCacheEntryPacker.PackResult packResult = packer.pack(outputProperties, outputFingerprints, output, originMetadataFactory.createWriter(entry, executionTime));
            return new BuildCacheStoreCommand.Result() {
                @Override
                public long getArtifactEntryCount() {
                    return packResult.getEntries();
                }
            };
        }
    }
}
