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

package org.gradle.caching.internal.tasks;

import com.google.common.collect.ImmutableSortedMap;
import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata;
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.execution.TaskOutputChangesListener;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.controller.BuildCacheLoadCommand;
import org.gradle.caching.internal.controller.BuildCacheStoreCommand;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginFactory;
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

public class TaskOutputCacheCommandFactory {

    private static final Logger LOGGER = Logging.getLogger(TaskOutputCacheCommandFactory.class);

    private final TaskOutputPacker packer;
    private final TaskOutputOriginFactory taskOutputOriginFactory;
    private final FileSystemMirror fileSystemMirror;
    private final StringInterner stringInterner;

    public TaskOutputCacheCommandFactory(TaskOutputPacker packer, TaskOutputOriginFactory taskOutputOriginFactory, FileSystemMirror fileSystemMirror, StringInterner stringInterner) {
        this.packer = packer;
        this.taskOutputOriginFactory = taskOutputOriginFactory;
        this.fileSystemMirror = fileSystemMirror;
        this.stringInterner = stringInterner;
    }

    public BuildCacheLoadCommand<OriginTaskExecutionMetadata> createLoad(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, TaskProperties taskProperties, TaskOutputChangesListener taskOutputChangesListener, TaskArtifactState taskArtifactState) {
        return new LoadCommand(cacheKey, outputProperties, task, taskProperties, taskOutputChangesListener, taskArtifactState);
    }

    public BuildCacheStoreCommand createStore(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, Map<String, CurrentFileCollectionFingerprint> outputFingerprints, TaskInternal task, long taskExecutionTime) {
        return new StoreCommand(cacheKey, outputProperties, outputFingerprints, task, taskExecutionTime);
    }

    private class LoadCommand implements BuildCacheLoadCommand<OriginTaskExecutionMetadata> {

        private final TaskOutputCachingBuildCacheKey cacheKey;
        private final SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties;
        private final TaskInternal task;
        private final TaskProperties taskProperties;
        private final TaskOutputChangesListener taskOutputChangesListener;
        private final TaskArtifactState taskArtifactState;

        private LoadCommand(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, TaskProperties taskProperties, TaskOutputChangesListener taskOutputChangesListener, TaskArtifactState taskArtifactState) {
            this.cacheKey = cacheKey;
            this.outputProperties = outputProperties;
            this.task = task;
            this.taskProperties = taskProperties;
            this.taskOutputChangesListener = taskOutputChangesListener;
            this.taskArtifactState = taskArtifactState;
        }

        @Override
        public BuildCacheKey getKey() {
            return cacheKey;
        }

        @Override
        public BuildCacheLoadCommand.Result<OriginTaskExecutionMetadata> load(InputStream input) {
            taskOutputChangesListener.beforeTaskOutputChanged();
            final TaskOutputPacker.UnpackResult unpackResult;
            try {
                unpackResult = packer.unpack(outputProperties, input, taskOutputOriginFactory.createReader(task));
                updateSnapshots(unpackResult.getSnapshots(), unpackResult.getOriginMetadata());
            } catch (Exception e) {
                LOGGER.warn("Cleaning outputs for {} after failed load from cache.", task);
                try {
                    cleanupOutputsAfterUnpackFailure();
                    taskArtifactState.afterOutputsRemovedBeforeTask();
                } catch (Exception eCleanup) {
                    LOGGER.warn("Unrecoverable error during cleaning up after task output unpack failure", eCleanup);
                    throw new UnrecoverableTaskOutputUnpackingException(String.format("Failed to unpack outputs for %s, and then failed to clean up; see log above for details", task), e);
                }
                throw new GradleException(String.format("Failed to unpack outputs for %s", task), e);
            } finally {
                cleanLocalState();
            }
            LOGGER.info("Unpacked output for {} from cache.", task);

            return new BuildCacheLoadCommand.Result<OriginTaskExecutionMetadata>() {
                @Override
                public long getArtifactEntryCount() {
                    return unpackResult.getEntries();
                }

                @Override
                public OriginTaskExecutionMetadata getMetadata() {
                    return unpackResult.getOriginMetadata();
                }
            };
        }

        private void updateSnapshots(Map<String, ? extends FileSystemLocationSnapshot> propertySnapshots, OriginTaskExecutionMetadata originMetadata) {
            ImmutableSortedMap.Builder<String, CurrentFileCollectionFingerprint> propertyFingerprintsBuilder = ImmutableSortedMap.naturalOrder();
            FingerprintingStrategy fingerprintingStrategy = AbsolutePathFingerprintingStrategy.IGNORE_MISSING;
            for (ResolvedTaskOutputFilePropertySpec property : outputProperties) {
                String propertyName = property.getPropertyName();
                File outputFile = property.getOutputFile();
                if (outputFile == null) {
                    propertyFingerprintsBuilder.put(propertyName, fingerprintingStrategy.getEmptyFingerprint());
                    continue;
                }
                FileSystemLocationSnapshot snapshot = propertySnapshots.get(propertyName);
                String absolutePath = internedAbsolutePath(outputFile);
                List<FileSystemSnapshot> roots = new ArrayList<FileSystemSnapshot>();

                if (snapshot == null) {
                    fileSystemMirror.putMetadata(absolutePath, DefaultFileMetadata.missing());
                    fileSystemMirror.putSnapshot(new MissingFileSnapshot(absolutePath, property.getOutputFile().getName()));
                    propertyFingerprintsBuilder.put(propertyName, fingerprintingStrategy.getEmptyFingerprint());
                    continue;
                }

                switch (property.getOutputType()) {
                    case FILE:
                        if (snapshot.getType() != FileType.RegularFile) {
                            throw new IllegalStateException(String.format("Only a regular file should be produced by unpacking property '%s', but saw a %s", propertyName, snapshot.getType()));
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
            taskArtifactState.snapshotAfterLoadedFromCache(propertyFingerprintsBuilder.build(), originMetadata);
        }

        private void cleanLocalState() {
            for (File localStateFile : taskProperties.getLocalStateFiles()) {
                try {
                    remove(localStateFile);
                } catch (IOException ex) {
                    throw new UncheckedIOException(String.format("Failed to clean up local state files for %s: %s", task, localStateFile), ex);
                }
            }
        }

        private void cleanupOutputsAfterUnpackFailure() {
            for (ResolvedTaskOutputFilePropertySpec outputProperty : outputProperties) {
                File outputFile = outputProperty.getOutputFile();
                try {
                    remove(outputFile);
                } catch (IOException ex) {
                    throw new UncheckedIOException(String.format("Failed to clean up files for output property '%s' of %s: %s", outputProperty.getPropertyName(), task, outputFile), ex);
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

        private final TaskOutputCachingBuildCacheKey cacheKey;
        private final SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties;
        private final Map<String, CurrentFileCollectionFingerprint> outputFingerprints;
        private final TaskInternal task;
        private final long taskExecutionTime;

        private StoreCommand(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, Map<String, CurrentFileCollectionFingerprint> outputFingerprints, TaskInternal task, long taskExecutionTime) {
            this.cacheKey = cacheKey;
            this.outputProperties = outputProperties;
            this.outputFingerprints = outputFingerprints;
            this.task = task;
            this.taskExecutionTime = taskExecutionTime;
        }

        @Override
        public BuildCacheKey getKey() {
            return cacheKey;
        }

        @Override
        public BuildCacheStoreCommand.Result store(OutputStream output) throws IOException {
            LOGGER.info("Packing {}", task);
            final TaskOutputPacker.PackResult packResult = packer.pack(outputProperties, outputFingerprints, output, taskOutputOriginFactory.createWriter(task, taskExecutionTime));
            return new BuildCacheStoreCommand.Result() {
                @Override
                public long getArtifactEntryCount() {
                    return packResult.getEntries();
                }
            };
        }
    }
}
