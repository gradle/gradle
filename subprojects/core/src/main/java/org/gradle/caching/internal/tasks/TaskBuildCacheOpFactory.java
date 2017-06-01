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
import org.gradle.caching.internal.controller.BuildCacheLoadOp;
import org.gradle.caching.internal.controller.BuildCacheStoreOp;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginFactory;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginMetadata;
import org.gradle.internal.time.Timer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.SortedSet;

public class TaskBuildCacheOpFactory {

    private static final Logger LOGGER = Logging.getLogger(TaskBuildCacheOpFactory.class);

    private final TaskOutputPacker packer;
    private final TaskOutputOriginFactory taskOutputOriginFactory;

    public TaskBuildCacheOpFactory(TaskOutputPacker packer, TaskOutputOriginFactory taskOutputOriginFactory) {
        this.packer = packer;
        this.taskOutputOriginFactory = taskOutputOriginFactory;
    }

    public LoadOp loadOp(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, TaskOutputsGenerationListener taskOutputsGenerationListener, Timer clock) {
        return new LoadOp(cacheKey, outputProperties, task, taskOutputsGenerationListener, clock);
    }

    public StoreOp storeOp(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, Timer clock) {
        return new StoreOp(cacheKey, outputProperties, task, clock);
    }

    public class LoadOp implements BuildCacheLoadOp {

        private final TaskOutputCachingBuildCacheKey cacheKey;
        private final SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties;
        private final TaskInternal task;
        private final TaskOutputsGenerationListener taskOutputsGenerationListener;
        private final Timer clock;

        public TaskOutputOriginMetadata originMetadata;

        private long artifactEntryCount;

        private LoadOp(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, TaskOutputsGenerationListener taskOutputsGenerationListener, Timer clock) {
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
        public void load(InputStream input) {
            taskOutputsGenerationListener.beforeTaskOutputsGenerated();
            TaskOutputPacker.UnpackResult unpackResult = packer.unpack(outputProperties, input, taskOutputOriginFactory.createReader(task));
            LOGGER.info("Unpacked output for {} from cache (took {}).", task, clock.getElapsed());
            originMetadata = unpackResult.originMetadata;
            artifactEntryCount = unpackResult.entries;
        }

        @Override
        public boolean isLoaded() {
            return originMetadata != null;
        }

        @Override
        public long getArtifactEntryCount() {
            return artifactEntryCount;
        }
    }

    public class StoreOp implements BuildCacheStoreOp {

        private final TaskOutputCachingBuildCacheKey cacheKey;
        private final TaskInternal task;
        private final SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties;
        private final Timer clock;

        long artifactEntryCount;

        private StoreOp(TaskOutputCachingBuildCacheKey cacheKey, SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties, TaskInternal task, Timer clock) {
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
        public void store(OutputStream output) throws IOException {
            LOGGER.info("Packing {}", task.getPath());
            TaskOutputPacker.PackResult packResult = packer.pack(outputProperties, output, taskOutputOriginFactory.createWriter(task, clock.getElapsedMillis()));
            artifactEntryCount = packResult.entries;
        }

        @Override
        public boolean isStored() {
            return artifactEntryCount > 0;
        }

        @Override
        public long getArtifactEntryCount() {
            return artifactEntryCount;
        }

    }


}

