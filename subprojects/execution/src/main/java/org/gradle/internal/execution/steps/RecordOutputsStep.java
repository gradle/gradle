/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.steps;

import org.gradle.internal.execution.Context;
import org.gradle.internal.execution.CurrentSnapshotResult;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.history.OutputFilesRepository;

public class RecordOutputsStep<C extends Context> implements Step<C, CurrentSnapshotResult> {
    private final OutputFilesRepository outputFilesRepository;
    private final Step<? super C, ? extends CurrentSnapshotResult> delegate;

    public RecordOutputsStep(
        OutputFilesRepository outputFilesRepository,
        Step<? super C, ? extends CurrentSnapshotResult> delegate
    ) {
        this.outputFilesRepository = outputFilesRepository;
        this.delegate = delegate;
    }

    @Override
    public CurrentSnapshotResult execute(C context) {
        CurrentSnapshotResult result = delegate.execute(context);
        outputFilesRepository.recordOutputs(result.getFinalOutputs().values());
        return result;
    }
}
