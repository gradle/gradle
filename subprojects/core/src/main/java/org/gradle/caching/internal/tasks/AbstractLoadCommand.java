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

package org.gradle.caching.internal.tasks;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.state.EmptyFileCollectionSnapshot;
import org.gradle.api.internal.tasks.execution.TaskOutputChangesListener;
import org.gradle.api.internal.changedetection.state.CollectingFileCollectionSnapshotBuilder;
import org.gradle.api.internal.changedetection.state.DirectoryTreeDetails;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileSnapshot;
import org.gradle.api.internal.changedetection.state.FileSystemMirror;
import org.gradle.api.internal.changedetection.state.MissingFileSnapshot;
import org.gradle.api.internal.changedetection.state.OutputPathNormalizationStrategy;
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.OutputPropertySpec;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginFactory;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader;
import org.gradle.internal.file.FileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.SortedSet;

import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.UNORDERED;

@NonNullApi
public abstract class AbstractLoadCommand<I, O> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLoadCommand.class);
    private static final Predicate<? super FileSnapshot> EXCLUDE_ROOT_SNAPSHOTS = new Predicate<FileSnapshot>() {
        @Override
        public boolean apply(FileSnapshot snapshot) {
            return !snapshot.isRoot();
        }
    };

    protected final TaskInternal task;
    private final BuildCacheKey key;
    private final SortedSet<? extends OutputPropertySpec> outputProperties;
    private final FileCollection localStateFiles;
    private final TaskOutputChangesListener taskOutputChangesListener;
    private final TaskArtifactState taskArtifactState;
    private final FileSystemMirror fileSystemMirror;
    private final StringInterner stringInterner;
    private final TaskOutputOriginFactory taskOutputOriginFactory;

    public AbstractLoadCommand(BuildCacheKey key, SortedSet<? extends OutputPropertySpec> outputProperties, TaskInternal task, FileCollection localStateFiles, TaskOutputChangesListener taskOutputChangesListener, TaskArtifactState taskArtifactState, FileSystemMirror fileSystemMirror, StringInterner stringInterner, TaskOutputOriginFactory taskOutputOriginFactory) {
        this.key = key;
        this.outputProperties = outputProperties;
        this.task = task;
        this.localStateFiles = localStateFiles;
        this.taskOutputChangesListener = taskOutputChangesListener;
        this.taskArtifactState = taskArtifactState;
        this.fileSystemMirror = fileSystemMirror;
        this.stringInterner = stringInterner;
        this.taskOutputOriginFactory = taskOutputOriginFactory;
    }

    public BuildCacheKey getKey() {
        return key;
    }

    @Nullable
    public O performLoad(@Nullable I input) {
        taskOutputChangesListener.beforeTaskOutputChanged();
        try {
            TaskOutputOriginReader reader = taskOutputOriginFactory.createReader(task);
            return performLoad(input, outputProperties, reader);
        } catch (Exception e) {
            LOGGER.warn("Cleaning outputs for {} after failed load from cache.", task);
            try {
                cleanupOutputsAfterUnpackFailure();
                taskArtifactState.afterOutputsRemovedBeforeTask();
            } catch (Exception eCleanup) {
                LOGGER.warn("Unrecoverable error during cleaning up after task output unpack failure", eCleanup);
                throw new UnrecoverableTaskOutputUnpackingException(String.format("Failed to unpack outputs for %s, and then failed to clean up; see log above for details", task), e);
            }
            throw new GradleException(String.format("Failed to load outputs for %s", task), e);
        } finally {
            cleanLocalState();
        }
    }

    @Nullable
    protected abstract O performLoad(@Nullable I input, SortedSet<? extends OutputPropertySpec> outputProperties, TaskOutputOriginReader reader) throws IOException;

    protected void updateSnapshots(Multimap<String, FileSnapshot> propertiesFileSnapshots, OriginTaskExecutionMetadata originMetadata) {
        ImmutableSortedMap.Builder<String, FileCollectionSnapshot> propertySnapshotsBuilder = ImmutableSortedMap.naturalOrder();
        for (OutputPropertySpec property : outputProperties) {
            String propertyName = property.getPropertyName();
            File outputFile = property.getOutputRoot();
            if (outputFile == null) {
                propertySnapshotsBuilder.put(propertyName, EmptyFileCollectionSnapshot.INSTANCE);
                continue;
            }
            Collection<FileSnapshot> fileSnapshots = propertiesFileSnapshots.get(propertyName);

            CollectingFileCollectionSnapshotBuilder builder = new CollectingFileCollectionSnapshotBuilder(UNORDERED, OutputPathNormalizationStrategy.getInstance(), stringInterner);
            for (FileSnapshot fileSnapshot : fileSnapshots) {
                builder.collectFileSnapshot(fileSnapshot);
            }
            propertySnapshotsBuilder.put(propertyName, builder.build());

            switch (property.getOutputType()) {
                case FILE:
                    FileSnapshot singleSnapshot = Iterables.getOnlyElement(fileSnapshots, null);
                    if (singleSnapshot != null) {
                        if (singleSnapshot.getType() != FileType.RegularFile) {
                            throw new IllegalStateException(String.format("Only a regular file should be produced by unpacking property '%s', but saw a %s", propertyName, singleSnapshot.getType()));
                        }
                        fileSystemMirror.putFile(singleSnapshot);
                    } else {
                        fileSystemMirror.putFile(new MissingFileSnapshot(internedAbsolutePath(outputFile), RelativePath.EMPTY_ROOT));
                    }
                    break;
                case DIRECTORY:
                    Collection<FileSnapshot> descendants = Collections2.filter(fileSnapshots, EXCLUDE_ROOT_SNAPSHOTS);
                    fileSystemMirror.putDirectory(new DirectoryTreeDetails(internedAbsolutePath(outputFile), descendants));
                    break;
                default:
                    throw new AssertionError();
            }
        }
        taskArtifactState.snapshotAfterLoadedFromCache(propertySnapshotsBuilder.build(), originMetadata);
    }

    private String internedAbsolutePath(File outputFile) {
        return stringInterner.intern(outputFile.getAbsolutePath());
    }

    private void cleanLocalState() {
        for (File localStateFile : localStateFiles) {
            try {
                remove(localStateFile);
            } catch (IOException ex) {
                throw new UncheckedIOException(String.format("Failed to clean up local state files for %s: %s", task, localStateFile), ex);
            }
        }
    }

    private void cleanupOutputsAfterUnpackFailure() {
        for (OutputPropertySpec outputProperty : outputProperties) {
            File outputRoot = outputProperty.getOutputRoot();
            try {
                remove(outputRoot);
            } catch (IOException ex) {
                throw new UncheckedIOException(String.format("Failed to clean up files for output property '%s' of %s: %s", outputProperty.getPropertyName(), task, outputRoot), ex);
            }
        }
    }

    private void remove(@Nullable File file) throws IOException {
        if (file != null && file.exists()) {
            if (file.isDirectory()) {
                FileUtils.cleanDirectory(file);
            } else {
                FileUtils.forceDelete(file);
            }
        }
    }
}
