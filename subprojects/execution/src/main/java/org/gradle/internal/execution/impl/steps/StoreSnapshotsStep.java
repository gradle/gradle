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

package org.gradle.internal.execution.impl.steps;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;

public class StoreSnapshotsStep<C extends Context> implements Step<C, CurrentSnapshotResult> {
    private final OutputFilesRepository outputFilesRepository;
    private final Step<? super C, ? extends CurrentSnapshotResult> delegate;

    public StoreSnapshotsStep(
        OutputFilesRepository outputFilesRepository,
        Step<? super C, ? extends CurrentSnapshotResult> delegate
    ) {
        this.outputFilesRepository = outputFilesRepository;
        this.delegate = delegate;
    }

    @Override
    // TODO Return a simple Result (that includes the origin metadata) here
    public CurrentSnapshotResult execute(C context) {
        CurrentSnapshotResult result = delegate.execute(context);
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs = result.getFinalOutputs();
        context.getWork().persistResult(
            finalOutputs,
            result.getFailure() == null,
            result.getOriginMetadata()
        );
        outputFilesRepository.recordOutputs(finalOutputs.values());

        return result;
    }
}
