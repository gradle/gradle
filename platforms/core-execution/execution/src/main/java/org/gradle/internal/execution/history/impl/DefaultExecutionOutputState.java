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

package org.gradle.internal.execution.history.impl;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.history.ExecutionOutputState;
import org.gradle.internal.snapshot.FileSystemSnapshot;

public class DefaultExecutionOutputState implements ExecutionOutputState {
    private final boolean successful;
    private final ImmutableSortedMap<String, FileSystemSnapshot> outputFilesProducedByWork;
    private final OriginMetadata originMetadata;
    private final boolean reused;

    public DefaultExecutionOutputState(boolean successful, ImmutableSortedMap<String, FileSystemSnapshot> outputFilesProducedByWork, OriginMetadata originMetadata, boolean reused) {
        this.successful = successful;
        this.outputFilesProducedByWork = outputFilesProducedByWork;
        this.originMetadata = originMetadata;
        this.reused = reused;
    }

    @Override
    public boolean isSuccessful() {
        return successful;
    }

    @Override
    public ImmutableSortedMap<String, FileSystemSnapshot> getOutputFilesProducedByWork() {
        return outputFilesProducedByWork;
    }

    @Override
    public OriginMetadata getOriginMetadata() {
        return originMetadata;
    }

    @Override
    public boolean isReused() {
        return reused;
    }
}
