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

package org.gradle.internal.execution.history;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.overlap.OverlappingOutputs;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.util.Optional;

/**
 * The execution state before the current execution.
 */
public interface BeforeExecutionState extends ExecutionState {
    @Override
    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties();

    @Override
    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getOutputFileProperties();

    ImmutableSortedMap<String, FileSystemSnapshot> getOutputFileSnapshots();

    /**
     * Returns overlapping outputs if they are detected.
     *
     * @see org.gradle.internal.execution.UnitOfWork#getOverlappingOutputHandling()
     */
    Optional<OverlappingOutputs> getDetectedOverlappingOutputs();
}
