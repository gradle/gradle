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

package org.gradle.internal.execution.history.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;

public class DefaultAfterExecutionState extends AbstractExecutionState<FileCollectionFingerprint> implements AfterExecutionState {
    private final BuildCacheKey cacheKey;
    private final ImmutableSortedMap<String, FileSystemSnapshot> outputFilesProducedByWork;
    private final OriginMetadata originMetadata;

    public DefaultAfterExecutionState(
        OriginMetadata originMetadata,
        @Nullable
        BuildCacheKey cacheKey,
        ImplementationSnapshot implementation,
        ImmutableList<ImplementationSnapshot> additionalImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedMap<String, FileCollectionFingerprint> inputFileProperties,
        ImmutableSortedMap<String, FileSystemSnapshot> outputFilesProducedByWork
    ) {
        super(implementation, additionalImplementations, inputProperties, inputFileProperties);
        this.cacheKey = cacheKey;
        this.outputFilesProducedByWork = outputFilesProducedByWork;
        this.originMetadata = originMetadata;
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
    public BuildCacheKey getCacheKey() {
        return cacheKey;
    }
}
