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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.state.DirectoryTreeDetails;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotBuilder;
import org.gradle.api.internal.changedetection.state.FileSnapshot;
import org.gradle.api.internal.changedetection.state.FileSystemMirror;
import org.gradle.api.internal.changedetection.state.OutputPathNormalizationStrategy;
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.execution.TaskOutputsGenerationListener;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.controller.BuildCacheLoadCommand;
import org.gradle.caching.internal.controller.BuildCacheStoreCommand;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginFactory;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginMetadata;
import org.gradle.internal.time.Timer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.SortedSet;

import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.UNORDERED;

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

    public BuildCacheLoadCommand<TaskOutputOriginMetadata> createLoad(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, TaskOutputsGenerationListener taskOutputsGenerationListener, TaskArtifactState taskArtifactState, Timer clock) {
        return new LoadCommand(cacheKey, outputProperties, task, taskOutputsGenerationListener, taskArtifactState, clock);
    }

    public BuildCacheStoreCommand createStore(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, Timer clock) {
        return new StoreCommand(cacheKey, outputProperties, task, clock);
    }

    private class LoadCommand implements BuildCacheLoadCommand<TaskOutputOriginMetadata> {

        private final TaskOutputCachingBuildCacheKey cacheKey;
        private final SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties;
        private final TaskInternal task;
        private final TaskOutputsGenerationListener taskOutputsGenerationListener;
        private final TaskArtifactState taskArtifactState;
        private final Timer clock;

        private LoadCommand(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, TaskOutputsGenerationListener taskOutputsGenerationListener, TaskArtifactState taskArtifactState, Timer clock) {
            this.cacheKey = cacheKey;
            this.outputProperties = outputProperties;
            this.task = task;
            this.taskOutputsGenerationListener = taskOutputsGenerationListener;
            this.taskArtifactState = taskArtifactState;
            this.clock = clock;
        }

        @Override
        public BuildCacheKey getKey() {
            return cacheKey;
        }

        @Override
        public BuildCacheLoadCommand.Result<TaskOutputOriginMetadata> load(InputStream input) throws IOException {
            taskOutputsGenerationListener.beforeTaskOutputsGenerated();
            final TaskOutputPacker.UnpackResult unpackResult;
            try {
                unpackResult = packer.unpack(outputProperties, input, taskOutputOriginFactory.createReader(task));
                updateSnapshots(unpackResult.getSnapshots());
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
            }
            LOGGER.info("Unpacked output for {} from cache (took {}).", task, clock.getElapsed());

            return new BuildCacheLoadCommand.Result<TaskOutputOriginMetadata>() {
                @Override
                public long getArtifactEntryCount() {
                    return unpackResult.getEntries();
                }

                @Override
                public TaskOutputOriginMetadata getMetadata() {
                    return unpackResult.getOriginMetadata();
                }
            };
        }

        private void updateSnapshots(ImmutableListMultimap<String, FileSnapshot> propertiesFileSnapshots) {
            ImmutableMap<String, ResolvedTaskOutputFilePropertySpec> propertySpecsMap = Maps.uniqueIndex(outputProperties, new Function<TaskOutputFilePropertySpec, String>() {
                @Override
                public String apply(TaskOutputFilePropertySpec propertySpec) {
                    return propertySpec.getPropertyName();
                }
            });
            ImmutableSortedMap.Builder<String, FileCollectionSnapshot> propertySnapshotsBuilder = ImmutableSortedMap.naturalOrder();
            for (String propertyName : propertiesFileSnapshots.keySet()) {
                ResolvedTaskOutputFilePropertySpec property = propertySpecsMap.get(propertyName);
                List<FileSnapshot> fileSnapshots = propertiesFileSnapshots.get(propertyName);
                FileCollectionSnapshotBuilder builder = new FileCollectionSnapshotBuilder(UNORDERED, OutputPathNormalizationStrategy.getInstance(), stringInterner);
                builder.visitFileTreeSnapshot(fileSnapshots);

                propertySnapshotsBuilder.put(propertyName, builder.build());

                if (fileSnapshots.size() == 1) {
                    FileSnapshot singleSnapshot = fileSnapshots.get(0);
                    switch (singleSnapshot.getType()) {
                        case Missing:
                            continue;
                        case RegularFile:
                            fileSystemMirror.putFile(singleSnapshot);
                            continue;
                    }
                }
                fileSystemMirror.putDirectory(new DirectoryTreeDetails(property.getOutputFile().getAbsolutePath(), fileSnapshots));
            }
            taskArtifactState.snapshotAfterLoadedFromCache(propertySnapshotsBuilder.build());
        }

        private void cleanupOutputsAfterUnpackFailure() {
            for (ResolvedTaskOutputFilePropertySpec outputProperty : outputProperties) {
                File outputFile = outputProperty.getOutputFile();
                if (outputFile != null && outputFile.exists()) {
                    try {
                        if (outputFile.isDirectory()) {
                            FileUtils.cleanDirectory(outputFile);
                        } else {
                            FileUtils.forceDelete(outputFile);
                        }
                    } catch (IOException ex) {
                        throw new UncheckedIOException(String.format("Failed to clean up files for output property '%s' of %s: %s", outputProperty.getPropertyName(), task, outputFile), ex);
                    }
                }
            }
        }
    }

    private class StoreCommand implements BuildCacheStoreCommand {

        private final TaskOutputCachingBuildCacheKey cacheKey;
        private final TaskInternal task;
        private final SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties;
        private final Timer clock;

        private StoreCommand(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, Timer clock) {
            this.cacheKey = cacheKey;
            this.task = task;
            this.outputProperties = outputProperties;
            this.clock = clock;
        }

        @Override
        public BuildCacheKey getKey() {
            return cacheKey;
        }

        @Override
        public BuildCacheStoreCommand.Result store(OutputStream output) throws IOException {
            LOGGER.info("Packing {}", task);
            final TaskOutputPacker.PackResult packResult = packer.pack(outputProperties, output, taskOutputOriginFactory.createWriter(task, clock.getElapsedMillis()));
            return new BuildCacheStoreCommand.Result() {
                @Override
                public long getArtifactEntryCount() {
                    return packResult.getEntries();
                }
            };
        }
    }
}
