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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.execution.TaskOutputsGenerationListener;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.controller.BuildCacheLoadCommand;
import org.gradle.caching.internal.controller.BuildCacheStoreCommand;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginFactory;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginMetadata;
import org.gradle.internal.time.Timer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.SortedSet;

public class TaskBuildCacheCommandFactory {

    private static final Logger LOGGER = Logging.getLogger(TaskBuildCacheCommandFactory.class);

    private final TaskOutputPacker packer;
    private final TaskOutputOriginFactory taskOutputOriginFactory;

    public TaskBuildCacheCommandFactory(TaskOutputPacker packer, TaskOutputOriginFactory taskOutputOriginFactory) {
        this.packer = packer;
        this.taskOutputOriginFactory = taskOutputOriginFactory;
    }

    public BuildCacheLoadCommand<TaskOutputOriginMetadata> load(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, TaskOutputsGenerationListener taskOutputsGenerationListener, Timer clock) {
        return new LoadCommand(cacheKey, outputProperties, task, taskOutputsGenerationListener, clock);
    }

    public BuildCacheStoreCommand store(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, Timer clock) {
        return new StoreCommand(cacheKey, outputProperties, task, clock);
    }

    private class LoadCommand implements BuildCacheLoadCommand<TaskOutputOriginMetadata> {

        private final TaskOutputCachingBuildCacheKey cacheKey;
        private final SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties;
        private final TaskInternal task;
        private final TaskOutputsGenerationListener taskOutputsGenerationListener;
        private final Timer clock;

        private LoadCommand(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, TaskOutputsGenerationListener taskOutputsGenerationListener, Timer clock) {
            this.cacheKey = cacheKey;
            this.outputProperties = outputProperties;
            this.task = task;
            this.taskOutputsGenerationListener = taskOutputsGenerationListener;
            this.clock = clock;
        }

        @Override
        public BuildCacheKey getKey() {
            return cacheKey;
        }

        @Override
        public BuildCacheLoadCommand.Result<TaskOutputOriginMetadata> load(InputStream input) {
            taskOutputsGenerationListener.beforeTaskOutputsGenerated();
            final TaskOutputPacker.UnpackResult unpackResult = packer.unpack(outputProperties, input, taskOutputOriginFactory.createReader(task));
            LOGGER.info("Unpacked output for {} from cache (took {}).", task, clock.getElapsed());

            return new BuildCacheLoadCommand.Result<TaskOutputOriginMetadata>() {
                @Override
                public long getArtifactEntryCount() {
                    return unpackResult.entries;
                }

                @Override
                public TaskOutputOriginMetadata getMetadata() {
                    return unpackResult.originMetadata;
                }
            };
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
            LOGGER.info("Packing {}", task.getPath());
            final TaskOutputPacker.PackResult packResult = packer.pack(outputProperties, output, taskOutputOriginFactory.createWriter(task, clock.getElapsedMillis()));
            return new BuildCacheStoreCommand.Result() {
                @Override
                public long getArtifactEntryCount() {
                    return packResult.entries;
                }
            };
        }

    }


}

