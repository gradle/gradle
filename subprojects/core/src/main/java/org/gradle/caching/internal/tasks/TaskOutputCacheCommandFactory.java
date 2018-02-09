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

import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileSystemMirror;
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata;
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.execution.TaskOutputChangesListener;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.OutputPropertySpec;
import org.gradle.caching.internal.controller.BuildCacheLoadCommand;
import org.gradle.caching.internal.controller.BuildCacheStoreCommand;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginFactory;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.SortedSet;

@NonNullApi
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

    public BuildCacheLoadCommand<OriginTaskExecutionMetadata> createLoad(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, FileCollection localStateFiles, TaskOutputChangesListener taskOutputChangesListener, TaskArtifactState taskArtifactState) {
        return new LoadCommand(cacheKey, outputProperties, task, localStateFiles, taskOutputChangesListener, taskArtifactState);
    }

    public BuildCacheStoreCommand createStore(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, Map<String, Map<String, FileContentSnapshot>> outputSnapshots, TaskInternal task, long taskExecutionTime) {
        return new StoreCommand(cacheKey, outputProperties, outputSnapshots, task, taskExecutionTime);
    }

    private class LoadCommand extends AbstractLoadCommand<InputStream, TaskOutputPacker.UnpackResult> implements BuildCacheLoadCommand<OriginTaskExecutionMetadata> {

        private LoadCommand(TaskOutputCachingBuildCacheKey key, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, FileCollection localStateFiles, TaskOutputChangesListener taskOutputChangesListener, TaskArtifactState taskArtifactState) {
            super(key, outputProperties, task, localStateFiles, taskOutputChangesListener, taskArtifactState, fileSystemMirror, stringInterner, taskOutputOriginFactory);
        }

        @Override
        public Result<OriginTaskExecutionMetadata> load(InputStream inputStream) {
            final TaskOutputPacker.UnpackResult unpackResult = performLoad(inputStream);
            assert unpackResult != null;
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

        @Override
        protected TaskOutputPacker.UnpackResult performLoad(@Nullable InputStream input, SortedSet<? extends OutputPropertySpec> outputProperties, TaskOutputOriginReader reader) throws IOException {
            LOGGER.info("Unpacked output for {} from cache.", task);
            TaskOutputPacker.UnpackResult unpackResult = packer.unpack(outputProperties, input, reader);
            updateSnapshots(unpackResult.getSnapshots(), unpackResult.getOriginMetadata());
            return unpackResult;
        }
    }

    private class StoreCommand implements BuildCacheStoreCommand {

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
        public BuildCacheKey getKey() {
            return cacheKey;
        }

        @Override
        public BuildCacheStoreCommand.Result store(OutputStream output) throws IOException {
            LOGGER.info("Packing {}", task);
            final TaskOutputPacker.PackResult packResult = packer.pack(outputProperties, outputSnapshots, output, taskOutputOriginFactory.createWriter(task, taskExecutionTime));
            return new BuildCacheStoreCommand.Result() {
                @Override
                public long getArtifactEntryCount() {
                    return packResult.getEntries();
                }
            };
        }
    }
}
